package com.classjam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.classjam.core.datastore.AppPreferences
import com.classjam.core.model.NetworkMode
import com.classjam.core.model.QrPayload
import com.classjam.core.model.Role
import com.classjam.core.model.Track
import com.classjam.core.util.NetworkUtil
import com.classjam.games.engine.GameCoordinator
import com.classjam.network.client.KtorClientSocket
import com.classjam.network.client.TrackDownloader
import com.classjam.network.hotspot.HotspotState
import com.classjam.network.hotspot.LocalOnlyHotspotManager
import com.classjam.network.hotspot.WifiConnectManager
import com.classjam.network.protocol.*
import com.classjam.network.server.KtorHostServer
import com.classjam.player.AudioPlaybackService
import com.classjam.player.ClickTrackGenerator
import com.classjam.player.MediaStoreIndexer
import com.classjam.player.QueuePrefetchManager
import com.classjam.player.SyncedExoPlayer
import com.classjam.sync.ClockSyncEngine
import com.classjam.sync.DriftController
import com.classjam.sync.DriftCorrectionState
import com.classjam.ui.games.GameHostControlsScreen
import com.classjam.ui.games.GamePlayerScreen
import com.classjam.ui.home.HomeScreen
import com.classjam.ui.host.HostLobbyScreen
import com.classjam.ui.join.ClientSessionScreen
import com.classjam.ui.join.ConnectingScreen
import com.classjam.ui.join.FallbackJoinScreen
import com.classjam.ui.join.QrScanScreen
import com.classjam.ui.library.MusicLibraryScreen
import com.classjam.ui.library.QueueScreen
import com.classjam.ui.navigation.Screen
import com.classjam.ui.player.CalibrationScreen
import com.classjam.ui.player.NowPlayingScreen
import com.classjam.ui.settings.SettingsScreen
import com.classjam.ui.theme.ClassJamTheme
import com.classjam.ui.theme.JamBackground
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : ComponentActivity() {

    private lateinit var appPreferences: AppPreferences
    private lateinit var hotspotManager: LocalOnlyHotspotManager
    private lateinit var wifiConnectManager: WifiConnectManager
    private lateinit var mediaStoreIndexer: MediaStoreIndexer
    private lateinit var trackDownloader: TrackDownloader

    private var hostServer: KtorHostServer? = null
    private var clientSocket: KtorClientSocket? = null
    private var queuePrefetchManager: QueuePrefetchManager? = null
    private var clockSyncEngine: ClockSyncEngine? = null
    private var syncedPlayer: SyncedExoPlayer? = null
    private var driftController: DriftController? = null
    private var gameCoordinator: GameCoordinator? = null

    private val clientLibraryTracks = mutableStateListOf<Track>()
    private var clientManifestState = mutableStateOf<ManifestPayload?>(null)
    private var isClickTrackActive = mutableStateOf(false)

    // Client game state
    private var clientActiveGameType = mutableStateOf(GameType.NONE)
    private var clientLatestGameEvent = mutableStateOf<GameEventPayload?>(null)
    private var clientScoreboard = mutableStateOf<ScoreboardPayload?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        appPreferences = AppPreferences(applicationContext)
        hotspotManager = LocalOnlyHotspotManager(applicationContext)
        wifiConnectManager = WifiConnectManager(applicationContext)
        mediaStoreIndexer = MediaStoreIndexer(applicationContext)
        trackDownloader = TrackDownloader(applicationContext)

        setContent {
            ClassJamTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = JamBackground
                ) {
                    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
                    var connectingPayload by remember { mutableStateOf<QrPayload?>(null) }
                    var previousScreen by remember { mutableStateOf<Screen>(Screen.Home) }

                    val networkMode by appPreferences.networkModeFlow.collectAsState(initial = NetworkMode.IN_APP_HOTSPOT)
                    val manualSsid by appPreferences.manualSsidFlow.collectAsState(initial = "")
                    val manualPassword by appPreferences.manualPasswordFlow.collectAsState(initial = "")
                    val hotspotState by hotspotManager.state.collectAsState()
                    val manualOffsetMs by appPreferences.manualOffsetMsFlow.collectAsState(initial = 0L)

                    var hostIp by remember { mutableStateOf("127.0.0.1") }
                    var hostPort by remember { mutableIntStateOf(8080) }
                    var hostRoomCode by remember { mutableStateOf("") }
                    var hostRoomToken by remember { mutableStateOf("") }
                    var hostRoomName by remember { mutableStateOf("Class Jam Room") }

                    LaunchedEffect(manualOffsetMs) {
                        syncedPlayer?.manualOffsetNs = manualOffsetMs * 1_000_000L
                        driftController?.manualOffsetMs = manualOffsetMs
                    }

                    when (val screen = currentScreen) {
                        is Screen.Home -> {
                            HomeScreen(
                                appPreferences = appPreferences,
                                onHostClick = {
                                    lifecycleScope.launch {
                                        val deviceId = appPreferences.getOrCreateDeviceId()
                                        val displayName = appPreferences.displayNameFlow.first()
                                        val port = appPreferences.serverPortFlow.first()
                                        val maxClients = appPreferences.maxClientsFlow.first()
                                        val code = NetworkUtil.generateRoomCode()
                                        val token = UUID.randomUUID().toString().take(8)

                                        hostPort = port
                                        hostRoomCode = code
                                        hostRoomToken = token
                                        hostRoomName = "$displayName's Jam"

                                        if (networkMode == NetworkMode.IN_APP_HOTSPOT) {
                                            hotspotManager.startHotspot()
                                        }

                                        mediaStoreIndexer.scanLocalAudio()

                                        val detectedIp = NetworkUtil.getLocalIpAddress() ?: "192.168.43.1"
                                        hostIp = detectedIp

                                        val server = KtorHostServer(
                                            hostDeviceId = deviceId,
                                            hostDisplayName = displayName,
                                            port = port,
                                            roomName = hostRoomName,
                                            roomCode = code,
                                            sessionToken = token,
                                            maxClients = maxClients,
                                            mediaStoreIndexer = mediaStoreIndexer,
                                            onMessageReceived = { senderId, envelope ->
                                                if (envelope.type == MessageTypes.GAME_EVENT) {
                                                    gameCoordinator?.handleIncomingGameMessage(senderId, envelope)
                                                }
                                            }
                                        )
                                        server.start()
                                        hostServer = server

                                        // Game Coordinator
                                        val coordinator = GameCoordinator(server)
                                        gameCoordinator = coordinator

                                        // Host local player (clock sync offset = 0)
                                        val dummySocket = KtorClientSocket(deviceId, displayName)
                                        val hostClockSync = ClockSyncEngine(dummySocket)
                                        clockSyncEngine = hostClockSync
                                        val player = SyncedExoPlayer(applicationContext, hostClockSync)
                                        syncedPlayer = player

                                        val drift = DriftController(player.player, hostClockSync)
                                        drift.start()
                                        driftController = drift

                                        AudioPlaybackService.start(this@MainActivity, hostRoomName, isHost = true)
                                        currentScreen = Screen.HostLobby
                                    }
                                },
                                onJoinScanClick = {
                                    currentScreen = Screen.QrScan
                                },
                                onJoinFallbackClick = {
                                    currentScreen = Screen.FallbackJoin
                                }
                            )
                        }

                        is Screen.HostLobby -> {
                            val activeServer = hostServer
                            if (activeServer != null) {
                                HostLobbyScreen(
                                    server = activeServer,
                                    networkMode = networkMode,
                                    hotspotState = hotspotState,
                                    localIp = NetworkUtil.getLocalIpAddress() ?: hostIp,
                                    port = hostPort,
                                    roomCode = hostRoomCode,
                                    roomToken = hostRoomToken,
                                    roomName = hostRoomName,
                                    manualSsid = manualSsid.ifBlank { null },
                                    manualPassword = manualPassword.ifBlank { null },
                                    onOpenPlayerClick = {
                                        previousScreen = Screen.HostLobby
                                        currentScreen = Screen.NowPlaying
                                    },
                                    onOpenQueueClick = {
                                        previousScreen = Screen.HostLobby
                                        currentScreen = Screen.Queue
                                    },
                                    onOpenLibraryClick = {
                                        previousScreen = Screen.HostLobby
                                        currentScreen = Screen.MusicLibrary
                                    },
                                    onLeaveSession = {
                                        activeServer.stop()
                                        hostServer = null
                                        hotspotManager.stopHotspot()
                                        syncedPlayer?.release()
                                        syncedPlayer = null
                                        driftController?.stop()
                                        driftController = null
                                        gameCoordinator?.destroy()
                                        gameCoordinator = null
                                        clockSyncEngine?.stop()
                                        clockSyncEngine = null
                                        AudioPlaybackService.stop(this@MainActivity)
                                        currentScreen = Screen.Home
                                    }
                                )
                            }
                        }

                        is Screen.QrScan -> {
                            QrScanScreen(
                                onPayloadScanned = { payload ->
                                    connectingPayload = payload
                                    currentScreen = Screen.Connecting(
                                        ssid = payload.ssid,
                                        ip = payload.ip,
                                        port = payload.port
                                    )
                                },
                                onFallbackClick = {
                                    currentScreen = Screen.FallbackJoin
                                },
                                onBackClick = {
                                    currentScreen = Screen.Home
                                }
                            )
                        }

                        is Screen.FallbackJoin -> {
                            FallbackJoinScreen(
                                onConnectManual = { payload ->
                                    connectingPayload = payload
                                    currentScreen = Screen.Connecting(
                                        ssid = null,
                                        ip = payload.ip,
                                        port = payload.port
                                    )
                                },
                                onBackClick = {
                                    currentScreen = Screen.Home
                                }
                            )
                        }

                        is Screen.Connecting -> {
                            val payload = connectingPayload
                            if (payload != null) {
                                val socket = remember(payload) {
                                    var createdSocket: KtorClientSocket? = null
                                    lifecycleScope.launch {
                                        val deviceId = appPreferences.getOrCreateDeviceId()
                                        val displayName = appPreferences.displayNameFlow.first()
                                        createdSocket = KtorClientSocket(deviceId, displayName)
                                        clientSocket = createdSocket
                                    }
                                    createdSocket ?: KtorClientSocket(UUID.randomUUID().toString(), "Player")
                                }

                                ConnectingScreen(
                                    payload = payload,
                                    wifiConnectManager = wifiConnectManager,
                                    clientSocket = socket,
                                    onConnected = {
                                        val clockSync = ClockSyncEngine(socket)
                                        clockSync.startPeriodicSync()
                                        clockSyncEngine = clockSync

                                        val player = SyncedExoPlayer(applicationContext, clockSync)
                                        syncedPlayer = player

                                        val drift = DriftController(player.player, clockSync)
                                        drift.start()
                                        driftController = drift

                                        val prefetcher = QueuePrefetchManager(
                                            clientSocket = socket,
                                            trackDownloader = trackDownloader,
                                            hostIp = payload.ip,
                                            hostPort = payload.port
                                        )
                                        prefetcher.startListening()
                                        queuePrefetchManager = prefetcher

                                        lifecycleScope.launch {
                                            socket.incomingEnvelopes.collect { env ->
                                                when (env.type) {
                                                    MessageTypes.LIBRARY_LIST -> {
                                                        LibraryListPayload.fromJson(env.payload)?.let { lib ->
                                                            clientLibraryTracks.clear()
                                                            clientLibraryTracks.addAll(lib.tracks)
                                                        }
                                                    }
                                                    MessageTypes.MANIFEST -> {
                                                        clientManifestState.value = ManifestPayload.fromJson(env.payload)
                                                    }
                                                    MessageTypes.PLAY_AT -> {
                                                        PlayAtPayload.fromJson(env.payload)?.let { playAt ->
                                                            val cachedFile = trackDownloader.getCachedFile(playAt.trackId)
                                                            drift.updateSessionStart(playAt.hostStartTimeNs, playAt.startPositionMs)
                                                            player.schedulePlayAt(
                                                                audioFile = cachedFile,
                                                                uriStr = null,
                                                                startPositionMs = playAt.startPositionMs,
                                                                hostStartTimeNs = playAt.hostStartTimeNs,
                                                                outputGroupDeviceIds = playAt.outputGroupDeviceIds,
                                                                selfDeviceId = socket.deviceId
                                                            )
                                                        }
                                                    }
                                                    MessageTypes.PAUSE -> {
                                                        PausePayload.fromJson(env.payload)?.let { p ->
                                                            player.schedulePause(p.hostScheduledTimeNs)
                                                        }
                                                    }
                                                    MessageTypes.SEEK -> {
                                                        SeekPayload.fromJson(env.payload)?.let { s ->
                                                            drift.updateSessionStart(s.hostStartTimeNs, s.targetPositionMs)
                                                            player.scheduleSeek(s.targetPositionMs, s.hostStartTimeNs)
                                                        }
                                                    }
                                                    MessageTypes.MASTER_VOLUME -> {
                                                        MasterVolumePayload.fromJson(env.payload)?.let { v ->
                                                            player.setMasterVolume(v.masterVolume)
                                                        }
                                                    }
                                                    MessageTypes.MUTE -> {
                                                        MutePayload.fromJson(env.payload)?.let { m ->
                                                            if (m.targetDeviceId == socket.deviceId) {
                                                                player.setMuted(m.isMuted)
                                                            }
                                                        }
                                                    }
                                                    MessageTypes.GAME_START -> {
                                                        GameStartPayload.fromJson(env.payload)?.let { start ->
                                                            clientActiveGameType.value = start.gameType
                                                            previousScreen = currentScreen
                                                            currentScreen = Screen.GamePlayer
                                                        }
                                                    }
                                                    MessageTypes.GAME_EVENT -> {
                                                        GameEventPayload.fromJson(env.payload)?.let { event ->
                                                            clientLatestGameEvent.value = event
                                                        }
                                                    }
                                                    MessageTypes.GAME_SCOREBOARD -> {
                                                        ScoreboardPayload.fromJson(env.payload)?.let { sb ->
                                                            clientScoreboard.value = sb
                                                        }
                                                    }
                                                    MessageTypes.GAME_END -> {
                                                        clientActiveGameType.value = GameType.NONE
                                                    }
                                                }
                                            }
                                        }

                                        AudioPlaybackService.start(this@MainActivity, payload.roomName, isHost = false)
                                        currentScreen = Screen.ClientSession
                                    },
                                    onCancel = {
                                        currentScreen = Screen.Home
                                    }
                                )
                            }
                        }

                        is Screen.ClientSession -> {
                            val socket = clientSocket
                            if (socket != null) {
                                ClientSessionScreen(
                                    clientSocket = socket,
                                    currentTrack = clientManifestState.value?.currentTrack,
                                    isPlaying = syncedPlayer?.isPlaying?.collectAsState()?.value ?: false,
                                    onNowPlayingClick = {
                                        previousScreen = Screen.ClientSession
                                        currentScreen = Screen.NowPlaying
                                    },
                                    onBrowseMusicClick = {
                                        previousScreen = Screen.ClientSession
                                        currentScreen = Screen.MusicLibrary
                                    },
                                    onViewQueueClick = {
                                        previousScreen = Screen.ClientSession
                                        currentScreen = Screen.Queue
                                    },
                                    onLeaveClick = {
                                        lifecycleScope.launch {
                                            val keepSongs = appPreferences.keepDownloadedSongsFlow.first()
                                            trackDownloader.clearCache(keepSongs)
                                        }
                                        queuePrefetchManager?.stop()
                                        queuePrefetchManager = null
                                        clockSyncEngine?.stop()
                                        clockSyncEngine = null
                                        driftController?.stop()
                                        driftController = null
                                        syncedPlayer?.release()
                                        syncedPlayer = null
                                        wifiConnectManager.disconnectAndUnbind()
                                        AudioPlaybackService.stop(this@MainActivity)
                                        currentScreen = Screen.Home
                                    }
                                )
                            }
                        }

                        is Screen.NowPlaying -> {
                            val isHost = hostServer != null
                            val currentTrack = if (isHost) hostServer?.currentTrack?.collectAsState()?.value else clientManifestState.value?.currentTrack
                            val isPlaying = if (isHost) hostServer?.isPlaying?.collectAsState()?.value ?: false else syncedPlayer?.isPlaying?.collectAsState()?.value ?: false
                            val currentPos = syncedPlayer?.currentPositionMs?.collectAsState()?.value ?: 0L
                            val duration = syncedPlayer?.durationMs?.collectAsState()?.value ?: (currentTrack?.durationMs ?: 0L)
                            val userRole = if (isHost) Role.ADMIN else (clientSocket?.currentRole?.collectAsState()?.value ?: Role.PLAYER)
                            val masterVol = if (isHost) hostServer?.masterVolume?.collectAsState()?.value ?: 1.0f else syncedPlayer?.masterVolume?.collectAsState()?.value ?: 1.0f
                            val localVol = syncedPlayer?.localVolume?.collectAsState()?.value ?: 1.0f
                            val isMuted = syncedPlayer?.isMuted?.collectAsState()?.value ?: false
                            val outputCount = if (isHost) hostServer?.connectedDevices?.collectAsState()?.value?.values?.count { it.inOutputGroup } ?: 1
                            else clientManifestState.value?.outputGroupDeviceIds?.size ?: 1
                            val offsetNs = clockSyncEngine?.offsetNs?.collectAsState()?.value ?: 0L
                            val rttNs = clockSyncEngine?.averageRttNs?.collectAsState()?.value ?: 0L

                            NowPlayingScreen(
                                currentTrack = currentTrack,
                                isPlaying = isPlaying,
                                currentPositionMs = currentPos,
                                durationMs = duration,
                                userRole = userRole,
                                masterVolume = masterVol,
                                localVolume = localVol,
                                isMuted = isMuted,
                                outputGroupCount = outputCount,
                                clockOffsetNs = offsetNs,
                                averageRttNs = rttNs,
                                onPlayPauseClick = {
                                    if (isHost) {
                                        currentTrack?.let {
                                            if (isPlaying) hostServer?.broadcastPause()
                                            else hostServer?.broadcastPlayAt(it.id, currentPos)
                                        }
                                    } else {
                                        lifecycleScope.launch {
                                            val envelope = Envelope(
                                                type = if (isPlaying) MessageTypes.PAUSE else MessageTypes.PLAY_AT,
                                                payload = "{}"
                                            )
                                            clientSocket?.sendEnvelope(envelope)
                                        }
                                    }
                                },
                                onSkipClick = {
                                    if (isHost) hostServer?.broadcastSkip()
                                    else lifecycleScope.launch {
                                        val envelope = Envelope(type = MessageTypes.SKIP, payload = "{}")
                                        clientSocket?.sendEnvelope(envelope)
                                    }
                                },
                                onSeek = { targetPos ->
                                    if (isHost) hostServer?.broadcastSeek(targetPos)
                                    else lifecycleScope.launch {
                                        val seekPayload = SeekPayload(targetPositionMs = targetPos, hostStartTimeNs = 0L)
                                        val envelope = Envelope(type = MessageTypes.SEEK, payload = seekPayload.toJson())
                                        clientSocket?.sendEnvelope(envelope)
                                    }
                                },
                                onMasterVolumeChange = { vol ->
                                    if (isHost) hostServer?.broadcastMasterVolume(vol)
                                },
                                onLocalVolumeChange = { vol ->
                                    syncedPlayer?.setLocalVolume(vol)
                                },
                                onViewQueueClick = {
                                    previousScreen = Screen.NowPlaying
                                    currentScreen = Screen.Queue
                                },
                                onBackClick = { currentScreen = previousScreen }
                            )
                        }

                        is Screen.GameHostControls -> {
                            val coordinator = gameCoordinator
                            if (coordinator != null) {
                                GameHostControlsScreen(
                                    gameCoordinator = coordinator,
                                    onBackClick = { currentScreen = previousScreen }
                                )
                            }
                        }

                        is Screen.GamePlayer -> {
                            val socket = clientSocket
                            val clockSync = clockSyncEngine
                            if (socket != null && clockSync != null) {
                                GamePlayerScreen(
                                    activeGameType = clientActiveGameType.value,
                                    latestEvent = clientLatestGameEvent.value,
                                    scoreboard = clientScoreboard.value,
                                    connectedDevices = socket.deviceList.collectAsState().value,
                                    selfDeviceId = socket.deviceId,
                                    clockSyncEngine = clockSync,
                                    onSendEvent = { event ->
                                        lifecycleScope.launch {
                                            val envelope = Envelope(
                                                type = MessageTypes.GAME_EVENT,
                                                payload = event.toJson()
                                            )
                                            socket.sendEnvelope(envelope)
                                        }
                                    },
                                    onBackClick = { currentScreen = previousScreen }
                                )
                            }
                        }

                        is Screen.Calibration -> {
                            val driftMs = driftController?.currentDriftMs?.collectAsState()?.value ?: 0L
                            val speed = driftController?.playbackSpeed?.collectAsState()?.value ?: 1.0f
                            val correctionState = driftController?.correctionState?.collectAsState()?.value ?: DriftCorrectionState.InSync

                            CalibrationScreen(
                                currentOffsetMs = manualOffsetMs,
                                currentDriftMs = driftMs,
                                playbackSpeed = speed,
                                correctionState = correctionState,
                                isClickTrackPlaying = isClickTrackActive.value,
                                onOffsetChange = { newOffset ->
                                    lifecycleScope.launch { appPreferences.setManualOffsetMs(newOffset) }
                                },
                                onToggleClickTrack = {
                                    val clickFile = ClickTrackGenerator.getOrCreateClickTrack(applicationContext)
                                    val active = !isClickTrackActive.value
                                    isClickTrackActive.value = active
                                    if (active) {
                                        syncedPlayer?.player?.setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(clickFile)))
                                        syncedPlayer?.player?.prepare()
                                        syncedPlayer?.player?.playWhenReady = true
                                    } else {
                                        syncedPlayer?.player?.playWhenReady = false
                                    }
                                },
                                onBackClick = { currentScreen = previousScreen }
                            )
                        }

                        is Screen.Settings -> {
                            SettingsScreen(
                                appPreferences = appPreferences,
                                isHost = hostServer != null,
                                onOpenCalibrationClick = {
                                    previousScreen = Screen.Settings
                                    currentScreen = Screen.Calibration
                                },
                                onBackClick = { currentScreen = previousScreen }
                            )
                        }

                        is Screen.MusicLibrary -> {
                            val isHost = hostServer != null
                            val tracks = if (isHost) mediaStoreIndexer.indexedTracks.collectAsState().value else clientLibraryTracks
                            val userRole = if (isHost) Role.ADMIN else (clientSocket?.currentRole?.collectAsState()?.value ?: Role.PLAYER)

                            MusicLibraryScreen(
                                tracks = tracks,
                                userRole = userRole,
                                onTrackSelected = { track ->
                                    if (isHost) {
                                        hostServer?.addToQueue(track.id, hostServer!!.hostDeviceId, hostServer!!.hostDisplayName)
                                    } else {
                                        val addPayload = QueueAddPayload(trackId = track.id)
                                        val envelope = Envelope(
                                            type = MessageTypes.QUEUE_ADD,
                                            payload = addPayload.toJson()
                                        )
                                        lifecycleScope.launch { clientSocket?.sendEnvelope(envelope) }
                                    }
                                    currentScreen = previousScreen
                                },
                                onBackClick = { currentScreen = previousScreen }
                            )
                        }

                        is Screen.Queue -> {
                            val isHost = hostServer != null
                            val currentTrack = if (isHost) hostServer?.currentTrack?.collectAsState()?.value else clientManifestState.value?.currentTrack
                            val queue = if (isHost) hostServer?.queue?.collectAsState()?.value ?: emptyList() else clientManifestState.value?.queue ?: emptyList()
                            val userRole = if (isHost) Role.ADMIN else (clientSocket?.currentRole?.collectAsState()?.value ?: Role.PLAYER)
                            val allReady = if (isHost) hostServer?.isAllOutputGroupReady() ?: false else clientManifestState.value?.allPhonesReady ?: false
                            val readyOverride = if (isHost) hostServer?.readyPhonesOnlyOverride?.collectAsState()?.value ?: false else false

                            QueueScreen(
                                currentTrack = currentTrack,
                                queue = queue,
                                userRole = userRole,
                                allPhonesReady = allReady,
                                readyPhonesOnlyOverride = readyOverride,
                                onToggleReadyPhonesOverride = { enabled ->
                                    if (isHost) hostServer?.setReadyPhonesOnlyOverride(enabled)
                                },
                                onAddMoreMusicClick = {
                                    previousScreen = Screen.Queue
                                    currentScreen = Screen.MusicLibrary
                                },
                                onBackClick = { currentScreen = previousScreen }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        hostServer?.stop()
        hotspotManager.stopHotspot()
        wifiConnectManager.disconnectAndUnbind()
        clientSocket?.disconnect()
        queuePrefetchManager?.stop()
        clockSyncEngine?.stop()
        driftController?.stop()
        syncedPlayer?.release()
        gameCoordinator?.destroy()
        super.onDestroy()
    }
}
