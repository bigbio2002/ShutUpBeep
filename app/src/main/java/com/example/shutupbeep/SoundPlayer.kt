package com.example.shutupbeep

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/**
 * High-performance, zero-latency tone generator using a static looping [AudioTrack].
 *
 * When initialized, a seamless square-wave cycle is loaded into memory and kept active in the audio mixer
 * at zero volume. Un-muting and muting takes effect in the very next audio mixer period (<5ms),
 * eliminating any track startup or thread-scheduling delay when speech is detected.
 */
class SoundPlayer {
    companion object {
        private const val TAG = "SoundPlayer"
        private const val SAMPLE_RATE = 48_000
        private const val FREQUENCY_HZ = 880.0 // A5 harsh square wave
        private const val AMPLITUDE = 0.95f
    }

    private var audioTrack: AudioTrack? = null

    @Volatile
    private var isPrepared = false

    @Volatile
    private var isTonePlaying = false

    @Synchronized
    fun prepare() {
        if (isPrepared) return
        try {
            // Generate seamless looping buffer
            // 48000 / 880 = 600 / 11 -> exactly 880 cycles in 48000 samples (1 full second)
            val numSamples = SAMPLE_RATE
            val buffer = ShortArray(numSamples)
            val periodSamples = SAMPLE_RATE / FREQUENCY_HZ
            val ampValue = (AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
            val negAmpValue = (-AMPLITUDE * Short.MAX_VALUE).toInt().toShort()

            for (i in 0 until numSamples) {
                val phase = (i % periodSamples) / periodSamples
                buffer[i] = if (phase < 0.5) ampValue else negAmpValue
            }

            val bufferSizeInBytes = numSamples * 2

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSizeInBytes)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            val written = track.write(buffer, 0, numSamples)
            if (written != numSamples) {
                Log.w(TAG, "AudioTrack wrote $written of $numSamples samples")
            }

            track.setLoopPoints(0, numSamples, -1) // Loop infinitely
            track.setVolume(0.0f) // Start muted
            track.play()

            audioTrack = track
            isPrepared = true
            isTonePlaying = false
            Log.d(TAG, "SoundPlayer primed and standing by (muted)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare SoundPlayer", e)
        }
    }

    /**
     * Instantly mutes or un-mutes the tone.
     * Safe to call from any thread (e.g. directly from audio recording thread).
     */
    fun setToneActive(active: Boolean) {
        if (isTonePlaying == active) return
        isTonePlaying = active
        val track = audioTrack ?: return

        try {
            if (active) {
                track.setVolume(1.0f)
            } else {
                track.setVolume(0.0f)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume in SoundPlayer", e)
        }
    }

    @Synchronized
    fun release() {
        setToneActive(false)
        isPrepared = false
        audioTrack?.let { track ->
            try {
                track.stop()
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping AudioTrack", e)
            }
            try {
                track.release()
            } catch (e: Exception) {
                Log.w(TAG, "Error releasing AudioTrack", e)
            }
        }
        audioTrack = null
        Log.d(TAG, "SoundPlayer released")
    }
}
