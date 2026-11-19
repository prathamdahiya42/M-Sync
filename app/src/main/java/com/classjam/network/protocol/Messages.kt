package com.classjam.network.protocol

import com.classjam.core.model.Device
import com.classjam.core.model.QueueItem
import com.classjam.core.model.Role
import com.classjam.core.model.Track
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val protocolJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

@Serializable
data class JoinPayload(
    val deviceId: String,
    val displayName: String,
    val token: String,
    val roomCode: String = ""
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): JoinPayload? = runCatching {
            protocolJson.decodeFromString<JoinPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class WelcomePayload(
    val deviceId: String,
    val assignedRole: Role,
    val roomName: String,
    val roomCode: String,
    val maxClients: Int,
    val serverTimeNs: Long
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): WelcomePayload? = runCatching {
            protocolJson.decodeFromString<WelcomePayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class ErrorPayload(
    val code: String,
    val message: String
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): ErrorPayload? = runCatching {
            protocolJson.decodeFromString<ErrorPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class DeviceListPayload(
    val devices: List<Device>
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): DeviceListPayload? = runCatching {
            protocolJson.decodeFromString<DeviceListPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class RoleUpdatePayload(
    val targetDeviceId: String,
    val newRole: Role
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): RoleUpdatePayload? = runCatching {
            protocolJson.decodeFromString<RoleUpdatePayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class GrantDjPayload(
    val targetDeviceId: String
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): GrantDjPayload? = runCatching {
            protocolJson.decodeFromString<GrantDjPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class RevokeDjPayload(
    val targetDeviceId: String
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): RevokeDjPayload? = runCatching {
            protocolJson.decodeFromString<RevokeDjPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class MutePayload(
    val targetDeviceId: String,
    val isMuted: Boolean
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): MutePayload? = runCatching {
            protocolJson.decodeFromString<MutePayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class KickPayload(
    val targetDeviceId: String,
    val reason: String = "Removed by host"
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): KickPayload? = runCatching {
            protocolJson.decodeFromString<KickPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class PingPayload(
    val clientSendNs: Long,
    val seq: Int
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): PingPayload? = runCatching {
            protocolJson.decodeFromString<PingPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class PongPayload(
    val clientSendNs: Long,
    val serverReceiveNs: Long,
    val serverTransmitNs: Long,
    val seq: Int
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): PongPayload? = runCatching {
            protocolJson.decodeFromString<PongPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class SyncStatePayload(
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val role: Role = Role.PLAYER,
    val queue: List<String> = emptyList()
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): SyncStatePayload? = runCatching {
            protocolJson.decodeFromString<SyncStatePayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class LibraryListPayload(
    val tracks: List<Track>
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): LibraryListPayload? = runCatching {
            protocolJson.decodeFromString<LibraryListPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class QueueAddPayload(
    val trackId: String
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): QueueAddPayload? = runCatching {
            protocolJson.decodeFromString<QueueAddPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class ManifestPayload(
    val currentTrack: Track? = null,
    val queue: List<QueueItem> = emptyList(),
    val outputGroupDeviceIds: List<String> = emptyList(),
    val allPhonesReady: Boolean = false
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): ManifestPayload? = runCatching {
            protocolJson.decodeFromString<ManifestPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class ReadyPayload(
    val trackId: String,
    val isReady: Boolean,
    val progress: Int = 0 // 0 to 100
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): ReadyPayload? = runCatching {
            protocolJson.decodeFromString<ReadyPayload>(json)
        }.getOrNull()
    }
}

// Scheduled Synced Playback Messages

@Serializable
data class PlayAtPayload(
    val trackId: String,
    val startPositionMs: Long = 0L,
    val hostStartTimeNs: Long,
    val outputGroupDeviceIds: List<String> = emptyList()
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): PlayAtPayload? = runCatching {
            protocolJson.decodeFromString<PlayAtPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class PausePayload(
    val hostScheduledTimeNs: Long
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): PausePayload? = runCatching {
            protocolJson.decodeFromString<PausePayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class SeekPayload(
    val targetPositionMs: Long,
    val hostStartTimeNs: Long
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): SeekPayload? = runCatching {
            protocolJson.decodeFromString<SeekPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class SkipPayload(
    val nextTrackId: String? = null,
    val hostStartTimeNs: Long
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): SkipPayload? = runCatching {
            protocolJson.decodeFromString<SkipPayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class MasterVolumePayload(
    val masterVolume: Float
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): MasterVolumePayload? = runCatching {
            protocolJson.decodeFromString<MasterVolumePayload>(json)
        }.getOrNull()
    }
}

@Serializable
data class OutputGroupPayload(
    val outputGroupDeviceIds: List<String>
) {
    fun toJson(): String = protocolJson.encodeToString(this)
    companion object {
        fun fromJson(json: String): OutputGroupPayload? = runCatching {
            protocolJson.decodeFromString<OutputGroupPayload>(json)
        }.getOrNull()
    }
}
