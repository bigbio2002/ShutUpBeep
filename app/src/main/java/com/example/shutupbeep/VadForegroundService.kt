package com.example.shutupbeep

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.util.Log

class VadForegroundService : Service() {
    companion object {
        private const val TAG = "VadForegroundService"
        private const val CHANNEL_ID = "speech_detection"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.example.shutupbeep.action.START"
        const val ACTION_STOP = "com.example.shutupbeep.action.STOP"
        const val ACTION_TOGGLE_TONE = "com.example.shutupbeep.action.TOGGLE_TONE"
        const val EXTRA_TONE_ENABLED = "tone_enabled"
    }

    private var vadEngine: VadEngine? = null
    private var soundPlayer: SoundPlayer? = null
    private var notificationManager: NotificationManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NotificationManager::class.java)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startDetection(intent.getBooleanExtra(EXTRA_TONE_ENABLED, true))
            ACTION_TOGGLE_TONE -> setToneEnabled(intent.getBooleanExtra(EXTRA_TONE_ENABLED, false))
            ACTION_STOP -> stopDetection()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startDetection(toneEnabled: Boolean) {
        if (vadEngine != null) return

        try {
            promoteToForeground()
            VadServiceState.setErrorMessage("")
            VadServiceState.setToneEnabled(toneEnabled)
            VadServiceState.setStatus(AppStatus.STARTING)

            val player = SoundPlayer()
            soundPlayer = player
            player.prepare()
            player.setToneActive(false)

            val engine = VadEngine(
                context = applicationContext,
                onListeningStarted = {
                    VadServiceState.setStatus(AppStatus.LISTENING)
                    updateNotification("Listening for speech")
                },
                onSpeechStateChanged = { isSpeech ->
                    player.setToneActive(isSpeech && VadServiceState.toneEnabled.value)
                    VadServiceState.setStatus(
                        if (isSpeech) AppStatus.SPEECH_DETECTED else AppStatus.LISTENING,
                    )
                    updateNotification(if (isSpeech) "Speech detected" else "Listening for speech")
                },
                onError = { message ->
                    Log.e(TAG, message)
                    player.setToneActive(false)
                    VadServiceState.setErrorMessage(message)
                    VadServiceState.setStatus(AppStatus.ERROR)
                    updateNotification("Speech detection error")
                    mainHandler.postDelayed({ stopSelf() }, 100L)
                },
                onProbabilityUpdated = VadServiceState::setSpeechProbability,
                onAudioLevelUpdated = VadServiceState::setMicrophoneLevel,
            )
            vadEngine = engine
            engine.start()
        } catch (e: Exception) {
            Log.e(TAG, "Unable to start foreground speech detection", e)
            VadServiceState.setErrorMessage(e.message ?: "Unable to start speech detection")
            VadServiceState.setStatus(AppStatus.ERROR)
            vadEngine?.stop()
            vadEngine = null
            soundPlayer?.setToneActive(false)
            soundPlayer?.release()
            soundPlayer = null
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun setToneEnabled(enabled: Boolean) {
        VadServiceState.setToneEnabled(enabled)
        soundPlayer?.setToneActive(
            enabled && VadServiceState.status.value == AppStatus.SPEECH_DETECTED,
        )
    }

    private fun stopDetection() {
        val engine = vadEngine
        vadEngine = null
        engine?.stop()

        soundPlayer?.setToneActive(false)
        soundPlayer?.release()
        soundPlayer = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        VadServiceState.reset()
        stopSelf()
    }

    private fun promoteToForeground() {
        val notification = buildNotification("Starting speech detection")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Speech detection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps speech detection running while ShutUpBeep is in the background"
                setShowBadge(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopService = PendingIntent.getService(
            this,
            1,
            Intent(this, VadForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(message)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(this, R.mipmap.ic_launcher),
                    "Stop",
                    stopService,
                ).build(),
            )
            .build()
    }

    private fun updateNotification(message: String) {
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        vadEngine?.stop()
        vadEngine = null
        soundPlayer?.setToneActive(false)
        soundPlayer?.release()
        soundPlayer = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (VadServiceState.status.value != AppStatus.ERROR) {
            VadServiceState.reset()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
