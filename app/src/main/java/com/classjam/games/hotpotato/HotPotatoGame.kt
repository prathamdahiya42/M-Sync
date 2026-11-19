package com.classjam.games.hotpotato

import android.os.SystemClock
import com.classjam.games.engine.Game
import com.classjam.games.engine.GameHostContext
import com.classjam.network.protocol.Envelope
import com.classjam.network.protocol.GameEventPayload
import com.classjam.network.protocol.GameType
import com.classjam.network.protocol.MessageTypes
import kotlin.random.Random

class HotPotatoGame : Game {
    override val gameType: GameType = GameType.HOT_POTATO

    private var currentHolderDeviceId: String? = null
    private var explosionNs: Long = 0L
    private var isRoundActive = false

    override fun onStart(context: GameHostContext) {
        val nonHostDevices = context.connectedDevices.values.filter { it.role != com.classjam.core.model.Role.ADMIN }
        if (nonHostDevices.isEmpty()) {
            context.broadcastGameEvent(
                GameEventPayload(gameType = gameType, event = "ERROR", extraData = "Need at least 1 player to play Hot Potato")
            )
            return
        }

        isRoundActive = true
        // Hidden timer between 12 and 25 seconds
        val durationSec = Random.nextInt(12, 25)
        explosionNs = SystemClock.elapsedRealtimeNanos() + (durationSec * 1_000_000_000L)

        // Select first random potato holder
        val firstHolder = nonHostDevices.random()
        currentHolderDeviceId = firstHolder.deviceId

        context.broadcastGameEvent(
            GameEventPayload(
                gameType = gameType,
                event = "POTATO_HOLDER",
                targetDeviceId = firstHolder.deviceId,
                extraData = "${firstHolder.displayName} has the Hot Potato! Pass it quick!"
            )
        )
    }

    override fun onTick(nowNs: Long, context: GameHostContext) {
        if (!isRoundActive) return

        if (nowNs >= explosionNs) {
            isRoundActive = false
            val loserId = currentHolderDeviceId
            val loserName = context.connectedDevices[loserId]?.displayName ?: "Player"

            // Penalize loser, reward others
            loserId?.let { context.scoreboard.addPoints(it, -10) }
            context.connectedDevices.values.filter { it.role != com.classjam.core.model.Role.ADMIN && it.deviceId != loserId }
                .forEach { context.scoreboard.addPoints(it.deviceId, 5) }

            val summary = "BOOM! The potato exploded on $loserName (-10 points)!"
            context.scoreboard.nextRound(summary)
            context.broadcastScoreboard()

            context.broadcastGameEvent(
                GameEventPayload(
                    gameType = gameType,
                    event = "POTATO_EXPLODED",
                    targetDeviceId = loserId ?: "",
                    extraData = summary
                )
            )
        }
    }

    override fun onMessage(senderDeviceId: String, envelope: Envelope, context: GameHostContext) {
        if (!isRoundActive) return

        if (envelope.type == MessageTypes.GAME_EVENT) {
            val eventPayload = GameEventPayload.fromJson(envelope.payload)
            if (eventPayload?.event == "PASS_POTATO") {
                // Only the current holder can pass
                if (senderDeviceId == currentHolderDeviceId) {
                    val targetId = eventPayload.targetDeviceId
                    if (targetId.isNotBlank() && context.connectedDevices.containsKey(targetId)) {
                        currentHolderDeviceId = targetId
                        val targetName = context.connectedDevices[targetId]?.displayName ?: "Player"
                        val senderName = context.connectedDevices[senderDeviceId]?.displayName ?: "Player"

                        context.broadcastGameEvent(
                            GameEventPayload(
                                gameType = gameType,
                                event = "POTATO_HOLDER",
                                senderDeviceId = senderDeviceId,
                                targetDeviceId = targetId,
                                extraData = "$senderName tossed the potato to $targetName!"
                            )
                        )
                    }
                }
            }
        }
    }

    override fun onEnd(context: GameHostContext) {
        isRoundActive = false
        currentHolderDeviceId = null
    }
}
