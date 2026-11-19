package com.classjam.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class QrPayload(
    val v: Int = 1,
    val ssid: String? = null,
    val password: String? = null,
    val ip: String,
    val port: Int,
    val token: String,
    val roomName: String,
    val roomCode: String = ""
) {
    fun toJson(): String = jsonFormat.encodeToString(this)

    companion object {
        private val jsonFormat = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

        fun fromJson(jsonStr: String): QrPayload? {
            return try {
                jsonFormat.decodeFromString<QrPayload>(jsonStr.trim())
            } catch (e: Exception) {
                null
            }
        }
    }
}
