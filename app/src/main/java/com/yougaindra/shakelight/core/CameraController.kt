package com.yougaindra.shakelight.core

import android.app.ActivityOptions
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.yougaindra.shakelight.R

object CameraController {

    private const val CHANNEL_ID = "quick_capture_camera_channel"
    private const val NOTIFICATION_ID = 6001

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    private fun wakeScreen(context: Context) {
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!powerManager.isInteractive) {
                @Suppress("DEPRECATION")
                val wakeLock = powerManager.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "MotoGestures::CameraWakeScreen"
                )
                wakeLock.acquire(2000L)
            }
        } catch (_: Exception) {
        }
    }

    fun launchCamera(context: Context) {
        val appContext = context.applicationContext
        val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked

        // 1. Wake screen if display was off
        wakeScreen(appContext)

        // 2. Select camera action: secure camera over lockscreen, standard if unlocked
        val action = if (isLocked) {
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
        } else {
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        }

        val cameraIntent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }

        // 3. For Android 14+ (UPSIDE_DOWN_CAKE), prepare Background Activity Start allowance
        val bkgOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                    )
                }.toBundle()
            } catch (_: Exception) {
                null
            }
        } else null

        // 4. Attempt direct activity launch
        try {
            if (bkgOptions != null) {
                appContext.startActivity(cameraIntent, bkgOptions)
            } else {
                appContext.startActivity(cameraIntent)
            }
        } catch (_: Exception) {
        }

        // 5. Attempt PendingIntent launch with background activity privileges
        try {
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                NOTIFICATION_ID,
                cameraIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            if (bkgOptions != null) {
                pendingIntent.send(appContext, 0, null, null, null, null, bkgOptions)
            } else {
                pendingIntent.send()
            }
        } catch (_: Exception) {
        }

        // 6. Only if "Appear on top" permission is missing, send notification fallback so user can tap
        if (!canDrawOverlays(appContext)) {
            try {
                sendLaunchNotification(appContext, cameraIntent)
            } catch (_: Exception) {
            }
        }
    }

    private fun sendLaunchNotification(context: Context, cameraIntent: Intent) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            cameraIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Quick Capture Camera",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Tap to open camera when gesture is detected"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_camera)
            .setContentTitle("Quick Capture")
            .setContentText("Tap to open Camera")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(3500L)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
