package com.musicclock.alarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.musicclock.alarm.ring.AlarmRingService

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER_OCCURRENCE) return
        val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID) ?: return
        ContextCompat.startForegroundService(
            context,
            AlarmRingService.triggerIntent(context, occurrenceId),
        )
    }

    companion object {
        const val ACTION_TRIGGER_OCCURRENCE = "com.musicclock.alarm.TRIGGER_OCCURRENCE"
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"
    }
}

