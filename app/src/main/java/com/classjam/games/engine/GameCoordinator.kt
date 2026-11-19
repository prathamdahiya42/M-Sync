package com.classjam.games.engine

import android.os.SystemClock
import com.classjam.core.model.Device
import com.classjam.games.buzzer.BuzzerGame
import com.classjam.games.freedj.FreeDjGame
import com.classjam.games.hotpotato.HotPotatoGame
import com.classjam.games.musicalchairs.MusicalChairsGame
import com.classjam.network.protocol.*
import com.classjam.network.server.KtorHostServer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class GameCoordinator(
    private val hostServer: KtorHostServer
) : GameHostContext {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var tickJob: Job? = null
    private val seqGenerator = AtomicLong(500L)

    override val scoreboard = ScoreboardManager()
    override val connectedDevices: Map<String, Device>
        get() = hostServer.connectedDevices.value

    private val _activeGameType = MutableStateFlow(GameType.NONE)
    val activeGameType: StateFlow<GameType> = _activeGameType.asStateFlow()

    private val _scoreboardState = MutableStateFlow(scoreboard.toPayload(emptyMap()))
    val scoreboardState: StateFlow<ScoreboardPayload> = _scoreboardState.asStateFlow()

    private var activeGame: Game? = null

    fun startGame(type: GameType) {
        stopGame()
        _activeGameType.value = type

        val game = when (type) {
            GameType.NONE -> FreeDjGame()
            GameType.MUSICAL_CHAIRS -> MusicalChairsGame()
            GameType.HOT_POTATO -> HotPotatoGame()
            GameType.BUZZER_GUESS -> BuzzerGame()
        }
        activeGame = game

        // Broadcast GAME_START to room
        val startPayload = GameStartPayload(gameType = type, roundNumber = scoreboard.roundNumber)
        val envelope = Envelope(
            type = MessageTypes.GAME_START,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = startPayload.toJson()
        )
        hostServer.broadcast(envelope)

        game.onStart(this)
        startTicker()
    }

    fun handleIncomingGameMessage(senderDeviceId: String, envelope: Envelope) {
        activeGame?.onMessage(senderDeviceId, envelope, this)
    }

    private fun startTicker() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(100L)
                val nowNs = SystemClock.elapsedRealtimeNanos()
                activeGame?.onTick(nowNs, this@GameCoordinator)
            }
        }
    }

    fun stopGame() {
        tickJob?.cancel()
        activeGame?.onEnd(this)

        val endPayload = GameEndPayload(
            gameType = _activeGameType.value,
            finalScoreboard = scoreboard.toPayload(connectedDevices)
        )
        val envelope = Envelope(
            type = MessageTypes.GAME_END,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = endPayload.toJson()
        )
        hostServer.broadcast(envelope)

        activeGame = null
        _activeGameType.value = GameType.NONE
    }

    fun resetScores() {
        scoreboard.reset()
        broadcastScoreboard()
    }

    override fun sendTo(deviceId: String, envelope: Envelope) {
        hostServer.sendToDevice(deviceId, envelope)
    }

    override fun broadcast(envelope: Envelope) {
        hostServer.broadcast(envelope)
    }

    override fun broadcastGameEvent(event: GameEventPayload) {
        val envelope = Envelope(
            type = MessageTypes.GAME_EVENT,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = event.toJson()
        )
        hostServer.broadcast(envelope)
    }

    override fun broadcastScoreboard() {
        val payload = scoreboard.toPayload(connectedDevices)
        _scoreboardState.value = payload
        val envelope = Envelope(
            type = MessageTypes.GAME_SCOREBOARD,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = payload.toJson()
        )
        hostServer.broadcast(envelope)
    }

    override fun pauseMusic() {
        hostServer.broadcastPause()
    }

    override fun playMusic(trackId: String) {
        hostServer.broadcastPlayAt(trackId, 0L)
    }

    fun destroy() {
        stopGame()
        scope.cancel()
    }
}
