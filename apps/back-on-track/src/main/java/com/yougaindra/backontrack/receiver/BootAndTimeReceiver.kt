package com.yougaindra.backontrack.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yougaindra.backontrack.core.AlarmAndNotificationCoordinator
import com.yougaindra.backontrack.core.LocalStore
import com.yougaindra.backontrack.core.SchedulerEngine
import com.yougaindra.backontrack.model.SessionStatus

class BootAndTimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val store = LocalStore(context)
        val schedule = store.loadSchedule()
        if (schedule.status != SessionStatus.RUNNING) return

        val settings = store.loadSettings()
        val now = System.currentTimeMillis()
        val reconciled = SchedulerEngine.reconcileOnRestart(schedule, settings, now)

        store.saveSchedule(reconciled.state)
        reconciled.state.nextFireAtMillis?.let { target ->
            AlarmAndNotificationCoordinator(context).scheduleNextAlarm(
                target,
                reconciled.state.scheduleId ?: "sched_$target"
            )
        }
    }
}
