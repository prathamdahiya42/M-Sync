package com.classjam.player

import android.content.Context
import android.media.AudioAttributes as AndroidAudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.classjam.core.model.Track
import com.classjam.sync.ClockSyncEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import kotlin.math.max

class SyncedExoPlayer(
    private val context: Context,
    private val clockSyncEngine: ClockSyncEngine
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true // Automatic audio focus handling
        )
        .build()

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var scheduledStartJob: Job? = null
    private var scheduledPauseJob: Job? = null
    private var scheduledSeekJob: Job? = null

    // Track active playback details for resuming after call interruptions
    private var lastHostStartTimeNs: Long = 0L
    private var lastStartPositionMs: Long = 0L
    private var wasPlayingBeforeCallLoss = false

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _masterVolume = MutableStateFlow(1.0f)
    val masterVolume: StateFlow<Float> = _masterVolume.asStateFlow()

    private val _localVolume = MutableStateFlow(1.0f)
    val localVolume: StateFlow<Float> = _localVolume.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _inOutputGroup = MutableStateFlow(true)
    val inOutputGroup: StateFlow<Boolean> = _inOutputGroup.asStateFlow()

    var manualOffsetNs: Long = 0L

    // Audio Focus Listener for Phone Calls & Alarms
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Another app took full focus (e.g. YouTube, Spotify)
                wasPlayingBeforeCallLoss = player.isPlaying
                player.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // Phone call incoming / ringing
                wasPlayingBeforeCallLoss = player.isPlaying
                player.playWhenReady = false
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // Notification sound or navigation: duck volume
                player.volume = (_masterVolume.value * _localVolume.value) * 0.2f
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                // Call ended: restore volume and re-sync position
                applyEffectiveVolume()
                if (wasPlayingBeforeCallLoss && lastHostStartTimeNs > 0L) {
                    val currentHostNs = clockSyncEngine.currentEstimatedHostTimeNs()
                    val elapsedMs = (currentHostNs - lastHostStartTimeNs) / 1_000_000L
                    val expectedPosMs = lastStartPositionMs + elapsedMs
                    if (expectedPosMs < player.duration) {
                        player.seekTo(expectedPosMs)
                        if (_inOutputGroup.value && !_isMuted.value) {
                            player.playWhenReady = true
                        }
                    }
                }
            }
        }
    }

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    _durationMs.value = player.duration.coerceAtLeast(0L)
                }
            }
        })

        // Periodic position updater
        scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    _currentPositionMs.value = player.currentPosition
                }
                delay(200L)
            }
        }
    }

    fun schedulePlayAt(
        audioFile: File?,
        uriStr: String?,
        startPositionMs: Long,
        hostStartTimeNs: Long,
        outputGroupDeviceIds: List<String>,
        selfDeviceId: String
    ) {
        val isInGroup = outputGroupDeviceIds.isEmpty() || outputGroupDeviceIds.contains(selfDeviceId)
        _inOutputGroup.value = isInGroup

        this.lastHostStartTimeNs = hostStartTimeNs
        this.lastStartPositionMs = startPositionMs

        scheduledStartJob?.cancel()

        val mediaItem = when {
            audioFile != null && audioFile.exists() -> MediaItem.fromUri(Uri.fromFile(audioFile))
            !uriStr.isNullOrBlank() -> MediaItem.fromUri(Uri.parse(uriStr))
            else -> return
        }

        player.setMediaItem(mediaItem)
        player.prepare()
        player.seekTo(startPositionMs)
        player.playWhenReady = false

        applyEffectiveVolume()

        scheduledStartJob = scope.launch(Dispatchers.Default) {
            val localTargetNs = clockSyncEngine.toLocalTimeNs(hostStartTimeNs) + manualOffsetNs
            var nowNs = SystemClock.elapsedRealtimeNanos()
            val initialDelayNs = localTargetNs - nowNs

            if (initialDelayNs > 10_000_000L) {
                val delayMs = (initialDelayNs - 10_000_000L) / 1_000_000L
                delay(delayMs)
            }

            while (SystemClock.elapsedRealtimeNanos() < localTargetNs) {
                // Precision spin-wait
            }

            withContext(Dispatchers.Main) {
                if (_inOutputGroup.value && !_isMuted.value) {
                    player.playWhenReady = true
                }
            }
        }
    }

    fun schedulePause(hostScheduledTimeNs: Long) {
        scheduledPauseJob?.cancel()
        scheduledPauseJob = scope.launch(Dispatchers.Default) {
            val localTargetNs = clockSyncEngine.toLocalTimeNs(hostScheduledTimeNs) + manualOffsetNs
            val delayMs = ((localTargetNs - SystemClock.elapsedRealtimeNanos()) / 1_000_000L).coerceAtLeast(0L)
            if (delayMs > 0) delay(delayMs)

            withContext(Dispatchers.Main) {
                player.playWhenReady = false
            }
        }
    }

    fun scheduleSeek(targetPositionMs: Long, hostStartTimeNs: Long) {
        this.lastHostStartTimeNs = hostStartTimeNs
        this.lastStartPositionMs = targetPositionMs

        scheduledSeekJob?.cancel()
        scheduledSeekJob = scope.launch(Dispatchers.Main) {
            player.seekTo(targetPositionMs)
            player.playWhenReady = false

            val localTargetNs = clockSyncEngine.toLocalTimeNs(hostStartTimeNs) + manualOffsetNs
            val delayMs = ((localTargetNs - SystemClock.elapsedRealtimeNanos()) / 1_000_000L).coerceAtLeast(0L)
            if (delayMs > 0) delay(delayMs)

            if (_inOutputGroup.value && !_isMuted.value) {
                player.playWhenReady = true
            }
        }
    }

    fun setMasterVolume(volume: Float) {
        _masterVolume.value = volume.coerceIn(0.0f, 1.0f)
        applyEffectiveVolume()
    }

    fun setLocalVolume(volume: Float) {
        _localVolume.value = volume.coerceIn(0.0f, 1.0f)
        applyEffectiveVolume()
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        applyEffectiveVolume()
    }

    fun setInOutputGroup(inGroup: Boolean) {
        _inOutputGroup.value = inGroup
        applyEffectiveVolume()
    }

    private fun applyEffectiveVolume() {
        val effective = if (_isMuted.value || !_inOutputGroup.value) {
            0.0f
        } else {
            _masterVolume.value * _localVolume.value
        }
        player.volume = effective
    }

    fun release() {
        scheduledStartJob?.cancel()
        scheduledPauseJob?.cancel()
        scheduledSeekJob?.cancel()
        scope.cancel()
        player.release()
    }
}
