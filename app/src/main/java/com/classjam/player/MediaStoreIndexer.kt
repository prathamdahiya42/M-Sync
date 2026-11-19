package com.classjam.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.classjam.core.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.InputStream
import java.security.MessageDigest

class MediaStoreIndexer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _indexedTracks = MutableStateFlow<List<Track>>(emptyList())
    val indexedTracks: StateFlow<List<Track>> = _indexedTracks.asStateFlow()

    private val _isIndexing = MutableStateFlow(false)
    val isIndexing: StateFlow<Boolean> = _isIndexing.asStateFlow()

    fun scanLocalAudio() {
        scope.launch {
            _isIndexing.value = true
            try {
                val tracks = mutableListOf<Track>()
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE
                )
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} > 5000"

                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    "${MediaStore.Audio.Media.TITLE} ASC"
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)

                    while (cursor.moveToNext()) {
                        val mediaId = cursor.getLong(idCol)
                        val title = cursor.getString(titleCol) ?: "Unknown Track"
                        val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                        val duration = cursor.getLong(durCol)
                        val size = cursor.getLong(sizeCol)
                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mediaId)

                        tracks.add(
                            Track(
                                id = "track_$mediaId",
                                title = title,
                                artist = artist,
                                durationMs = duration,
                                sizeBytes = size,
                                sha256 = "", // Computed in background
                                localUriString = contentUri.toString()
                            )
                        )
                    }
                }

                _indexedTracks.value = tracks

                // Compute SHA-256 hashes in background for integrity validation
                tracks.forEachIndexed { index, track ->
                    if (track.localUriString != null && track.sha256.isBlank()) {
                        val hash = computeSha256(Uri.parse(track.localUriString))
                        if (hash.isNotBlank()) {
                            val updated = _indexedTracks.value.toMutableList()
                            if (index < updated.size && updated[index].id == track.id) {
                                updated[index] = updated[index].copy(sha256 = hash)
                                _indexedTracks.value = updated
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isIndexing.value = false
            }
        }
    }

    private fun computeSha256(uri: Uri): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                var read: Int
                while (stream.read(buffer).also { read = it } != -1) {
                    digest.update(buffer, 0, read)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            ""
        }
    }

    fun openTrackInputStream(trackId: String): InputStream? {
        val track = _indexedTracks.value.find { it.id == trackId } ?: return null
        val uriStr = track.localUriString ?: return null
        return try {
            context.contentResolver.openInputStream(Uri.parse(uriStr))
        } catch (e: Exception) {
            null
        }
    }

    fun getTrack(trackId: String): Track? {
        return _indexedTracks.value.find { it.id == trackId }
    }
}
