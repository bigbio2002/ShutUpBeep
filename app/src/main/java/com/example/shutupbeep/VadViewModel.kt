package com.example.shutupbeep

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppStatus { IDLE, LISTENING, SPEECH_DETECTED, PERMISSION_DENIED }

class VadViewModel(application: Application) : AndroidViewModel(application) {

    private val _status = MutableStateFlow(AppStatus.IDLE)
    val status: StateFlow<AppStatus> = _status.asStateFlow()

    private val _threshold = MutableStateFlow(0.5f)
    val threshold: StateFlow<Float> = _threshold.asStateFlow()

    private var vadEngine: VadEngine? = null
    private val soundPlayer = SoundPlayer()

    fun setThreshold(value: Float) {
        val clamped = value.coerceIn(0.1f, 0.95f)
        _threshold.value = clamped
        vadEngine?.speechThreshold = clamped
    }

    fun onPermissionGranted() {
        if (vadEngine != null) return
        _status.value = AppStatus.LISTENING

        soundPlayer.prepare()

        vadEngine = VadEngine(
            context = getApplication(),
            speechThreshold = _threshold.value,
            onSpeechStateChanged = { isSpeech ->
                // Instant tone playback on the audio thread
                soundPlayer.setToneActive(isSpeech)
                // Update UI state
                _status.value = if (isSpeech) AppStatus.SPEECH_DETECTED else AppStatus.LISTENING
            }
        )
        vadEngine!!.start()
    }

    fun onPermissionDenied() {
        soundPlayer.setToneActive(false)
        _status.value = AppStatus.PERMISSION_DENIED
    }

    fun stopListening() {
        vadEngine?.stop()
        vadEngine = null
        soundPlayer.release()
        _status.value = AppStatus.IDLE
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
