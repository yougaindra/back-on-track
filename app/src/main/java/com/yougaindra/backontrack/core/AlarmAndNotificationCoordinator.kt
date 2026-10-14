package com.yougaindra.backontrack.core

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yougaindra.backontrack.MainActivity
import com.yougaindra.backontrack.model.CheckInResponseType
import com.yougaindra.backontrack.model.DailyIntention
import com.yougaindra.backontrack.model.UserSettings
import com.yougaindra.backontrack.receiver.CheckInAlarmReceiver
import com.yougaindra.backontrack.receiver.NotificationActionReceiver

class AlarmAndNotificationCoordinator(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "back_on_track_checkins"
        const val NOTIFICATION_ID = 2001
        const val ALARM_REQUEST_CODE = 1001

        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_OPEN_CHECKIN_MODAL = "extra_open_checkin_modal"
        const val EXTRA_OPEN_RESET_STEP = "extra_open_reset_step"
        const val ACTION_QUICK_RESPONSE = "com.yougaindra.backontrack.ACTION_QUICK_RESPONSE"
        const val EXTRA_RESPONSE_TYPE = "extra_response_type"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        ensureNotificationChannel()
    }

    fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gentle Attention Check-ins",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Supportive, non-judgmental check-in reminders during active hours."
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun scheduleNextAlarm(triggerAtMillis: Long, scheduleId: String) {
        val intent = Intent(context, CheckInAlarmReceiver::class.java).apply {
            putExtra(EXTRA_SCHEDULE_ID, scheduleId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        try {
            if (canUseExact) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (se: SecurityException) {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancelScheduledAlarm() {
        val intent = Intent(context, CheckInAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showCheckInNotification(settings: UserSettings, intention: DailyIntention) {
        if (!hasNotificationPermission()) return

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CHECKIN_MODAL, true)
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            3000,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val onTrackPending = buildQuickActionIntent(CheckInResponseType.ON_TRACK, 3001)
        val resetIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_CHECKIN_MODAL, true)
            putExtra(EXTRA_OPEN_RESET_STEP, true)
        }
        val resetPending = PendingIntent.getActivity(
            context,
            3002,
            resetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozePending = buildQuickActionIntent(CheckInResponseType.REMIND_LATER, 3003)

        val bodyText = if (settings.showIntentionInPrompt && intention.primary.isNotBlank()) {
            "Are you doing what you intended? (\"${intention.primary}\")"
        } else {
            "Are you doing what you intended to do?"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle("Time for a quick check-in")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "On Track", onTrackPending)
            .addAction(0, "Got Distracted", resetPending)
            .addAction(0, "Snooze 15m", snoozePending)

        if (!settings.soundEnabled) {
            builder.setSilent(true)
        }

        notificationManager.notify(NOTIFICATION_ID, builder.build())
    }

    fun dismissNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun buildQuickActionIntent(
        responseType: CheckInResponseType,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_QUICK_RESPONSE
            putExtra(EXTRA_RESPONSE_TYPE, responseType.name)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
