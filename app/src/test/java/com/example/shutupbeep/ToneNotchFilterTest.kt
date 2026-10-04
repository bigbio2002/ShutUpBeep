package com.example.shutupbeep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sin
import kotlin.math.sqrt

class ToneNotchFilterTest {

    @Test
    fun testNotchFilterAttenuatesToneFrequency() {
        val filter = ToneNotchFilter(notchFreqHz = 1000.0, sampleRateHz = 16000.0, qFactor = 8.0)
        val sampleRate = 16000.0
        val numSamples = 1600 // 100 ms

        // Generate pure 1000 Hz sine wave
        val buffer = FloatArray(numSamples) { i ->
            sin(2.0 * Math.PI * 1000.0 * i / sampleRate).toFloat()
        }

        val originalRms = calculateRms(buffer)
        filter.processInPlace(buffer)
        val filteredRms = calculateRms(buffer)

        // Verify >30 dB attenuation of 1000 Hz tone
        assertTrue("Expected filtered RMS < 0.05, got $filteredRms", filteredRms < 0.05f)
        assertTrue("Filtered RMS should be much less than original RMS", filteredRms < originalRms * 0.05f)
    }

    @Test
    fun testNotchFilterPassesHumanSpeechFrequencies() {
        val filter = ToneNotchFilter(notchFreqHz = 1000.0, sampleRateHz = 16000.0, qFactor = 8.0)
        val sampleRate = 16000.0
        val numSamples = 1600

        // Generate 200 Hz pitch (male voice fundamental frequency)
        val buffer = FloatArray(numSamples) { i ->
            sin(2.0 * Math.PI * 200.0 * i / sampleRate).toFloat()
        }

        val originalRms = calculateRms(buffer)
        filter.processInPlace(buffer)
        val filteredRms = calculateRms(buffer)

        // 200 Hz speech frequency should pass through almost 100% intact
        assertEquals(originalRms, filteredRms, 0.05f)
    }

    private fun calculateRms(buffer: FloatArray): Float {
        var sumSq = 0f
        // Skip first 100 samples for filter settling time
        for (i in 100 until buffer.size) {
            sumSq += buffer[i] * buffer[i]
        }
        return sqrt(sumSq / (buffer.size - 100))
    }
}
