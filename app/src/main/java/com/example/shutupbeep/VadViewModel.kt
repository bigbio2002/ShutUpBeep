package com.example.shutupbeep

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppStatus { IDLE, STARTING, LISTENING, SPEECH_DETECTED, PERMISSION_DENIED, ERROR }

class VadViewModel(application: Application) : AndroidViewModel(application) {

    private val _status = MutableStateFlow(AppStatus.IDLE)
    val status: StateFlow<AppStatus> = _status.asStateFlow()

    private val _toneEnabled = MutableStateFlow(true) // Enabled by default
    val toneEnabled: StateFlow<Boolean> = _toneEnabled.asStateFlow()

    private val _speechProbability = MutableStateFlow(0f)
    val speechProbability: StateFlow<Float> = _speechProbability.asStateFlow()

    private val _microphoneLevel = MutableStateFlow(0f)
    val microphoneLevel: StateFlow<Float> = _microphoneLevel.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private var vadEngine: VadEngine? = null
    private val soundPlayer = SoundPlayer()

    fun toggleTone() {
        val enabled = !_toneEnabled.value
        _toneEnabled.value = enabled
        if (enabled) {
            soundPlayer.prepare()
            soundPlayer.setToneActive(_status.value == AppStatus.SPEECH_DETECTED)
        } else {
            soundPlayer.setToneActive(false)
        }
    }

    fun onPermissionGranted() {
        if (vadEngine != null && _status.value != AppStatus.ERROR) return
        if (_status.value == AppStatus.ERROR) stopListening()
        _status.value = AppStatus.STARTING
        _errorMessage.value = ""

        soundPlayer.prepare()
        soundPlayer.setToneActive(false)
        vadEngine = VadEngine(
            context = getApplication(),
            onListeningStarted = {
                if (_status.value == AppStatus.STARTING) {
                    _status.value = AppStatus.LISTENING
                }
            },
            onSpeechStateChanged = { isSpeech ->
                soundPlayer.setToneActive(isSpeech && _toneEnabled.value)
                _status.value = if (isSpeech) AppStatus.SPEECH_DETECTED else AppStatus.LISTENING
            },
            onError = { message ->
                soundPlayer.setToneActive(false)
                Log.e("VadViewModel", message)
                _errorMessage.value = message
                _status.value = AppStatus.ERROR
            },
            onProbabilityUpdated = { prob ->
                _speechProbability.value = prob
            },
            onAudioLevelUpdated = { level ->
                _microphoneLevel.value = level
            },
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
        soundPlayer.setToneActive(false)
        soundPlayer.release()
        _speechProbability.value = 0f
        _microphoneLevel.value = 0f
        _errorMessage.value = ""
        _status.value = AppStatus.IDLE
    }

    override fun onCleared() {
        super.onCleared()
        stopListening()
    }
}
