package com.classjam.network.protocol

import com.classjam.core.model.Role
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
enum class GameType(val title: String, val description: String) {
    NONE("Free DJ", "No party game. Pass music control around freely."),
    MUSICAL_CHAIRS("Musical Chairs", "Music stops at random. First to tap SIT wins, last to tap loses!"),
    HOT_POTATO("Hot Potato", "Pass the potato before the hidden timer explodes!"),
    BUZZER_GUESS("Buzzer / Guess the Song", "First to buzz in wins the right to guess the mystery track!")
}

@Serializable
data class RoundScore(
    val deviceId: String,
    val displayName: String,
    val score: Int
)

@Serializable
data class ScoreboardPayload(
    val roundNumber: Int,
    val scores: List<RoundScore>,
    val latestEventSummary: String = ""
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): ScoreboardPayload? = runCatching {
            protocolJson.decodeFromString<ScoreboardPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class GameStartPayload(
    val gameType: GameType,
    val roundNumber: Int = 1
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): GameStartPayload? = runCatching {
            protocolJson.decodeFromString<GameStartPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class GameEventPayload(
    val gameType: GameType,
    val event: String,
    val senderDeviceId: String = "",
    val targetDeviceId: String = "",
    val hostTimestampNs: Long = 0L,
    val correctedTimestampNs: Long = 0L,
    val extraData: String = ""
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): GameEventPayload? = runCatching {
            protocolJson.decodeFromString<GameEventPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class GameEndPayload(
    val gameType: GameType,
    val finalScoreboard: ScoreboardPayload
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): GameEndPayload? = runCatching {
            protocolJson.decodeFromString<GameEndPayload>(json)
        }.getOrNull()
    }
}
