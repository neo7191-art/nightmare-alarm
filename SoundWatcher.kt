package com.you.nightmarealarm

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.log10
import kotlin.math.sqrt

class SoundWatcher(
    private val thresholdDb: Double,
    private val onThresholdExceeded: () -> Unit
) {
    @Volatile private var running = false
    private var thread: Thread? = null

    @SuppressLint("MissingPermission")
    fun start() {
        if (running) return
        running = true

        thread = Thread {
            val sampleRate = 44100
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )

            if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                running = false
                return@Thread
            }

            val buffer = ShortArray(minBuf)
            recorder.startRecording()

            var lastTrigger = 0L

            while (running) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    var sum = 0.0
                    for (i in 0 until read) {
                        val s = buffer[i].toDouble()
                        sum += s * s
                    }
                    val rms = sqrt(sum / read)
                    val db = 20 * log10((rms / 32768.0).coerceAtLeast(1e-9)) + 90.0

                    val now = System.currentTimeMillis()
                    if (db > thresholdDb && now - lastTrigger > 8000) {
                        lastTrigger = now
                        onThresholdExceeded()
                    }
                }
            }

            try {
                recorder.stop()
            } catch (_: Exception) {}
            recorder.release()
        }.also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(1000)
        thread = null
    }
}