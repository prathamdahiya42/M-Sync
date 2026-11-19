package com.classjam.player

import com.classjam.core.model.QueueItem
import com.classjam.core.model.Track
import com.classjam.network.client.KtorClientSocket
import com.classjam.network.client.TrackDownloader
import com.classjam.network.protocol.Envelope
import com.classjam.network.protocol.ManifestPayload
import com.classjam.network.protocol.MessageTypes
import com.classjam.network.protocol.ReadyPayload
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

class QueuePrefetchManager(
    private val clientSocket: KtorClientSocket,
    private val trackDownloader: TrackDownloader,
    private val hostIp: String,
    private val hostPort: Int
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seqGenerator = AtomicLong(100L)

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack.asStateFlow()

    private val _queue = MutableStateFlow<List<QueueItem>>(emptyList())
    val queue: StateFlow<List<QueueItem>> = _queue.asStateFlow()

    fun startListening() {
        scope.launch {
            clientSocket.incomingEnvelopes.collect { envelope ->
                if (envelope.type == MessageTypes.MANIFEST) {
                    val manifest = ManifestPayload.fromJson(envelope.payload) ?: return@collect
                    _currentTrack.value = manifest.currentTrack
                    _queue.value = manifest.queue
                    handleManifestUpdate(manifest)
                }
            }
        }
    }

    private fun handleManifestUpdate(manifest: ManifestPayload) {
        val tracksToDownload = mutableListOf<Track>()

        // 1. Current track must be ready
        manifest.currentTrack?.let { tracksToDownload.add(it) }

        // 2. Prefetch the next 2 queued tracks
        manifest.queue.take(2).forEach { item ->
            tracksToDownload.add(item.track)
        }

        // Trigger downloads
        tracksToDownload.forEach { track ->
            trackDownloader.downloadTrack(
                hostIp = hostIp,
                hostPort = hostPort,
                track = track,
                onProgress = { progress ->
                    sendReadyUpdate(track.id, isReady = progress >= 100, progress = progress)
                },
                onReady = { trackId, _ ->
                    sendReadyUpdate(trackId, isReady = true, progress = 100)
                },
                onError = { trackId, _ ->
                    sendReadyUpdate(trackId, isReady = false, progress = 0)
                }
            )
        }
    }

    private fun sendReadyUpdate(trackId: String, isReady: Boolean, progress: Int) {
        val payload = ReadyPayload(trackId = trackId, isReady = isReady, progress = progress)
        val envelope = Envelope(
            type = MessageTypes.READY,
            seq = seqGenerator.getAndIncrement(),
            ts = System.currentTimeMillis(),
            payload = payload.toJson()
        )
        scope.launch {
            clientSocket.sendEnvelope(envelope)
        }
    }

    fun stop() {
        scope.cancel()
    }
}
