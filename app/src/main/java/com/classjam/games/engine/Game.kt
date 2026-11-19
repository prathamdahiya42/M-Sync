package com.classjam.games.engine

import com.classjam.core.model.Device
import com.classjam.network.protocol.*

interface GameHostContext {
    val connectedDevices: Map<String, Device>
    val scoreboard: ScoreboardManager
    fun sendTo(deviceId: String, envelope: Envelope)
    fun broadcast(envelope: Envelope)
    fun broadcastGameEvent(event: GameEventPayload)
    fun broadcastScoreboard()
    fun pauseMusic()
    fun playMusic(trackId: String)
}

interface Game {
    val gameType: GameType
    fun onStart(context: GameHostContext)
    fun onMessage(senderDeviceId: String, envelope: Envelope, context: GameHostContext)
    fun onTick(nowNs: Long, context: GameHostContext)
    fun onEnd(context: GameHostContext)
}

class ScoreboardManager {
    private val scoresMap = mutableMapOf<String, Int>() // deviceId -> score
    var roundNumber: Int = 1
        private set
    var latestSummary: String = "Welcome to Party Games!"
        private set

    fun getScore(deviceId: String): Int = scoresMap[deviceId] ?: 0

    fun addPoints(deviceId: String, points: Int) {
        val current = getScore(deviceId)
        scoresMap[deviceId] = current + points
    }

    fun nextRound(summary: String = "") {
        roundNumber++
        if (summary.isNotBlank()) {
            latestSummary = summary
        }
    }

    fun reset() {
        scoresMap.clear()
        roundNumber = 1
        latestSummary = "Scores reset for new game."
    }

    fun toPayload(devices: Map<String, Device>): ScoreboardPayload {
        val list = devices.values.map { device ->
            RoundScore(
                deviceId = device.deviceId,
                displayName = device.displayName,
                score = getScore(device.deviceId)
            )
        }.sortedByDescending { it.score }

        return ScoreboardPayload(
            roundNumber = roundNumber,
            scores = list,
            latestEventSummary = latestSummary
        )
    }
}
