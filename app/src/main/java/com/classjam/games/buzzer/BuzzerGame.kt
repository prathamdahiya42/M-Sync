package com.classjam.games.buzzer

import android.os.SystemClock
import com.classjam.games.engine.Game
import com.classjam.games.engine.GameHostContext
import com.classjam.network.protocol.Envelope
import com.classjam.network.protocol.GameEventPayload
import com.classjam.network.protocol.GameType
import com.classjam.network.protocol.MessageTypes

class BuzzerGame : Game {
    override val gameType: GameType = GameType.BUZZER_GUESS

    private var isBuzzerLocked = false
    private var winnerDeviceId: String? = null
    private var winningTimestampNs: Long = 0L

    override fun onStart(context: GameHostContext) {
        unlockBuzzer(context)
    }

    override fun onTick(nowNs: Long, context: GameHostContext) {
        // No auto-timeout; controlled by host / DJ
    }

    override fun onMessage(senderDeviceId: String, envelope: Envelope, context: GameHostContext) {
        if (envelope.type == MessageTypes.GAME_EVENT) {
            val eventPayload = GameEventPayload.fromJson(envelope.payload) ?: return

            when (eventPayload.event) {
                "BUZZ_IN" -> {
                    // Check if buzzer is already locked
                    if (!isBuzzerLocked) {
                        isBuzzerLocked = true
                        winnerDeviceId = senderDeviceId
                        winningTimestampNs = if (eventPayload.correctedTimestampNs > 0) {
                            eventPayload.correctedTimestampNs
                        } else {
                            SystemClock.elapsedRealtimeNanos()
                        }

                        val winnerName = context.connectedDevices[senderDeviceId]?.displayName ?: "Player"
                        val summary = "$winnerName buzzed in first!"

                        context.broadcastGameEvent(
                            GameEventPayload(
                                gameType = gameType,
                                event = "BUZZER_LOCKED",
                                senderDeviceId = senderDeviceId,
                                hostTimestampNs = winningTimestampNs,
                                extraData = summary
                            )
                        )
                    }
                }

                "AWARD_POINTS" -> {
                    // Admin grading
                    val targetId = eventPayload.targetDeviceId
                    val points = eventPayload.extraData.toIntOrNull() ?: 10
                    if (targetId.isNotBlank()) {
                        context.scoreboard.addPoints(targetId, points)
                        val name = context.connectedDevices[targetId]?.displayName ?: "Player"
                        context.scoreboard.nextRound("$name awarded $points points!")
                        context.broadcastScoreboard()
                    }
                    unlockBuzzer(context)
                }

                "RESET_BUZZER" -> {
                    unlockBuzzer(context)
                }
            }
        }
    }

    fun unlockBuzzer(context: GameHostContext) {
        isBuzzerLocked = false
        winnerDeviceId = null
        winningTimestampNs = 0L

        context.broadcastGameEvent(
            GameEventPayload(
                gameType = gameType,
                event = "BUZZER_UNLOCKED",
                extraData = "Buzzer is open! Tap as fast as you can!"
            )
        )
    }

    override fun onEnd(context: GameHostContext) {
        isBuzzerLocked = true
        winnerDeviceId = null
    }
}
