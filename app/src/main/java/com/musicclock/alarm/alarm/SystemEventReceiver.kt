package com.musicclock.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.musicclock.alarm.MusicClockApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            -> {
                val application = context.applicationContext as MusicClockApplication
                application.applicationScope.launch(Dispatchers.IO) {
                    if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
                        intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
                    ) {
                        application.container.repository.missAllRingingOccurrences()
                    }
                    application.container.alarmScheduler.rescheduleAll()
                }
            }
        }
    }

    companion object {
        private const val ACTION_EXACT_ALARM_PERMISSION_STATE_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}

