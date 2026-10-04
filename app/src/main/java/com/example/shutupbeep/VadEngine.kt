package com.example.shutupbeep

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.media.AudioManager
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.util.Log
import java.nio.FloatBuffer
import java.nio.LongBuffer

class VadEngine(
    private val context: Context,
    private val onListeningStarted: () -> Unit,
    private val onSpeechStateChanged: (isSpeech: Boolean) -> Unit,
    private val onError: (message: String) -> Unit,
    private val onProbabilityUpdated: ((Float) -> Unit)? = null,
    private val onAudioLevelUpdated: ((Float) -> Unit)? = null,
) {
    companion object {
        private const val TAG = "VadEngine"
        private const val SAMPLE_RATE = 16_000
        private const val WINDOW_SIZE = 256
        private const val STATE_DIM = 128
        private const val STATE_LAYERS = 2
        private const val SPEECH_THRESHOLD = 0.10f
        private const val SPEECH_CONFIRM_MILLIS = 32L
        private const val SPEECH_RELEASE_MILLIS = 850L
        private const val TARGET_MODEL_INPUT_RMS = 0.08f
        private const val MAX_INPUT_GAIN = 32f
        private const val GAIN_ATTACK = 0.5f
        private const val GAIN_RELEASE = 0.15f
    }

    @Volatile
    private var ortSession: OrtSession? = null

    @Volatile
    private var audioRecord: AudioRecord? = null
    private var acousticEchoCanceler: AcousticEchoCanceler? = null

    @Volatile
    private var isRunning = false
    private var recordingThread: Thread? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        recordingThread = Thread(::initializeAndRecord, "silero-vad-thread").also { it.start() }
    }

    private fun initializeAndRecord() {
        try {
            val modelBytes = context.assets.open("silero_vad.onnx").use { it.readBytes() }
            val session = OrtEnvironment.getEnvironment().createSession(
                modelBytes,
                OrtSession.SessionOptions(),
            )
            ortSession = session

            Log.d(TAG, "Silero VAD session ready. Inputs=${session.inputNames}, outputs=${session.outputNames}")
            check(session.inputNames.containsAll(listOf("input", "state", "sr"))) {
                "Unexpected Silero VAD model inputs: ${session.inputNames}"
            }
            check(session.outputNames.containsAll(listOf("output", "stateN"))) {
                "Unexpected Silero VAD model outputs: ${session.outputNames}"
            }
            check(isRunning) { "Speech detection was stopped during initialization" }

            val record = createAudioRecord()
            audioRecord = record
            enableEchoCancellation(record)

            check(isRunning) { "Speech detection was stopped during initialization" }
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw IllegalStateException("Microphone did not start recording")
            }

            Log.i(TAG, "Microphone recording started at ${SAMPLE_RATE}Hz")
            if (!isRunning) return
            onListeningStarted()
            recordingLoop()
        } catch (e: Exception) {
            if (isRunning) {
                Log.e(TAG, "Failed to run Silero VAD", e)
                onError(e.message ?: "Unable to start speech detection")
            }
        } finally {
            isRunning = false
            cleanup()
        }
    }

    private fun createAudioRecord(): AudioRecord {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val supportsUnprocessed = audioManager
            .getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true"
        val audioSource = if (supportsUnprocessed) {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
        val minimumBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        check(minimumBufferSize > 0) {
            "Device does not support 16 kHz mono microphone capture (error $minimumBufferSize)"
        }

        val record = AudioRecord(
            audioSource,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimumBufferSize, WINDOW_SIZE * 2 * 4),
        )
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            throw IllegalStateException("Microphone recorder failed to initialize")
        }
        Log.i(TAG, "Using ${if (supportsUnprocessed) "unprocessed" else "voice-recognition"} audio source")
        return record
    }

    private fun enableEchoCancellation(record: AudioRecord) {
        if (!AcousticEchoCanceler.isAvailable()) return
        try {
            acousticEchoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply {
                enabled = true
                Log.i(TAG, "Acoustic echo cancellation enabled")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not enable acoustic echo cancellation", e)
        }
    }

    fun stop() {
        isRunning = false
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord", e)
        }

        try {
            recordingThread?.join()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            Log.w(TAG, "Interrupted while waiting for VAD thread to stop", e)
        }
        recordingThread = null
    }

    private fun cleanup() {
        try {
            acousticEchoCanceler?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AcousticEchoCanceler", e)
        }
        acousticEchoCanceler = null

        try {
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null

        try {
            ortSession?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing Silero session", e)
        }
        ortSession = null
    }

    private fun recordingLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)

        val session = ortSession ?: return
        val env = OrtEnvironment.getEnvironment()
        val pcmBuffer = ShortArray(WINDOW_SIZE)
        val floatBuffer = FloatArray(WINDOW_SIZE)
        val state = FloatArray(STATE_LAYERS * STATE_DIM)
        val inputShape = longArrayOf(1L, WINDOW_SIZE.toLong())
        val stateShape = longArrayOf(STATE_LAYERS.toLong(), 1L, STATE_DIM.toLong())
        var speechMillis = 0L
        var belowThresholdMillis = 0L
        var lastSpeechState = false
        var frameCounter = 0L
        var samplesRead = 0
        var inputGain = 1f
        var smoothedProbability = 0f

        var srTensor: OnnxTensor? = null
        try {
            val sampleRateTensor = OnnxTensor.createTensor(
                env,
                LongBuffer.wrap(longArrayOf(SAMPLE_RATE.toLong())),
                longArrayOf(),
            )
            srTensor = sampleRateTensor
            while (isRunning) {
                val record = audioRecord ?: break

                val count = record.read(
                    pcmBuffer,
                    samplesRead,
                    WINDOW_SIZE - samplesRead,
                    AudioRecord.READ_BLOCKING,
                )

                if (count > 0) {
                    samplesRead += count
                } else if (count < 0) {
                    if (isRunning) {
                        throw IllegalStateException("Microphone read failed (error $count)")
                    }
                } else {
                    Thread.sleep(5)
                }

                if (!isRunning) break

                // Wait until we have accumulated a complete model frame.
                if (samplesRead < WINDOW_SIZE) {
                    continue
                }

                // Reset accumulator for the next frame
                samplesRead = 0

                var sumSquares = 0.0
                for (i in 0 until WINDOW_SIZE) {
                    val sample = pcmBuffer[i].toFloat() / 32768.0f
                    floatBuffer[i] = sample
                    sumSquares += sample * sample
                }
                val rms = kotlin.math.sqrt(sumSquares / WINDOW_SIZE).toFloat()
                onAudioLevelUpdated?.invoke(rms)

                // Adapt quiet microphone input to the model's useful range without gating frames.
                val desiredGain = (TARGET_MODEL_INPUT_RMS / rms.coerceAtLeast(1e-8f))
                    .coerceIn(1f, MAX_INPUT_GAIN)
                val smoothing = if (desiredGain > inputGain) GAIN_ATTACK else GAIN_RELEASE
                inputGain += smoothing * (desiredGain - inputGain)
                for (i in floatBuffer.indices) {
                    floatBuffer[i] = (floatBuffer[i] * inputGain).coerceIn(-1f, 1f)
                }

                val rawProb = runInference(
                    session = session,
                    env = env,
                    input = floatBuffer,
                    inputShape = inputShape,
                    state = state,
                    stateShape = stateShape,
                    srTensor = sampleRateTensor,
                )

                val probability = if (rawProb.isNaN() || rawProb.isInfinite()) 0f else rawProb.coerceIn(0f, 1f)

                smoothedProbability = maxOf(probability, smoothedProbability * 0.85f)
                onProbabilityUpdated?.invoke(smoothedProbability)

                frameCounter++
                if (frameCounter % 25L == 0L) {
                    Log.d(TAG, "Audio frame $frameCounter: micRms=$rms gain=$inputGain probability=$probability")
                }

                val frameMillis = WINDOW_SIZE * 1_000L / SAMPLE_RATE

                val speechCandidate = probability >= SPEECH_THRESHOLD

                if (speechCandidate) {
                    speechMillis += frameMillis
                    belowThresholdMillis = 0L
                } else {
                    speechMillis = 0L
                    if (lastSpeechState) {
                        belowThresholdMillis += frameMillis
                    } else {
                        belowThresholdMillis = 0L
                    }
                }

                val speechDetected = when {
                    !lastSpeechState && speechMillis >= SPEECH_CONFIRM_MILLIS -> true
                    lastSpeechState && belowThresholdMillis >= SPEECH_RELEASE_MILLIS -> false
                    else -> lastSpeechState
                }

                if (speechDetected != lastSpeechState) {
                    Log.d(TAG, "Speech state changed: $speechDetected (probability=$probability)")
                    lastSpeechState = speechDetected
                    speechMillis = 0L
                    belowThresholdMillis = 0L
                    onSpeechStateChanged(speechDetected)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Silero inference loop failed", e)
            if (lastSpeechState) {
                lastSpeechState = false
                onSpeechStateChanged(false)
            }
            onError(e.message ?: "Speech detection stopped unexpectedly")
        } finally {
            srTensor?.close()
            if (lastSpeechState) onSpeechStateChanged(false)
        }
    }

    private fun runInference(
        session: OrtSession,
        env: OrtEnvironment,
        input: FloatArray,
        inputShape: LongArray,
        state: FloatArray,
        stateShape: LongArray,
        srTensor: OnnxTensor,
    ): Float {
        val inputTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(input), inputShape)
        val stateTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(state), stateShape)
        try {
            val inputs = mapOf(
                "input" to inputTensor,
                "state" to stateTensor,
                "sr" to srTensor,
            )
            session.run(inputs).use { results ->
                val outputTensor = results["output"].get() as? OnnxTensor
                    ?: error("Silero output is not an ONNX tensor")
                val output = outputTensor.value as? Array<*>
                    ?: error("Unexpected Silero output shape")
                val firstRow = output.firstOrNull() as? FloatArray
                    ?: error("Silero output did not contain a probability")
                val probability = firstRow.firstOrNull()
                    ?: error("Silero output probability is empty")

                val nextStateTensor = results["stateN"].get() as? OnnxTensor
                    ?: error("Silero recurrent state is not an ONNX tensor")
                val stateBuffer = nextStateTensor.floatBuffer
                stateBuffer.rewind()
                check(stateBuffer.remaining() == state.size) {
                    "Unexpected Silero recurrent state size: ${stateBuffer.remaining()}"
                }
                stateBuffer.get(state)
                return probability
            }
        } finally {
            inputTensor.close()
            stateTensor.close()
        }
    }
}
