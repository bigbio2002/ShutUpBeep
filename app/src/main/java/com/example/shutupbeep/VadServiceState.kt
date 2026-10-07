package com.example.shutupbeep

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

object VadServiceState {
    private val _status = MutableStateFlow(AppStatus.IDLE)
    val status = _status.asStateFlow()

    private val _toneEnabled = MutableStateFlow(true)
    val toneEnabled = _toneEnabled.asStateFlow()

    private val _speechProbability = MutableStateFlow(0f)
    val speechProbability = _speechProbability.asStateFlow()

    private val _microphoneLevel = MutableStateFlow(0f)
    val microphoneLevel = _microphoneLevel.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage = _errorMessage.asStateFlow()

    internal fun setStatus(value: AppStatus) {
        _status.value = value
    }

    internal fun setToneEnabled(value: Boolean) {
        _toneEnabled.value = value
    }

    internal fun setSpeechProbability(value: Float) {
        _speechProbability.value = value
    }

    internal fun setMicrophoneLevel(value: Float) {
        _microphoneLevel.value = value
    }

    internal fun setErrorMessage(value: String) {
        _errorMessage.value = value
    }

    internal fun reset() {
        _status.value = AppStatus.IDLE
        _speechProbability.value = 0f
        _microphoneLevel.value = 0f
        _errorMessage.value = ""
    }
}
