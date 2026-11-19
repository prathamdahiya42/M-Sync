package com.classjam.network.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Envelope(
    val type: String,
    val seq: Long = 0L,
    val ts: Long = System.currentTimeMillis(),
    val payload: String = ""
) {
    fun toJson(): String = jsonFormat.encodeToString(this)

    companion object {
        val jsonFormat = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(json: String): Envelope? {
            return try {
                jsonFormat.decodeFromString<Envelope>(json)
            } catch (e: Exception) {
                null
            }
        }
    }
}
