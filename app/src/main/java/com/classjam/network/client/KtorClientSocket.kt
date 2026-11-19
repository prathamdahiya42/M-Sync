package com.classjam.network.client

import android.os.SystemClock
import com.classjam.core.model.ConnectionState
import com.classjam.core.model.Device
import com.classjam.core.model.Role
import com.classjam.network.protocol.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

class KtorClientSocket(
    val deviceId: String,
    val displayName: String
) {
    private val client = HttpClient(CIO) {
        install(io.ktor.client.plugins.websocket.WebSockets) {
            pingInterval = 15_000
        }
    }

    private var session: DefaultClientWebSocketSession? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var connectionJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentRole = MutableStateFlow<Role>(Role.PLAYER)
    val currentRole: StateFlow<Role> = _currentRole.asStateFlow()

    private val _deviceList = MutableStateFlow<List<Device>>(emptyList())
    val deviceList: StateFlow<List<Device>> = _deviceList.asStateFlow()

    private val _incomingEnvelopes = MutableSharedFlow<Envelope>(extraBufferCapacity = 64)
    val incomingEnvelopes: SharedFlow<Envelope> = _incomingEnvelopes.asSharedFlow()

    private val seqGenerator = AtomicLong(1L)
    private var targetHost: String = ""
    private var targetPort: Int = 8080
    private var sessionToken: String = ""
    private var roomCode: String = ""
    private var isVoluntarilyClosed = false

    fun connect(
        host: String,
        port: Int,
        token: String,
        roomCode: String = "",
        autoReconnect: Boolean = true
    ) {
        this.targetHost = host
        this.targetPort = port
        this.sessionToken = token
        this.roomCode = roomCode
        this.isVoluntarilyClosed = false

        connectionJob?.cancel()
        connectionJob = scope.launch {
            var attempt = 0
            while (isActive && !isVoluntarilyClosed) {
                _connectionState.value = ConnectionState.ConnectingSocket(targetHost, targetPort)
                try {
                    client.webSocket(host = targetHost, port = targetPort, path = "/ws") {
                        session = this
                        attempt = 0 // Reset attempt counter on success

                        // Step 1: Send JOIN handshake
                        val joinPayload = JoinPayload(
                            deviceId = deviceId,
                            displayName = displayName,
                            token = sessionToken,
                            roomCode = roomCode
                        )
                        val joinEnvelope = Envelope(
                            type = MessageTypes.JOIN,
                            seq = seqGenerator.getAndIncrement(),
                            ts = System.currentTimeMillis(),
                            payload = joinPayload.toJson()
                        )
                        send(Frame.Text(joinEnvelope.toJson()))

                        // Step 2: Read incoming loop
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val text = frame.readText()
                                val envelope = Envelope.fromJson(text) ?: continue

                                when (envelope.type) {
                                    MessageTypes.WELCOME -> {
                                        val welcome = WelcomePayload.fromJson(envelope.payload)
                                        if (welcome != null) {
                                            _currentRole.value = welcome.assignedRole
                                            _connectionState.value = ConnectionState.Connected(
                                                sessionToken = sessionToken,
                                                role = welcome.assignedRole,
                                                roomName = welcome.roomName
                                            )
                                        }
                                    }
                                    MessageTypes.ROLE_UPDATE -> {
                                        val roleUpdate = RoleUpdatePayload.fromJson(envelope.payload)
                                        if (roleUpdate != null && roleUpdate.targetDeviceId == deviceId) {
                                            _currentRole.value = roleUpdate.newRole
                                        }
                                        _incomingEnvelopes.emit(envelope)
                                    }
                                    MessageTypes.DEVICE_LIST -> {
                                        val listPayload = DeviceListPayload.fromJson(envelope.payload)
                                        if (listPayload != null) {
                                            _deviceList.value = listPayload.devices
                                            listPayload.devices.find { it.deviceId == deviceId }?.let { self ->
                                                _currentRole.value = self.role
                                            }
                                        }
                                        _incomingEnvelopes.emit(envelope)
                                    }
                                    MessageTypes.PING -> {
                                        // Respond to server heartbeat ping
                                        val ping = PingPayload.fromJson(envelope.payload)
                                        if (ping != null) {
                                            val pong = PongPayload(
                                                clientSendNs = ping.clientSendNs,
                                                serverReceiveNs = SystemClock.elapsedRealtimeNanos(),
                                                serverTransmitNs = SystemClock.elapsedRealtimeNanos(),
                                                seq = ping.seq
                                            )
                                            val pongEnvelope = Envelope(
                                                type = MessageTypes.PONG,
                                                seq = seqGenerator.getAndIncrement(),
                                                ts = System.currentTimeMillis(),
                                                payload = pong.toJson()
                                            )
                                            send(Frame.Text(pongEnvelope.toJson()))
                                        }
                                    }
                                    MessageTypes.ERROR -> {
                                        val error = ErrorPayload.fromJson(envelope.payload)
                                        val msg = error?.message ?: "Server rejected connection"
                                        _connectionState.value = ConnectionState.Error(msg, canRetry = false)
                                        isVoluntarilyClosed = true
                                        close()
                                        return@webSocket
                                    }
                                    MessageTypes.KICK -> {
                                        val kick = KickPayload.fromJson(envelope.payload)
                                        _connectionState.value = ConnectionState.Disconnected(
                                            kick?.reason ?: "Removed from room by host"
                                        )
                                        isVoluntarilyClosed = true
                                        close()
                                        return@webSocket
                                    }
                                    else -> {
                                        _incomingEnvelopes.emit(envelope)
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isVoluntarilyClosed) break

                    attempt++
                    if (!autoReconnect || attempt > 5) {
                        _connectionState.value = ConnectionState.Error(
                            "Cannot connect to host ($targetHost:$targetPort): ${e.localizedMessage}",
                            canRetry = true
                        )
                        break
                    } else {
                        val backoffMs = min(1000L * (1 shl (attempt - 1)), 8000L)
                        _connectionState.value = ConnectionState.Error(
                            "Host disconnected. Reconnecting in ${backoffMs / 1000}s (Attempt $attempt/5)...",
                            canRetry = true
                        )
                        delay(backoffMs)
                    }
                }
            }
        }
    }

    suspend fun sendEnvelope(envelope: Envelope): Boolean {
        val s = session ?: return false
        return try {
            s.send(Frame.Text(envelope.toJson()))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun leave() {
        isVoluntarilyClosed = true
        scope.launch {
            try {
                val leaveEnvelope = Envelope(
                    type = MessageTypes.LEAVE,
                    seq = seqGenerator.getAndIncrement(),
                    ts = System.currentTimeMillis(),
                    payload = "{}"
                )
                session?.send(Frame.Text(leaveEnvelope.toJson()))
                session?.close(CloseReason(CloseReason.Codes.NORMAL, "Voluntary Leave"))
            } catch (e: Exception) {
                // Ignored
            } finally {
                session = null
                _connectionState.value = ConnectionState.Disconnected("You left the session")
            }
        }
    }

    fun disconnect() {
        isVoluntarilyClosed = true
        connectionJob?.cancel()
        scope.launch {
            try {
                session?.close()
            } catch (e: Exception) {
                // Ignored
            } finally {
                session = null
                _connectionState.value = ConnectionState.Idle
            }
        }
    }
}
