package com.classjam.player

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sin

object ClickTrackGenerator {

    /**
     * Generates a 60-second procedural calibration click track (WAV):
     * Sharp 1000 Hz beep for 25ms every 1.000 second, followed by silence.
     * Perfect for aligning multiple phones and Bluetooth speakers by ear.
     */
    fun getOrCreateClickTrack(context: Context): File {
        val clickFile = File(context.cacheDir, "calibration_click_track.wav")
        if (clickFile.exists() && clickFile.length() > 1000) {
            return clickFile
        }

        val sampleRate = 44100
        val durationSeconds = 60
        val totalSamples = sampleRate * durationSeconds
        val numChannels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * numChannels * (bitsPerSample / 8)
        val dataChunkSize = totalSamples * numChannels * (bitsPerSample / 8)
        val totalFileSize = 36 + dataChunkSize

        FileOutputStream(clickFile).use { out ->
            // RIFF header
            out.write("RIFF".toByteArray())
            out.write(intToByteArray(totalFileSize))
            out.write("WAVE".toByteArray())

            // fmt sub-chunk
            out.write("fmt ".toByteArray())
            out.write(intToByteArray(16)) // Subchunk1Size
            out.write(shortToByteArray(1)) // AudioFormat (PCM = 1)
            out.write(shortToByteArray(numChannels.toShort()))
            out.write(intToByteArray(sampleRate))
            out.write(intToByteArray(byteRate))
            out.write(shortToByteArray((numChannels * (bitsPerSample / 8)).toShort())) // BlockAlign
            out.write(shortToByteArray(bitsPerSample.toShort()))

            // data sub-chunk
            out.write("data".toByteArray())
            out.write(intToByteArray(dataChunkSize))

            // Audio samples: Beep for first 25ms of each second, then silence
            val buffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
            val beepSamples = (sampleRate * 0.025).toInt() // 25ms beep
            val beepFreq = 1200.0 // 1200 Hz tone

            for (sampleIdx in 0 until totalSamples) {
                val secondOffset = sampleIdx % sampleRate
                val sampleValue: Short = if (secondOffset < beepSamples) {
                    val angle = 2.0 * Math.PI * beepFreq * secondOffset / sampleRate
                    // Apply smooth envelope to prevent clicking
                    val envelope = sin(Math.PI * secondOffset / beepSamples)
                    (sin(angle) * envelope * 32000.0).toInt().toShort()
                } else {
                    0
                }

                buffer.putShort(sampleValue)
                if (!buffer.hasRemaining()) {
                    out.write(buffer.array())
                    buffer.clear()
                }
            }

            if (buffer.position() > 0) {
                out.write(buffer.array(), 0, buffer.position())
            }
        }

        return clickFile
    }

    private fun intToByteArray(value: Int): ByteArray {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array()
    }

    private fun shortToByteArray(value: Short): ByteArray {
        return ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value).array()
    }
}
