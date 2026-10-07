package com.example.shutupbeep

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.StateFlow

enum class AppStatus { IDLE, STARTING, LISTENING, SPEECH_DETECTED, PERMISSION_DENIED, ERROR }

class VadViewModel(application: Application) : AndroidViewModel(application) {
    val status: StateFlow<AppStatus> = VadServiceState.status
    val toneEnabled: StateFlow<Boolean> = VadServiceState.toneEnabled
    val speechProbability: StateFlow<Float> = VadServiceState.speechProbability
    val microphoneLevel: StateFlow<Float> = VadServiceState.microphoneLevel
    val errorMessage: StateFlow<String> = VadServiceState.errorMessage

    fun onPermissionGranted() {
        val intent = Intent(getApplication(), VadForegroundService::class.java)
            .setAction(VadForegroundService.ACTION_START)
            .putExtra(VadForegroundService.EXTRA_TONE_ENABLED, toneEnabled.value)
        ContextCompat.startForegroundService(getApplication(), intent)
    }

    fun onPermissionDenied() {
        VadServiceState.setStatus(AppStatus.PERMISSION_DENIED)
    }

    fun toggleTone() {
        val enabled = !toneEnabled.value
        VadServiceState.setToneEnabled(enabled)
        val intent = Intent(getApplication(), VadForegroundService::class.java)
            .setAction(VadForegroundService.ACTION_TOGGLE_TONE)
            .putExtra(VadForegroundService.EXTRA_TONE_ENABLED, enabled)
        getApplication<Application>().startService(intent)
    }

    fun stopListening() {
        val intent = Intent(getApplication(), VadForegroundService::class.java)
            .setAction(VadForegroundService.ACTION_STOP)
        getApplication<Application>().startService(intent)
    }
}
