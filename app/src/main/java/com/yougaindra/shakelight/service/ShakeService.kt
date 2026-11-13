package com.yougaindra.shakelight.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.yougaindra.shakelight.MainActivity
import com.yougaindra.shakelight.R
import com.yougaindra.shakelight.core.CameraController
import com.yougaindra.shakelight.core.FlashlightController
import com.yougaindra.shakelight.core.ShakeDetector
import com.yougaindra.shakelight.data.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class ShakeService : Service() {

    companion object {
        const val CHANNEL_ID = "shake_flashlight_channel"
        const val NOTIFICATION_ID = 5001

        const val ACTION_START = "com.yougaindra.shakelight.ACTION_START"
        const val ACTION_STOP = "com.yougaindra.shakelight.ACTION_STOP"
        const val ACTION_TOGGLE_TORCH = "com.yougaindra.shakelight.ACTION_TOGGLE_TORCH"
        const val ACTION_OPEN_CAMERA = "com.yougaindra.shakelight.ACTION_OPEN_CAMERA"
        const val ACTION_UPDATE_SETTINGS = "com.yougaindra.shakelight.ACTION_UPDATE_SETTINGS"

        fun start(context: Context) {
            val intent = Intent(context, ShakeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ShakeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun updateSettings(context: Context) {
            val intent = Intent(context, ShakeService::class.java).apply {
                action = ACTION_UPDATE_SETTINGS
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var flashlightController: FlashlightController
    private lateinit var shakeDetector: ShakeDetector
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(applicationContext)
        flashlightController = FlashlightController(applicationContext)

        val settings = preferencesManager.loadSettings()
        flashlightController.setAutoOffMinutes(settings.autoTurnOffMinutes)

        shakeDetector = ShakeDetector(
            context = applicationContext,
            onShakeTriggered = {
                handleShakeTrigger()
            },
            onTwistTriggered = {
                handleTwistTrigger()
            }
        )

        applySettingsToDetector(settings)

        // Listen for torch state changes to update the foreground notification
        flashlightController.isTorchOn
            .onEach { isOn ->
                updateNotification(isOn)
            }
            .launchIn(serviceScope)

        acquireWakeLock()
        shakeDetector.startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                preferencesManager.updateSettings(
                    preferencesManager.loadSettings().copy(isServiceEnabled = false)
                )
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            ACTION_TOGGLE_TORCH -> {
                flashlightController.toggleTorch()
                val currentSettings = preferencesManager.loadSettings()
                if (currentSettings.vibrateOnToggle) {
                    performTorchHaptic(flashlightController.isTorchOn.value)
                }
            }

            ACTION_OPEN_CAMERA -> {
                CameraController.launchCamera(applicationContext)
            }

            ACTION_UPDATE_SETTINGS -> {
                val updated = preferencesManager.loadSettings()
                applySettingsToDetector(updated)
                flashlightController.setAutoOffMinutes(updated.autoTurnOffMinutes)
            }

            ACTION_START, null -> {
                // Default start
            }
        }

        createNotificationChannel()
        val notification = buildNotification(flashlightController.isTorchOn.value)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun handleShakeTrigger() {
        val newState = !flashlightController.isTorchOn.value
        flashlightController.setTorch(newState)

        val currentSettings = preferencesManager.loadSettings()
        if (currentSettings.vibrateOnToggle) {
            performTorchHaptic(newState)
        }
    }

    private fun handleTwistTrigger() {
        val currentSettings = preferencesManager.loadSettings()
        if (!currentSettings.isCameraTwistEnabled) return

        if (currentSettings.vibrateOnCamera) {
            performCameraHaptic()
        }

        CameraController.launchCamera(applicationContext)
    }

    private fun applySettingsToDetector(settings: com.yougaindra.shakelight.data.AppSettings) {
        shakeDetector.shakeStateMachine.sensitivity = settings.sensitivity
        shakeDetector.shakeStateMachine.gestureMode = settings.gestureMode
        shakeDetector.shakeStateMachine.pocketProtectionEnabled = settings.pocketProtection

        shakeDetector.twistStateMachine.sensitivity = settings.cameraSensitivity
        shakeDetector.twistStateMachine.pocketProtectionEnabled = settings.pocketProtection
    }

    private fun performTorchHaptic(isTorchOn: Boolean) {
        try {
            val vibrator = getVibrator() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (isTorchOn) {
                    vibrator.vibrate(VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    val timings = longArrayOf(0, 45, 60, 45)
                    val amplitudes = intArrayOf(0, 160, 0, 160)
                    if (vibrator.hasAmplitudeControl()) {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    } else {
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                if (isTorchOn) {
                    vibrator.vibrate(70)
                } else {
                    vibrator.vibrate(longArrayOf(0, 45, 60, 45), -1)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun performCameraHaptic() {
        try {
            val vibrator = getVibrator() ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Triple crisp buzz for camera
                val timings = longArrayOf(0, 50, 40, 50, 40, 70)
                val amplitudes = intArrayOf(0, 200, 0, 200, 0, 240)
                if (vibrator.hasAmplitudeControl()) {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                } else {
                    vibrator.vibrate(VibrationEffect.createWaveform(timings, -1))
                }
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 50, 40, 50, 40, 70), -1)
            }
        } catch (_: Exception) {
        }
    }

    private fun getVibrator(): Vibrator? {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        return if (vibrator.hasVibrator()) vibrator else null
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ShakeLight::SensorWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L) // 12 hours safeguard max
            }
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Moto Gestures Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps chop for torch and twist for camera gestures active"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(isTorchOn: Boolean): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleTorchIntent = Intent(this, ShakeService::class.java).apply {
            action = ACTION_TOGGLE_TORCH
        }
        val toggleTorchPendingIntent = PendingIntent.getService(
            this,
            1,
            toggleTorchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openCameraIntent = Intent(this, ShakeService::class.java).apply {
            action = ACTION_OPEN_CAMERA
        }
        val openCameraPendingIntent = PendingIntent.getService(
            this,
            2,
            openCameraIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (isTorchOn) "⚡ Flashlight is ON" else "Moto Gestures Active"
        val statusText = if (isTorchOn) "Double-chop to turn off" else "Chop for Torch · Twist for Camera"
        val toggleActionText = if (isTorchOn) "Turn Off" else "Torch ON"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_flashlight)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, toggleActionText, toggleTorchPendingIntent)
            .addAction(0, "Open Camera", openCameraPendingIntent)
            .build()
    }

    private fun updateNotification(isTorchOn: Boolean) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(isTorchOn))
    }

    override fun onDestroy() {
        super.onDestroy()
        shakeDetector.stopListening()
        flashlightController.cleanUp()
        releaseWakeLock()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
