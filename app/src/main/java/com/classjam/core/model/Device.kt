package com.classjam.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Device(
    val deviceId: String,
    val displayName: String,
    val role: Role = Role.PLAYER,
    val latencyMs: Long = 0L,
    val isReady: Boolean = false,
    val downloadProgress: Int = 0, // 0 to 100
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val ipAddress: String = "",
    val inOutputGroup: Boolean = true,
    val lastSeenEpochMs: Long = System.currentTimeMillis()
)
