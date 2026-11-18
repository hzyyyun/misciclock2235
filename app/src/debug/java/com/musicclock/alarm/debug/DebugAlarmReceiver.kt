package com.musicclock.alarm.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.musicclock.alarm.MusicClockApplication
import com.musicclock.alarm.data.AlarmEntity
import com.musicclock.alarm.data.ScheduleType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DebugAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MusicClockApplication
                when (intent.action) {
                    ACTION_SCHEDULE -> {
                        val delaySeconds = intent.getIntExtra(EXTRA_DELAY_SECONDS, 120)
                            .coerceIn(10, 600)
                        val triggerAt = System.currentTimeMillis() + delaySeconds * 1_000L
                        val alarm = AlarmEntity(
                            label = intent.getStringExtra(EXTRA_LABEL)
                                ?.take(40)
                                ?: "调试重启闹钟",
                            hour = 7,
                            minute = 0,
                            scheduleType = ScheduleType.ONE_SHOT,
                            oneShotAtMillis = triggerAt,
                            vibrationEnabled = false,
                            snoozeEnabled = true,
                        )
                        val id = app.container.repository.saveAlarm(alarm)
                        app.container.alarmScheduler.scheduleNext(alarm.copy(id = id))
                    }

                    ACTION_CLEAR -> {
                        app.container.repository.getAllAlarms().forEach { alarm ->
                            app.container.alarmScheduler.cancelAllPending(alarm.id)
                            app.container.repository.deleteAlarm(alarm.id)
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SCHEDULE = "com.musicclock.alarm.debug.SCHEDULE"
        const val ACTION_CLEAR = "com.musicclock.alarm.debug.CLEAR"
        const val EXTRA_DELAY_SECONDS = "delay_seconds"
        const val EXTRA_LABEL = "label"
    }
}
