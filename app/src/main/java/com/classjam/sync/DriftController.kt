package com.classjam.sync

import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

sealed interface DriftCorrectionState {
    data object InSync : DriftCorrectionState
    data class SpeedNudge(val driftMs: Long, val newSpeed: Float) : DriftCorrectionState
    data class HardSeek(val driftMs: Long, val targetPositionMs: Long) : DriftCorrectionState
}

class DriftController(
    private val player: ExoPlayer,
    private val clockSyncEngine: ClockSyncEngine
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controllerJob: Job? = null

    private var hostStartTimeNs: Long = 0L
    private var trackStartPositionMs: Long = 0L
    var manualOffsetMs: Long = 0L

    private val _currentDriftMs = MutableStateFlow(0L)
    val currentDriftMs: StateFlow<Long> = _currentDriftMs.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _correctionState = MutableStateFlow<DriftCorrectionState>(DriftCorrectionState.InSync)
    val correctionState: StateFlow<DriftCorrectionState> = _correctionState.asStateFlow()

    fun updateSessionStart(hostStartTimeNs: Long, trackStartPositionMs: Long) {
        this.hostStartTimeNs = hostStartTimeNs
        this.trackStartPositionMs = trackStartPositionMs
    }

    fun start() {
        controllerJob?.cancel()
        controllerJob = scope.launch {
            while (isActive) {
                delay(2000L) // Drift check every 2 seconds
                if (player.isPlaying && hostStartTimeNs > 0L) {
                    evaluateAndCorrectDrift()
                }
            }
        }
    }

    fun evaluateAndCorrectDrift() {
        val nowHostNs = clockSyncEngine.currentEstimatedHostTimeNs() + (manualOffsetMs * 1_000_000L)
        val elapsedHostNs = nowHostNs - hostStartTimeNs
        if (elapsedHostNs < 0) return

        val expectedPositionMs = trackStartPositionMs + (elapsedHostNs / 1_000_000L)
        val actualPositionMs = player.currentPosition

        val driftMs = actualPositionMs - expectedPositionMs
        _currentDriftMs.value = driftMs

        val action = determineCorrectionAction(driftMs, expectedPositionMs)
        _correctionState.value = action

        when (action) {
            is DriftCorrectionState.InSync -> {
                if (_playbackSpeed.value != 1.0f) {
                    _playbackSpeed.value = 1.0f
                    player.playbackParameters = PlaybackParameters(1.0f)
                }
            }
            is DriftCorrectionState.SpeedNudge -> {
                _playbackSpeed.value = action.newSpeed
                player.playbackParameters = PlaybackParameters(action.newSpeed)
            }
            is DriftCorrectionState.HardSeek -> {
                _playbackSpeed.value = 1.0f
                player.playbackParameters = PlaybackParameters(1.0f)
                player.seekTo(action.targetPositionMs)
            }
        }
    }

    fun stop() {
        controllerJob?.cancel()
        _playbackSpeed.value = 1.0f
        player.playbackParameters = PlaybackParameters(1.0f)
    }

    companion object {
        /**
         * Pure logic for determining drift correction action:
         * - Drift under 20ms: ignore (InSync)
         * - 20ms to 200ms: nudge speed (1.0 +/- 0.02)
         * - Over 200ms: seek to expected position
         */
        fun determineCorrectionAction(driftMs: Long, expectedPositionMs: Long): DriftCorrectionState {
            val absDrift = abs(driftMs)
            return when {
                absDrift < 20L -> DriftCorrectionState.InSync
                absDrift in 20L..200L -> {
                    // If actual > expected (running ahead), slow down to 0.98f
                    // If actual < expected (running behind), speed up to 1.02f
                    val nudgeSpeed = if (driftMs > 0) 0.98f else 1.02f
                    DriftCorrectionState.SpeedNudge(driftMs, nudgeSpeed)
                }
                else -> {
                    DriftCorrectionState.HardSeek(driftMs, expectedPositionMs)
                }
            }
        }
    }
}
