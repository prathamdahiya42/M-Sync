package com.classjam.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Track(
    val id: String,
    val title: String,
    val artist: String = "Unknown Artist",
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val sha256: String = "",
    val localUriString: String? = null // Only kept in host memory, omitted in client protocol
) {
    fun formatDuration(): String {
        val totalSec = durationMs / 1000
        val min = totalSec / 60
        val sec = totalSec % 60
        return "%d:%02d".format(min, sec)
    }

    fun formatSize(): String {
        val mb = sizeBytes.toDouble() / (1024 * 1024)
        return "%.1f MB".format(mb)
    }
}

@Serializable
data class QueueItem(
    val queueId: String,
    val track: Track,
    val addedByDeviceId: String,
    val addedByDisplayName: String,
    val addedTimestampMs: Long = System.currentTimeMillis()
)
