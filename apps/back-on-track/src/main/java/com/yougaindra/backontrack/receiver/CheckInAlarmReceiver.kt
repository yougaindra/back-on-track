package com.yougaindra.backontrack.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yougaindra.backontrack.core.AlarmAndNotificationCoordinator
import com.yougaindra.backontrack.core.CheckInCuePlayer
import com.yougaindra.backontrack.core.LocalStore
import com.yougaindra.backontrack.core.SchedulerEngine
import com.yougaindra.backontrack.model.CheckInRecord
import com.yougaindra.backontrack.model.CheckInResponseType
import com.yougaindra.backontrack.model.ReminderStyle
import com.yougaindra.backontrack.model.SessionStatus
import java.util.UUID

class CheckInAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = LocalStore(context)
        val schedule = store.loadSchedule()
        if (schedule.status != SessionStatus.RUNNING) return

        val settings = store.loadSettings()
        val intention = store.loadIntention()
        val coordinator = AlarmAndNotificationCoordinator(context)
        val now = System.currentTimeMillis()

        val nextFire = SchedulerEngine.computeNextFireTime(now, settings)
        val nextId = "sched_$nextFire"
        store.saveSchedule(
            schedule.copy(
                status = SessionStatus.RUNNING,
                nextFireAtMillis = nextFire,
                scheduleId = nextId
            )
        )
        coordinator.scheduleNextAlarm(nextFire, nextId)

        if (SchedulerEngine.isWithinActiveWindow(now, settings)) {
            if (settings.reminderStyle == ReminderStyle.SOUND_ONLY) {
                CheckInCuePlayer.playCheckInCue(context, settings, intention)
                store.appendHistory(
                    CheckInRecord(
                        id = UUID.randomUUID().toString(),
                        timestampMillis = now,
                        response = CheckInResponseType.SOUND_ONLY,
                        intentionSnapshot = intention.primary
                    )
                )
            } else {
                coordinator.showCheckInNotification(settings, intention)
            }
        }
    }
}
