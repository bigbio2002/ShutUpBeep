package com.example.shutupbeep

import kotlin.math.cos
import kotlin.math.sin

/**
 * Stable 2nd-order Biquad IIR Notch Filter centered at [notchFreqHz] for sample rate [sampleRateHz].
 * Attenuates speaker tone feedback from microphone audio before VAD inference.
 */
class ToneNotchFilter(
    notchFreqHz: Double = 1000.0,
    sampleRateHz: Double = 16000.0,
    qFactor: Double = 5.0,
) {
    private var b0 = 1.0f
    private var b1 = 0.0f
    private var b2 = 0.0f
    private var a1 = 0.0f
    private var a2 = 0.0f

    private var x1 = 0.0f
    private var x2 = 0.0f
    private var y1 = 0.0f
    private var y2 = 0.0f

    init {
        updateCoefficients(notchFreqHz, sampleRateHz, qFactor)
    }

    fun updateCoefficients(notchFreqHz: Double, sampleRateHz: Double, qFactor: Double) {
        val w0 = 2.0 * Math.PI * notchFreqHz / sampleRateHz
        val alpha = sin(w0) / (2.0 * qFactor)
        val cosw0 = cos(w0)

        val a0Raw = 1.0 + alpha
        b0 = (1.0 / a0Raw).toFloat()
        b1 = ((-2.0 * cosw0) / a0Raw).toFloat()
        b2 = (1.0 / a0Raw).toFloat()
        a1 = ((-2.0 * cosw0) / a0Raw).toFloat()
        a2 = ((1.0 - alpha) / a0Raw).toFloat()

        reset()
    }

    fun reset() {
        x1 = 0.0f
        x2 = 0.0f
        y1 = 0.0f
        y2 = 0.0f
    }

    fun processInPlace(buffer: FloatArray) {
        for (i in buffer.indices) {
            val x0 = buffer[i]
            var y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2

            if (y0.isNaN() || y0.isInfinite()) {
                y0 = x0
                reset()
            }

            x2 = x1
            x1 = x0
            y2 = y1
            y1 = y0
            buffer[i] = y0
        }
    }
}
