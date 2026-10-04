package com.example.shutupbeep

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.sin

/**
 * High-performance, zero-latency streaming tone generator using [AudioTrack] in [AudioTrack.MODE_STREAM].
 * Routes tone audio through [AudioAttributes.USAGE_MEDIA] for universal device volume compatibility.
 */
class SoundPlayer {
    companion object {
        private const val TAG = "SoundPlayer"
        private const val SAMPLE_RATE = 48_000
        const val TONE_FREQUENCY_HZ = 1000.0 // 1 kHz loud tone
        private const val AMPLITUDE = 0.95f
    }

    private var audioTrack: AudioTrack? = null

    @Volatile
    private var isPrepared = false

    @Volatile
    private var isPlaying = false

    @Volatile
    private var playerThread: Thread? = null

    @Synchronized
    fun prepare() {
        if (isPrepared) return
        try {
            val minBufSize = AudioTrack.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(4800)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack = track
            isPrepared = true
            isPlaying = false

            playerThread = Thread(::playbackLoop, "sound-player-thread").also { it.start() }
            Log.d(TAG, "SoundPlayer primed and standing by")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare SoundPlayer", e)
        }
    }

    /**
     * Instantly mutes or un-mutes the tone output.
     */
    fun setToneActive(active: Boolean) {
        if (isPlaying == active) return
        isPlaying = active
        Log.d(TAG, "setToneActive: $active")
    }

    private fun playbackLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

        val chunkSize = 480 // 10ms chunks at 48kHz
        val pcmBuffer = ShortArray(chunkSize)
        val ampValue = (AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
        val negAmpValue = (-AMPLITUDE * Short.MAX_VALUE).toInt().toShort()

        var sampleIndex = 0L

        try {
            val track = audioTrack ?: return
            track.play()

            while (isPrepared) {
                if (isPlaying) {
                    for (i in 0 until chunkSize) {
                        val phase = (2.0 * Math.PI * TONE_FREQUENCY_HZ * sampleIndex) / SAMPLE_RATE
                        pcmBuffer[i] = if (sin(phase) >= 0) ampValue else negAmpValue
                        sampleIndex++
                    }
                    track.write(pcmBuffer, 0, chunkSize)
                } else {
                    sampleIndex = 0L
                    Thread.sleep(10)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in SoundPlayer playback loop", e)
        }
    }

    @Synchronized
    fun release() {
        isPrepared = false
        isPlaying = false

        try {
            playerThread?.join(500)
        } catch (_: Exception) {}
        playerThread = null

        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioTrack", e)
        }
        audioTrack = null
        Log.d(TAG, "SoundPlayer released")
    }
}
