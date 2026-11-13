package com.yougaindra.shakelight.core

import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import com.yougaindra.shakelight.R

object CameraController {

    private const val CHANNEL_ID = "quick_capture_camera_channel"
    private const val NOTIFICATION_ID = 6001

    fun launchCamera(context: Context) {
        val appContext = context.applicationContext
        val keyguardManager = appContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        val isLocked = keyguardManager.isKeyguardLocked

        val action = if (isLocked) {
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE
        } else {
            MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA
        }

        val cameraIntent = Intent(action).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        // 1. Attempt direct activity launch
        var launched = false
        try {
            appContext.startActivity(cameraIntent)
            launched = true
        } catch (_: Exception) {
        }

        // 2. Also trigger via high-priority full-screen intent so it works over lockscreen
        // and bypasses Android 10+ background activity start restrictions
        try {
            launchViaFullScreenIntent(appContext, cameraIntent)
        } catch (_: Exception) {
        }
    }

    private fun launchViaFullScreenIntent(context: Context, cameraIntent: Intent) {
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
                description = "Opens the camera when double-twist gesture is detected"
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_camera)
            .setContentTitle("Quick Capture")
            .setContentText("Opening Camera...")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setAutoCancel(true)
            .setTimeoutAfter(3500L)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
