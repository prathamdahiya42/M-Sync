package com.classjam.games.musicalchairs

import android.os.SystemClock
import com.classjam.games.engine.Game
import com.classjam.games.engine.GameHostContext
import com.classjam.network.protocol.Envelope
import com.classjam.network.protocol.GameEventPayload
import com.classjam.network.protocol.GameType
import com.classjam.network.protocol.MessageTypes
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random

class MusicalChairsGame : Game {
    override val gameType: GameType = GameType.MUSICAL_CHAIRS

    private enum class State { IDLE, PLAYING_MUSIC, MUSIC_STOPPED, ROUND_OVER }
    private var state = State.IDLE

    private var stopTargetNs: Long = 0L
    private var musicStopNs: Long = 0L
    private var roundTimeoutNs: Long = 0L

    // Player taps: deviceId -> correctedTimestampNs
    private val sitTaps = ConcurrentHashMap<String, Long>()

    override fun onStart(context: GameHostContext) {
        sitTaps.clear()
        state = State.PLAYING_MUSIC

        // Random music stop between 8 and 20 seconds
        val delaySec = Random.nextInt(8, 20)
        stopTargetNs = SystemClock.elapsedRealtimeNanos() + (delaySec * 1_000_000_000L)

        context.broadcastGameEvent(
            GameEventPayload(
                gameType = gameType,
                event = "MUSIC_PLAYING",
                extraData = "Listen closely! Sit as soon as the music cuts out!"
            )
        )
    }

    override fun onTick(nowNs: Long, context: GameHostContext) {
        when (state) {
            State.PLAYING_MUSIC -> {
                if (nowNs >= stopTargetNs) {
                    state = State.MUSIC_STOPPED
                    musicStopNs = nowNs
                    roundTimeoutNs = nowNs + 6_000_000_000L // 6s timeout for all to sit

                    // Stop the music across all phones
                    context.pauseMusic()

                    context.broadcastGameEvent(
                        GameEventPayload(
                            gameType = gameType,
                            event = "MUSIC_STOPPED",
                            hostTimestampNs = nowNs,
                            extraData = "SIT NOW!"
                        )
                    )
                }
            }

            State.MUSIC_STOPPED -> {
                // Check if all players have tapped or timeout expired
                val playerDevices = context.connectedDevices.values.filter { it.role != com.classjam.core.model.Role.ADMIN }
                val allTapped = playerDevices.isNotEmpty() && playerDevices.all { sitTaps.containsKey(it.deviceId) }

                if (allTapped || nowNs >= roundTimeoutNs) {
                    concludeRound(context)
                }
            }

            else -> {}
        }
    }

    override fun onMessage(senderDeviceId: String, envelope: Envelope, context: GameHostContext) {
        if (state != State.MUSIC_STOPPED) return

        if (envelope.type == MessageTypes.GAME_EVENT) {
            val eventPayload = GameEventPayload.fromJson(envelope.payload)
            if (eventPayload?.event == "SIT_TAP") {
                // Record host-corrected timestamp
                if (!sitTaps.containsKey(senderDeviceId)) {
                    val correctedNs = if (eventPayload.correctedTimestampNs > 0) eventPayload.correctedTimestampNs
                    else SystemClock.elapsedRealtimeNanos()
                    sitTaps[senderDeviceId] = correctedNs

                    // Notify room of tap
                    val rank = sitTaps.size
                    val senderName = context.connectedDevices[senderDeviceId]?.displayName ?: "Player"
                    context.broadcastGameEvent(
                        GameEventPayload(
                            gameType = gameType,
                            event = "PLAYER_SAT",
                            senderDeviceId = senderDeviceId,
                            extraData = "$senderName sat down! (#$rank)"
                        )
                    )
                }
            }
        }
    }

    private fun concludeRound(context: GameHostContext) {
        state = State.ROUND_OVER
        val playerDevices = context.connectedDevices.values.filter { it.role != com.classjam.core.model.Role.ADMIN }

        if (playerDevices.isEmpty()) {
            context.broadcastGameEvent(
                GameEventPayload(gameType = gameType, event = "ROUND_OVER", extraData = "No players connected")
            )
            return
        }

        // Sort taps by correctedTimestampNs
        val sortedTaps = sitTaps.entries.sortedBy { it.value }

        val winnerDeviceId = sortedTaps.firstOrNull()?.key
        val winnerName = context.connectedDevices[winnerDeviceId]?.displayName ?: "Nobody"

        // The slowest tap or any player who failed to tap
        val nonTappers = playerDevices.filter { !sitTaps.containsKey(it.deviceId) }
        val loserDeviceId = if (nonTappers.isNotEmpty()) {
            nonTappers.first().deviceId
        } else {
            sortedTaps.lastOrNull()?.key
        }
        val loserName = context.connectedDevices[loserDeviceId]?.displayName ?: "Nobody"

        // Award points
        winnerDeviceId?.let { context.scoreboard.addPoints(it, 10) }
        loserDeviceId?.let { context.scoreboard.addPoints(it, -5) }

        val summary = "$winnerName sat down first (+10)! $loserName was too slow (-5)!"
        context.scoreboard.nextRound(summary)
        context.broadcastScoreboard()

        context.broadcastGameEvent(
            GameEventPayload(
                gameType = gameType,
                event = "ROUND_OVER",
                targetDeviceId = loserDeviceId ?: "",
                extraData = summary
            )
        )
    }

    override fun onEnd(context: GameHostContext) {
        state = State.IDLE
        sitTaps.clear()
    }
}
