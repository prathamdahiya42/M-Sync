package com.classjam.sync

import android.os.SystemClock
import com.classjam.network.client.KtorClientSocket
import com.classjam.network.protocol.Envelope
import com.classjam.network.protocol.MessageTypes
import com.classjam.network.protocol.PingPayload
import com.classjam.network.protocol.PongPayload
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class ClockSample(
    val rttNs: Long,
    val offsetNs: Long
)

class ClockSyncEngine(
    private val clientSocket: KtorClientSocket,
    private val sampleCount: Int = 20,
    private val topCount: Int = 5
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seqGenerator = AtomicLong(1L)

    // Offset in nanoseconds: hostTimeNs = localTimeNs + offsetNs
    // (Positive means host is ahead of local; negative means local is ahead)
    private val _offsetNs = MutableStateFlow(0L)
    val offsetNs: StateFlow<Long> = _offsetNs.asStateFlow()

    private val _averageRttNs = MutableStateFlow(0L)
    val averageRttNs: StateFlow<Long> = _averageRttNs.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private var syncJob: Job? = null
    private var periodicSyncJob: Job? = null

    // Track active sample responses by sequence ID
    private val pendingSamples = mutableMapOf<Int, Long>() // seq -> t0 (clientSendNs)
    private val collectedSamples = mutableListOf<ClockSample>()
    private var sampleCompleter: CompletableDeferred<Unit>? = null

    init {
        // Listen for PONG messages
        scope.launch {
            clientSocket.incomingEnvelopes.collect { envelope ->
                if (envelope.type == MessageTypes.PONG) {
                    val pong = PongPayload.fromJson(envelope.payload)
                    if (pong != null) {
                        val t2 = SystemClock.elapsedRealtimeNanos()
                        val t0 = pendingSamples.remove(pong.seq) ?: pong.clientSendNs
                        val t1 = pong.serverReceiveNs

                        val rtt = t2 - t0
                        if (rtt > 0) {
                            // offset = hostTime - (t0 + t2)/2
                            val offset = t1 - ((t0 + t2) / 2)
                            synchronized(collectedSamples) {
                                collectedSamples.add(ClockSample(rttNs = rtt, offsetNs = offset))
                                if (collectedSamples.size >= sampleCount) {
                                    sampleCompleter?.complete(Unit)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    fun startPeriodicSync() {
        periodicSyncJob?.cancel()
        periodicSyncJob = scope.launch {
            while (isActive) {
                runSyncPass()
                delay(30_000L) // Re-run clock sync every 30 seconds
            }
        }
    }

    suspend fun runSyncPass() {
        _isSyncing.value = true
        synchronized(collectedSamples) {
            collectedSamples.clear()
            pendingSamples.clear()
        }
        val completer = CompletableDeferred<Unit>()
        sampleCompleter = completer

        try {
            // Send 20 PING samples spaced by 50ms
            for (i in 1..sampleCount) {
                val t0 = SystemClock.elapsedRealtimeNanos()
                pendingSamples[i] = t0
                val ping = PingPayload(clientSendNs = t0, seq = i)
                val envelope = Envelope(
                    type = MessageTypes.PING,
                    seq = seqGenerator.getAndIncrement(),
                    ts = System.currentTimeMillis(),
                    payload = ping.toJson()
                )
                clientSocket.sendEnvelope(envelope)
                delay(40L)
            }

            // Wait up to 2 seconds for all samples
            withTimeoutOrNull(2000L) {
                completer.await()
            }

            calculateBestOffset()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _isSyncing.value = false
        }
    }

    private fun calculateBestOffset() {
        val samples = synchronized(collectedSamples) { collectedSamples.toList() }
        if (samples.isEmpty()) return

        // Sort by lowest RTT (least network jitter / queuing delay)
        val sortedByRtt = samples.sortedBy { it.rttNs }
        val bestSamples = sortedByRtt.take(topCount)

        // Compute median offset of the best 5 samples
        val sortedOffsets = bestSamples.map { it.offsetNs }.sorted()
        val medianOffset = if (sortedOffsets.size % 2 == 1) {
            sortedOffsets[sortedOffsets.size / 2]
        } else {
            (sortedOffsets[sortedOffsets.size / 2 - 1] + sortedOffsets[sortedOffsets.size / 2]) / 2
        }

        _offsetNs.value = medianOffset
        _averageRttNs.value = bestSamples.map { it.rttNs }.average().toLong()
    }

    /**
     * Converts a host monotonic timestamp to this client's local monotonic timestamp
     */
    fun toLocalTimeNs(hostTimeNs: Long): Long {
        return hostTimeNs - _offsetNs.value
    }

    /**
     * Converts a local monotonic timestamp to the host's monotonic timestamp
     */
    fun toHostTimeNs(localTimeNs: Long): Long {
        return localTimeNs + _offsetNs.value
    }

    /**
     * Returns current host monotonic time estimated by this client
     */
    fun currentEstimatedHostTimeNs(): Long {
        return SystemClock.elapsedRealtimeNanos() + _offsetNs.value
    }

    fun stop() {
        periodicSyncJob?.cancel()
        syncJob?.cancel()
        scope.cancel()
    }

    companion object {
        /**
         * Pure calculation function for unit tests
         */
        fun computeMedianOffset(samples: List<ClockSample>, topN: Int = 5): Long {
            if (samples.isEmpty()) return 0L
            val best = samples.sortedBy { it.rttNs }.take(topN)
            val sortedOffsets = best.map { it.offsetNs }.sorted()
            return if (sortedOffsets.size % 2 == 1) {
                sortedOffsets[sortedOffsets.size / 2]
            } else {
                (sortedOffsets[sortedOffsets.size / 2 - 1] + sortedOffsets[sortedOffsets.size / 2]) / 2
            }
        }
    }
}
