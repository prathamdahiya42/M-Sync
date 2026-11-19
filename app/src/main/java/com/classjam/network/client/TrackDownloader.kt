package com.classjam.network.client

import android.content.Context
import com.classjam.core.model.Track
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

class TrackDownloader(
    private val context: Context
) {
    private val client = HttpClient(CIO)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Enforce max 3 concurrent downloads per client
    private val downloadSemaphore = Semaphore(3)

    private val cacheDir = File(context.cacheDir, "tracks").apply { mkdirs() }

    // Active downloading progress: trackId -> Int (0..100)
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    // Ready cached tracks: trackId -> local File
    private val _readyTracks = MutableStateFlow<Map<String, File>>(emptyMap())
    val readyTracks: StateFlow<Map<String, File>> = _readyTracks.asStateFlow()

    private val activeDownloadJobs = ConcurrentHashMap<String, Job>()

    fun getCachedFile(trackId: String): File? {
        val finalFile = File(cacheDir, "$trackId.audio")
        return if (finalFile.exists()) finalFile else null
    }

    fun isTrackReady(trackId: String): Boolean {
        return getCachedFile(trackId) != null
    }

    fun downloadTrack(
        hostIp: String,
        hostPort: Int,
        track: Track,
        onProgress: (progress: Int) -> Unit = {},
        onReady: (trackId: String, file: File) -> Unit = { _, _ -> },
        onError: (trackId: String, error: String) -> Unit = { _, _ -> }
    ) {
        val trackId = track.id
        val finalFile = File(cacheDir, "$trackId.audio")

        // 1. If final verified file exists, signal ready immediately
        if (finalFile.exists()) {
            val currentReady = _readyTracks.value.toMutableMap()
            currentReady[trackId] = finalFile
            _readyTracks.value = currentReady
            onProgress(100)
            onReady(trackId, finalFile)
            return
        }

        // Avoid starting duplicate jobs for the same track
        if (activeDownloadJobs.containsKey(trackId)) return

        val job = scope.launch {
            downloadSemaphore.withPermit {
                val tempFile = File(cacheDir, "$trackId.tmp")
                try {
                    val existingBytes = if (tempFile.exists()) tempFile.length() else 0L

                    val response = client.get("http://$hostIp:$hostPort/track/$trackId") {
                        if (existingBytes > 0 && existingBytes < track.sizeBytes) {
                            header(HttpHeaders.Range, "bytes=$existingBytes-")
                        }
                    }

                    val isPartial = response.status == HttpStatusCode.PartialContent
                    val channel: ByteReadChannel = response.bodyAsChannel()

                    val randomAccessFile = RandomAccessFile(tempFile, "rw")
                    if (isPartial) {
                        randomAccessFile.seek(existingBytes)
                    } else {
                        randomAccessFile.setLength(0)
                        randomAccessFile.seek(0)
                    }

                    val buffer = ByteArray(8192)
                    var totalDownloaded = if (isPartial) existingBytes else 0L
                    val totalExpected = track.sizeBytes

                    while (!channel.isClosedForRead) {
                        val read = channel.readAvailable(buffer)
                        if (read <= 0) break
                        randomAccessFile.write(buffer, 0, read)
                        totalDownloaded += read

                        val progress = if (totalExpected > 0) {
                            ((totalDownloaded * 100) / totalExpected).toInt().coerceIn(0, 100)
                        } else 0

                        updateProgress(trackId, progress)
                        onProgress(progress)
                    }
                    randomAccessFile.close()

                    // Verify SHA-256 checksum if provided by host
                    val verified = verifyChecksum(tempFile, track.sha256)
                    if (verified) {
                        tempFile.renameTo(finalFile)
                        val currentReady = _readyTracks.value.toMutableMap()
                        currentReady[trackId] = finalFile
                        _readyTracks.value = currentReady

                        updateProgress(trackId, 100)
                        onProgress(100)
                        onReady(trackId, finalFile)
                    } else {
                        tempFile.delete()
                        onError(trackId, "SHA-256 verification failed for track $trackId")
                    }
                } catch (e: Exception) {
                    onError(trackId, "Download failed: ${e.localizedMessage}")
                } finally {
                    activeDownloadJobs.remove(trackId)
                }
            }
        }
        activeDownloadJobs[trackId] = job
    }

    private fun verifyChecksum(file: File, expectedSha256: String): Boolean {
        if (expectedSha256.isBlank()) return true // No checksum provided
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            file.inputStream().use { input ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            val computed = digest.digest().joinToString("") { "%02x".format(it) }
            computed.equals(expectedSha256, ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    private fun updateProgress(trackId: String, progress: Int) {
        val current = _downloadProgress.value.toMutableMap()
        current[trackId] = progress
        _downloadProgress.value = current
    }

    fun clearCache(keepSongs: Boolean) {
        if (keepSongs) return
        activeDownloadJobs.values.forEach { it.cancel() }
        activeDownloadJobs.clear()
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _downloadProgress.value = emptyMap()
        _readyTracks.value = emptyMap()
    }
}
