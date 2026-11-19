package com.classjam.network.server

import android.os.SystemClock
import com.classjam.core.model.Device
import com.classjam.core.model.QueueItem
import com.classjam.core.model.Role
import com.classjam.core.model.RolePermissions
import com.classjam.core.model.Track
import com.classjam.network.protocol.*
import com.classjam.player.MediaStoreIndexer
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.partialcontent.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class KtorHostServer(
    val hostDeviceId: String,
    val hostDisplayName: String,
    val port: Int = 8080,
    val roomName: String = "Class Jam Room",
    val roomCode: String,
    val sessionToken: String,
    val maxClients: Int = 10,
    private val mediaStoreIndexer: MediaStoreIndexer? = null,
    private val onMessageReceived: (senderDeviceId: String, envelope: Envelope) -> Unit = { _, _ -> }
) {
    private var engine: ApplicationEngine? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Active client sessions: deviceId -> WebSocketServerSession
    private val activeSessions = ConcurrentHashMap<String, WebSocketServerSession>()

    // Connected devices model: deviceId -> Device
    private val _connectedDevices = MutableStateFlow<Map<String, Device>>(emptyMap())
    val connectedDevices: StateFlow<Map<String, Device>> = _connectedDevices.asStateFlow()

    // Playback Queue & Manifest
    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _masterVolume = MutableStateFlow(1.0f)
    val masterVolume: StateFlow<Float> = _masterVolume.asStateFlow()

    private val _readyPhonesOnlyOverride = MutableStateFlow(false)
    val readyPhonesOnlyOverride: StateFlow<Boolean> = _readyPhonesOnlyOverride.asStateFlow()

    private val seqGenerator = AtomicLong(1L)
    private var heartbeatJob: Job? = null

    // Track active DJ (single DJ at a time by default)
    var currentDjDeviceId: String? = null
        private set

    init {
        // Register host as ADMIN in the device list
        val hostDevice = Device(
            deviceId = hostDeviceId,
            displayName = hostDisplayName,
            role = Role.ADMIN,
            isReady = true,
            inOutputGroup = true
        )
        _connectedDevices.value = mapOf(hostDeviceId to hostDevice)
    }

    fun start() {
        if (engine != null) return

        engine = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets) {
                pingPeriod = Duration.ofSeconds(15)
                timeout = Duration.ofSeconds(30)
                maxFrameSize = Long.MAX_VALUE
                masking = false
            }
            install(PartialContent)

            routing {
                webSocket("/ws") {
                    handleClientConnection(this)
                }

                // HTTP Range Audio Stream endpoint
                get("/track/{id}") {
                    handleTrackDownload(call)
                }
            }
        }.start(wait = false)

        startHeartbeat()
    }

    private suspend fun handleTrackDownload(call: ApplicationCall) {
        val trackId = call.parameters["id"] ?: run {
            call.respond(HttpStatusCode.BadRequest, "Missing track id")
            return
        }

        val track = mediaStoreIndexer?.getTrack(trackId) ?: run {
            call.respond(HttpStatusCode.NotFound, "Track not found")
            return
        }

        val totalLength = track.sizeBytes
        val rangeHeader = call.request.headers[HttpHeaders.Range]

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val rangeSpec = rangeHeader.removePrefix("bytes=").trim()
            val parts = rangeSpec.split("-")
            val start = parts[0].toLongOrNull() ?: 0L
            val end = if (parts.size > 1 && parts[1].isNotBlank()) {
                min(parts[1].toLongOrNull() ?: (totalLength - 1), totalLength - 1)
            } else {
                totalLength - 1
            }

            if (start > end || start >= totalLength) {
                call.response.header(HttpHeaders.ContentRange, "bytes */$totalLength")
                call.respond(HttpStatusCode.RequestedRangeNotSatisfiable)
                return
            }

            val contentLength = end - start + 1
            call.response.status(HttpStatusCode.PartialContent)
            call.response.header(HttpHeaders.AcceptRanges, "bytes")
            call.response.header(HttpHeaders.ContentRange, "bytes $start-$end/$totalLength")
            call.response.header(HttpHeaders.ContentType, "audio/mpeg")
            call.response.header(HttpHeaders.ContentLength, contentLength.toString())

            call.respondOutputStream(ContentType.Audio.MPEG, HttpStatusCode.PartialContent) {
                mediaStoreIndexer.openTrackInputStream(trackId)?.use { input ->
                    input.skip(start)
                    val buffer = ByteArray(8192)
                    var bytesRemaining = contentLength
                    while (bytesRemaining > 0) {
                        val toRead = min(buffer.size.toLong(), bytesRemaining).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read == -1) break
                        write(buffer, 0, read)
                        bytesRemaining -= read
                    }
                }
            }
        } else {
            call.response.header(HttpHeaders.AcceptRanges, "bytes")
            call.response.header(HttpHeaders.ContentLength, totalLength.toString())
            call.response.header(HttpHeaders.ContentType, "audio/mpeg")

            call.respondOutputStream(ContentType.Audio.MPEG, HttpStatusCode.OK) {
                mediaStoreIndexer?.openTrackInputStream(trackId)?.use { input ->
                    input.copyTo(this)
                }
            }
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                delay(2000L) // 2-second heartbeat
                val nowNs = SystemClock.elapsedRealtimeNanos()
                activeSessions.forEach { (deviceId, session) ->
                    val pingPayload = PingPayload(clientSendNs = nowNs, seq = 0)
                    val envelope = Envelope(
                        type = MessageTypes.PING,
                        seq = seqGenerator.getAndIncrement(),
                        ts = System.currentTimeMillis(),
                        payload = pingPayload.toJson()
                    )
                    try {
                        session.send(Frame.Text(envelope.toJson()))
                    } catch (e: Exception) {
                        // Handled on disconnect
                    }
                }
            }
        }
    }

    private suspend fun handleClientConnection(session: WebSocketServerSession) {
        var clientDeviceId: String? = null

        try {
            for (frame in session.incoming) {
                if (frame is Frame.Text) {
                    val text = frame.readText()
                    val envelope = Envelope.fromJson(text) ?: continue

                    when (envelope.type) {
                        MessageTypes.JOIN -> {
                            val joinPayload = JoinPayload.fromJson(envelope.payload)
                            if (joinPayload == null) {
                                sendError(session, "BAD_PAYLOAD", "Invalid JOIN payload")
                                session.close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid JOIN payload"))
                                return
                            }

                            val tokenMatches = joinPayload.token == sessionToken
                            val codeMatches = joinPayload.roomCode.isNotBlank() && joinPayload.roomCode == roomCode
                            if (!tokenMatches && !codeMatches) {
                                sendError(session, "UNAUTHORIZED", "Invalid session token or room code")
                                session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
                                return
                            }

                            val currentNonHostClients = _connectedDevices.value.filterKeys { it != hostDeviceId }.size
                            if (currentNonHostClients >= maxClients && !_connectedDevices.value.containsKey(joinPayload.deviceId)) {
                                sendError(session, "ROOM_FULL", "Room is at full capacity ($maxClients clients max)")
                                session.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Room Full"))
                                return
                            }

                            clientDeviceId = joinPayload.deviceId
                            activeSessions[clientDeviceId] = session

                            val existingDevice = _connectedDevices.value[clientDeviceId]
                            val assignedRole = existingDevice?.role ?: Role.PLAYER

                            val device = Device(
                                deviceId = clientDeviceId,
                                displayName = joinPayload.displayName,
                                role = assignedRole,
                                isReady = existingDevice?.isReady ?: false,
                                downloadProgress = existingDevice?.downloadProgress ?: 0,
                                inOutputGroup = existingDevice?.inOutputGroup ?: true,
                                isMuted = existingDevice?.isMuted ?: false,
                                latencyMs = existingDevice?.latencyMs ?: 0L
                            )
                            updateDevice(device)

                            // Send WELCOME
                            val welcomePayload = WelcomePayload(
                                deviceId = clientDeviceId,
                                assignedRole = assignedRole,
                                roomName = roomName,
                                roomCode = roomCode,
                                maxClients = maxClients,
                                serverTimeNs = SystemClock.elapsedRealtimeNanos()
                            )
                            val welcomeEnvelope = Envelope(
                                type = MessageTypes.WELCOME,
                                seq = seqGenerator.getAndIncrement(),
                                ts = System.currentTimeMillis(),
                                payload = welcomePayload.toJson()
                            )
                            session.send(Frame.Text(welcomeEnvelope.toJson()))

                            // Send Library List & Manifest
                            sendLibraryListToDevice(clientDeviceId)
                            sendManifestToDevice(clientDeviceId)
                            broadcastDeviceList()
                        }

                        MessageTypes.QUEUE_ADD -> {
                            val addPayload = QueueAddPayload.fromJson(envelope.payload)
                            if (addPayload != null && clientDeviceId != null) {
                                val sender = _connectedDevices.value[clientDeviceId]
                                if (sender != null && RolePermissions.canRequestQueue(sender.role)) {
                                    addToQueue(addPayload.trackId, clientDeviceId, sender.displayName)
                                } else {
                                    sendError(session, "UNAUTHORIZED", "Only Admin or DJ can add songs to the queue")
                                }
                            }
                        }

                        MessageTypes.READY -> {
                            val readyPayload = ReadyPayload.fromJson(envelope.payload)
                            if (readyPayload != null && clientDeviceId != null) {
                                val current = _connectedDevices.value[clientDeviceId]
                                if (current != null) {
                                    val updated = current.copy(
                                        isReady = readyPayload.isReady,
                                        downloadProgress = readyPayload.progress
                                    )
                                    updateDevice(updated)
                                    broadcastDeviceList()
                                    broadcastManifest()
                                }
                            }
                        }

                        // DJ-requested playback actions (validated by host authority)
                        MessageTypes.PAUSE -> {
                            val sender = _connectedDevices.value[clientDeviceId]
                            if (sender != null && RolePermissions.canControlPlayback(sender.role)) {
                                broadcastPause()
                            }
                        }

                        MessageTypes.SEEK -> {
                            val seekPayload = SeekPayload.fromJson(envelope.payload)
                            val sender = _connectedDevices.value[clientDeviceId]
                            if (seekPayload != null && sender != null && RolePermissions.canControlPlayback(sender.role)) {
                                broadcastSeek(seekPayload.targetPositionMs)
                            }
                        }

                        MessageTypes.SKIP -> {
                            val sender = _connectedDevices.value[clientDeviceId]
                            if (sender != null && RolePermissions.canControlPlayback(sender.role)) {
                                broadcastSkip()
                            }
                        }

                        MessageTypes.PING -> {
                            val pingPayload = PingPayload.fromJson(envelope.payload)
                            if (pingPayload != null) {
                                val nowNs = SystemClock.elapsedRealtimeNanos()
                                val pongPayload = PongPayload(
                                    clientSendNs = pingPayload.clientSendNs,
                                    serverReceiveNs = nowNs,
                                    serverTransmitNs = SystemClock.elapsedRealtimeNanos(),
                                    seq = pingPayload.seq
                                )
                                val pongEnvelope = Envelope(
                                    type = MessageTypes.PONG,
                                    seq = seqGenerator.getAndIncrement(),
                                    ts = System.currentTimeMillis(),
                                    payload = pongPayload.toJson()
                                )
                                session.send(Frame.Text(pongEnvelope.toJson()))
                            }
                        }

                        MessageTypes.PONG -> {
                            val pongPayload = PongPayload.fromJson(envelope.payload)
                            if (pongPayload != null && clientDeviceId != null) {
                                val nowNs = SystemClock.elapsedRealtimeNanos()
                                val rttNs = nowNs - pongPayload.clientSendNs
                                val rttMs = (rttNs / 1_000_000L).coerceAtLeast(0L)
                                val current = _connectedDevices.value[clientDeviceId]
                                if (current != null) {
                                    updateDevice(current.copy(latencyMs = rttMs))
                                }
                            }
                        }

                        MessageTypes.LEAVE -> {
                            clientDeviceId?.let { removeDevice(it) }
                            session.close(CloseReason(CloseReason.Codes.NORMAL, "Client left session"))
                            return
                        }

                        else -> {
                            clientDeviceId?.let { id ->
                                onMessageReceived(id, envelope)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Disconnect handled in finally
        } finally {
            clientDeviceId?.let { id ->
                activeSessions.remove(id)
                if (currentDjDeviceId == id) {
                    currentDjDeviceId = null
                }
                removeDevice(id)
                broadcastDeviceList()
                broadcastManifest()
            }
        }
    }

    /**
     * Broadcasts PLAY_AT scheduled ~2 seconds into the future
     */
    fun broadcastPlayAt(trackId: String, startPositionMs: Long = 0L) {
        if (!isAllOutputGroupReady()) return

        val hostStartTimeNs = SystemClock.elapsedRealtimeNanos() + 2_000_000_000L // 2 seconds ahead
        val outputDevices = _connectedDevices.value.values.filter { it.inOutputGroup }.map { it.deviceId }

        val payload = PlayAtPayload(
            trackId = trackId,
            startPositionMs = startPositionMs,
            hostStartTimeNs = hostStartTimeNs,
            outputGroupDeviceIds = outputDevices
        )
        val envelope = Envelope(
            type = MessageTypes.PLAY_AT,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = payload.toJson()
        )
        _isPlaying.value = true
        broadcast(envelope)
    }

    /**
     * Broadcasts synchronized PAUSE scheduled ~300ms in the future
     */
    fun broadcastPause() {
        val hostScheduledTimeNs = SystemClock.elapsedRealtimeNanos() + 300_000_000L // 300ms ahead
        val payload = PausePayload(hostScheduledTimeNs = hostScheduledTimeNs)
        val envelope = Envelope(
            type = MessageTypes.PAUSE,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = payload.toJson()
        )
        _isPlaying.value = false
        broadcast(envelope)
    }

    /**
     * Broadcasts synchronized SEEK scheduled ~1.5s in the future
     */
    fun broadcastSeek(targetPositionMs: Long) {
        val hostStartTimeNs = SystemClock.elapsedRealtimeNanos() + 1_500_000_000L
        val payload = SeekPayload(targetPositionMs = targetPositionMs, hostStartTimeNs = hostStartTimeNs)
        val envelope = Envelope(
            type = MessageTypes.SEEK,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = payload.toJson()
        )
        broadcast(envelope)
    }

    /**
     * Broadcasts SKIP to advance queue
     */
    fun broadcastSkip() {
        val currentQ = _queue.value.toMutableList()
        if (currentQ.isNotEmpty()) {
            val nextItem = currentQ.removeAt(0)
            _queue.value = currentQ
            _currentTrack.value = nextItem.track
            resetReadyFlags()
            broadcastManifest()
            // Auto schedule play when ready
            broadcastPlayAt(nextItem.track.id, 0L)
        } else {
            _currentTrack.value = null
            _isPlaying.value = false
            broadcastPause()
            broadcastManifest()
        }
    }

    /**
     * Broadcasts master volume to all clients
     */
    fun broadcastMasterVolume(volume: Float) {
        _masterVolume.value = volume.coerceIn(0.0f, 1.0f)
        val payload = MasterVolumePayload(masterVolume = _masterVolume.value)
        val envelope = Envelope(
            type = MessageTypes.MASTER_VOLUME,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = payload.toJson()
        )
        broadcast(envelope)
    }

    fun addToQueue(trackId: String, addedByDeviceId: String, addedByDisplayName: String) {
        val track = mediaStoreIndexer?.getTrack(trackId) ?: return
        val sanitizedTrack = track.copy(localUriString = null)

        val item = QueueItem(
            queueId = UUID.randomUUID().toString(),
            track = sanitizedTrack,
            addedByDeviceId = addedByDeviceId,
            addedByDisplayName = addedByDisplayName
        )
        val updated = _queue.value.toMutableList()
        updated.add(item)
        _queue.value = updated

        if (_currentTrack.value == null) {
            _currentTrack.value = sanitizedTrack
            resetReadyFlags()
        }

        broadcastManifest()
    }

    fun setReadyPhonesOnlyOverride(enabled: Boolean) {
        _readyPhonesOnlyOverride.value = enabled
        broadcastManifest()
    }

    fun isAllOutputGroupReady(): Boolean {
        if (_readyPhonesOnlyOverride.value) return true

        val outputDevices = _connectedDevices.value.values.filter { it.inOutputGroup }
        if (outputDevices.isEmpty()) return true
        return outputDevices.all { it.isReady }
    }

    private fun resetReadyFlags() {
        val updated = _connectedDevices.value.mapValues { (id, device) ->
            if (id == hostDeviceId) device.copy(isReady = true, downloadProgress = 100)
            else device.copy(isReady = false, downloadProgress = 0)
        }
        _connectedDevices.value = updated
        broadcastDeviceList()
    }

    fun broadcastLibraryList() {
        val tracks = mediaStoreIndexer?.indexedTracks?.value?.map {
            it.copy(localUriString = null)
        } ?: emptyList()
        val payload = LibraryListPayload(tracks = tracks)
        val envelope = Envelope(
            type = MessageTypes.LIBRARY_LIST,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = payload.toJson()
        )
        broadcast(envelope)
    }

    private fun sendLibraryListToDevice(deviceId: String) {
        val tracks = mediaStoreIndexer?.indexedTracks?.value?.map {
            it.copy(localUriString = null)
        } ?: emptyList()
        val payload = LibraryListPayload(tracks = tracks)
        val envelope = Envelope(
            type = MessageTypes.LIBRARY_LIST,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = payload.toJson()
        )
        sendToDevice(deviceId, envelope)
    }

    fun broadcastManifest() {
        val outputGroupIds = _connectedDevices.value.values.filter { it.inOutputGroup }.map { it.deviceId }
        val manifest = ManifestPayload(
            currentTrack = _currentTrack.value,
            queue = _queue.value,
            outputGroupDeviceIds = outputGroupIds,
            allPhonesReady = isAllOutputGroupReady()
        )
        val envelope = Envelope(
            type = MessageTypes.MANIFEST,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = manifest.toJson()
        )
        broadcast(envelope)
    }

    private fun sendManifestToDevice(deviceId: String) {
        val outputGroupIds = _connectedDevices.value.values.filter { it.inOutputGroup }.map { it.deviceId }
        val manifest = ManifestPayload(
            currentTrack = _currentTrack.value,
            queue = _queue.value,
            outputGroupDeviceIds = outputGroupIds,
            allPhonesReady = isAllOutputGroupReady()
        )
        val envelope = Envelope(
            type = MessageTypes.MANIFEST,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = manifest.toJson()
        )
        sendToDevice(deviceId, envelope)
    }

    fun grantDj(targetDeviceId: String) {
        if (targetDeviceId == hostDeviceId) return
        val target = _connectedDevices.value[targetDeviceId] ?: return

        currentDjDeviceId?.let { oldDjId ->
            if (oldDjId != targetDeviceId) {
                val oldDj = _connectedDevices.value[oldDjId]
                if (oldDj != null) {
                    updateDevice(oldDj.copy(role = Role.PLAYER))
                    broadcastRoleUpdate(oldDjId, Role.PLAYER)
                }
            }
        }

        currentDjDeviceId = targetDeviceId
        updateDevice(target.copy(role = Role.DJ))
        broadcastRoleUpdate(targetDeviceId, Role.DJ)
        broadcastDeviceList()
    }

    fun revokeDj(targetDeviceId: String) {
        val target = _connectedDevices.value[targetDeviceId] ?: return
        if (target.role == Role.DJ) {
            if (currentDjDeviceId == targetDeviceId) {
                currentDjDeviceId = null
            }
            updateDevice(target.copy(role = Role.PLAYER))
            broadcastRoleUpdate(targetDeviceId, Role.PLAYER)
            broadcastDeviceList()
        }
    }

    fun toggleMute(targetDeviceId: String) {
        val target = _connectedDevices.value[targetDeviceId] ?: return
        val newMuted = !target.isMuted
        updateDevice(target.copy(isMuted = newMuted))
        val mutePayload = MutePayload(targetDeviceId = targetDeviceId, isMuted = newMuted)
        val envelope = Envelope(
            type = MessageTypes.MUTE,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = mutePayload.toJson()
        )
        sendToDevice(targetDeviceId, envelope)
        broadcastDeviceList()
    }

    fun toggleOutputGroup(targetDeviceId: String) {
        val target = _connectedDevices.value[targetDeviceId] ?: return
        val newGroup = !target.inOutputGroup
        updateDevice(target.copy(inOutputGroup = newGroup))
        broadcastDeviceList()
        broadcastManifest()
    }

    fun kickDevice(targetDeviceId: String, reason: String = "Removed by host") {
        if (targetDeviceId == hostDeviceId) return
        val session = activeSessions[targetDeviceId]
        if (session != null) {
            val kickPayload = KickPayload(targetDeviceId = targetDeviceId, reason = reason)
            val envelope = Envelope(
                type = MessageTypes.KICK,
                seq = seqGenerator.getAndIncrement(),
                ts = System.currentTimeMillis(),
                payload = kickPayload.toJson()
            )
            scope.launch {
                try {
                    session.send(Frame.Text(envelope.toJson()))
                    session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, reason))
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }
        if (currentDjDeviceId == targetDeviceId) {
            currentDjDeviceId = null
        }
        removeDevice(targetDeviceId)
        broadcastDeviceList()
        broadcastManifest()
    }

    private fun broadcastRoleUpdate(targetDeviceId: String, newRole: Role) {
        val payload = RoleUpdatePayload(targetDeviceId = targetDeviceId, newRole = newRole)
        val envelope = Envelope(
            type = MessageTypes.ROLE_UPDATE,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = payload.toJson()
        )
        broadcast(envelope)
    }

    private suspend fun sendError(session: WebSocketServerSession, code: String, msg: String) {
        val errorPayload = ErrorPayload(code = code, message = msg)
        val envelope = Envelope(
            type = MessageTypes.ERROR,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = errorPayload.toJson()
        )
        try {
            session.send(Frame.Text(envelope.toJson()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun broadcast(envelope: Envelope) {
        val json = envelope.toJson()
        scope.launch {
            activeSessions.values.forEach { session ->
                try {
                    session.send(Frame.Text(json))
                } catch (e: Exception) {
                    // Ignored
                }
            }
        }
    }

    fun sendToDevice(deviceId: String, envelope: Envelope) {
        val session = activeSessions[deviceId] ?: return
        val json = envelope.toJson()
        scope.launch {
            try {
                session.send(Frame.Text(json))
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun broadcastDeviceList() {
        val listPayload = DeviceListPayload(devices = _connectedDevices.value.values.toList())
        val envelope = Envelope(
            type = MessageTypes.DEVICE_LIST,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = listPayload.toJson()
        )
        broadcast(envelope)
    }

    fun updateDevice(device: Device) {
        val current = _connectedDevices.value.toMutableMap()
        current[device.deviceId] = device
        _connectedDevices.value = current
    }

    fun removeDevice(deviceId: String) {
        if (deviceId == hostDeviceId) return
        val current = _connectedDevices.value.toMutableMap()
        current.remove(deviceId)
        _connectedDevices.value = current
        activeSessions.remove(deviceId)
    }

    fun stop() {
        heartbeatJob?.cancel()
        scope.launch {
            activeSessions.values.forEach {
                try {
                    it.close(CloseReason(CloseReason.Codes.GOING_AWAY, "Host closed room"))
                } catch (e: Exception) {
                    // Ignored
                }
            }
            activeSessions.clear()
        }
        engine?.stop(1000, 2000)
        engine = null
    }
}
