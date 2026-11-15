package com.yougaindra.backontrack.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yougaindra.backontrack.core.AlarmAndNotificationCoordinator
import com.yougaindra.backontrack.core.LocalStore
import com.yougaindra.backontrack.model.CheckInRecord
import com.yougaindra.backontrack.model.CheckInResponseType
import com.yougaindra.backontrack.model.SessionStatus
import java.util.UUID

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AlarmAndNotificationCoordinator.ACTION_QUICK_RESPONSE) return
        val rawType = intent.getStringExtra(AlarmAndNotificationCoordinator.EXTRA_RESPONSE_TYPE) ?: return
        val responseType = try {
            CheckInResponseType.valueOf(rawType)
        } catch (e: Exception) {
            return
        }

        val store = LocalStore(context)
        val coordinator = AlarmAndNotificationCoordinator(context)
        val intention = store.loadIntention()
        val now = System.currentTimeMillis()

        store.appendHistory(
            CheckInRecord(
                id = UUID.randomUUID().toString(),
                timestampMillis = now,
                response = responseType,
                intentionSnapshot = intention.primary
            )
        )

        if (responseType == CheckInResponseType.REMIND_LATER) {
            val snoozeTarget = now + 15 * 60_000L
            val nextId = "sched_$snoozeTarget"
            store.saveSchedule(
                store.loadSchedule().copy(
                    status = SessionStatus.RUNNING,
                    nextFireAtMillis = snoozeTarget,
                    scheduleId = nextId
                )
            )
            coordinator.scheduleNextAlarm(snoozeTarget, nextId)
        }

        coordinator.dismissNotification()
    }
}
