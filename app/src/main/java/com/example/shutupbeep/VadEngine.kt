package com.example.shutupbeep

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.nio.FloatBuffer
import java.nio.LongBuffer

class VadEngine(
    private val context: Context,
    @Volatile var speechThreshold: Float = 0.5f,
    private val onSpeechStateChanged: (isSpeech: Boolean) -> Unit,
) {
    companion object {
        private const val TAG = "VadEngine"
        private const val SAMPLE_RATE = 16_000
        private const val WINDOW_SIZE = 512 // 32ms at 16kHz
        private const val STATE_DIM = 128
        private const val STATE_LAYERS = 2
    }

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null

    private val state = FloatArray(STATE_LAYERS * 1 * STATE_DIM)

    private var audioRecord: AudioRecord? = null
    private val minBufferSize = AudioRecord.getMinBufferSize(
        SAMPLE_RATE,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
    ).coerceAtLeast(WINDOW_SIZE * 2 * 4)

    @Volatile
    private var isRunning = false
    private var recordingThread: Thread? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        state.fill(0f)

        try {
            val modelBytes = context.assets.open("silero_vad.onnx").use { it.readBytes() }
            val env = OrtEnvironment.getEnvironment()
            val session = env.createSession(modelBytes, OrtSession.SessionOptions())
            ortEnv = env
            ortSession = session

            Log.d(TAG, "ONNX session created. Inputs: ${session.inputNames}, Outputs: ${session.outputNames}")

            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize,
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                record.release()
                stop()
                return
            }

            audioRecord = record
            record.startRecording()
            Log.d(TAG, "AudioRecord started, state=${record.recordingState}")

            recordingThread = Thread(::recordingLoop, "vad-recording-thread").also {
                it.start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start VadEngine", e)
            stop()
        }
    }

    fun stop() {
        isRunning = false
        try {
            recordingThread?.join(1_000)
        } catch (e: Exception) {
            Log.w(TAG, "Interrupted while joining recording thread", e)
        }
        recordingThread = null

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }
        audioRecord?.release()
        audioRecord = null

        ortSession?.close()
        ortSession = null
        ortEnv?.close()
        ortEnv = null
    }

    private fun recordingLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)

        val pcmBuffer = ShortArray(WINDOW_SIZE)
        val floatBuffer = FloatArray(WINDOW_SIZE)
        val stateShape = longArrayOf(STATE_LAYERS.toLong(), 1L, STATE_DIM.toLong())
        val inputShape = longArrayOf(1L, WINDOW_SIZE.toLong())
        var lastSpeechState = false

        val env = ortEnv ?: return
        val session = ortSession ?: return

        // Create constant sample rate tensor once for the entire session
        val srTensor = OnnxTensor.createTensor(
            env,
            LongBuffer.wrap(longArrayOf(SAMPLE_RATE.toLong())),
            longArrayOf(),
        )

        try {
            while (isRunning) {
                var samplesRead = 0
                val record = audioRecord ?: break

                while (samplesRead < WINDOW_SIZE && isRunning) {
                    val n = record.read(pcmBuffer, samplesRead, WINDOW_SIZE - samplesRead)
                    if (n < 0) {
                        Log.e(TAG, "AudioRecord.read() error: $n")
                        return
                    }
                    if (n > 0) samplesRead += n
                }
                if (!isRunning) break

                // Normalize 16-bit PCM shorts to [-1.0f, 1.0f]
                for (i in 0 until WINDOW_SIZE) {
                    floatBuffer[i] = pcmBuffer[i].toFloat() / 32768.0f
                }

                val inputTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(floatBuffer),
                    inputShape,
                )
                val stateTensor = OnnxTensor.createTensor(
                    env,
                    FloatBuffer.wrap(state),
                    stateShape,
                )

                val inputs = mapOf(
                    "input" to inputTensor,
                    "state" to stateTensor,
                    "sr" to srTensor,
                )

                val results = session.run(inputs)

                val outputTensor = results["output"].get() as OnnxTensor
                @Suppress("UNCHECKED_CAST")
                val probability = (outputTensor.value as Array<FloatArray>)[0][0]

                val stateNTensor = results["stateN"].get() as OnnxTensor
                val sBuf = stateNTensor.floatBuffer
                sBuf.rewind()
                sBuf.get(state)

                inputTensor.close()
                stateTensor.close()
                results.close()

                val isSpeech = probability >= speechThreshold
                if (isSpeech != lastSpeechState) {
                    Log.d(TAG, "Speech state changed: $isSpeech (prob=$probability, threshold=$speechThreshold)")
                    lastSpeechState = isSpeech
                    onSpeechStateChanged(isSpeech)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Recording loop crashed", e)
        } finally {
            try {
                srTensor.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing srTensor", e)
            }
            if (lastSpeechState) {
                onSpeechStateChanged(false)
            }
        }
    }
}
