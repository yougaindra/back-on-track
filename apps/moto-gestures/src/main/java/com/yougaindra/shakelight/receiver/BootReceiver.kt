package com.yougaindra.shakelight.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.yougaindra.shakelight.data.PreferencesManager
import com.yougaindra.shakelight.service.ShakeService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = PreferencesManager(context)
            val settings = prefs.loadSettings()
            if (settings.startOnBoot && settings.isServiceEnabled) {
                ShakeService.start(context)
            }
        }
    }
}
