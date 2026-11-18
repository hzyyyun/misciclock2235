package com.musicclock.alarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.musicclock.alarm.data.AlarmEntity
import com.musicclock.alarm.data.AppRepository
import com.musicclock.alarm.data.OccurrenceEntity
import com.musicclock.alarm.data.OccurrenceKind
import com.musicclock.alarm.data.OccurrenceState
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AlarmScheduler(
    private val context: Context,
    private val repository: AppRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val zoneId: () -> ZoneId = ZoneId::systemDefault,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val appContext = context.applicationContext
    private val schedulerMutex = Mutex()

    fun canScheduleExactAlarms(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()

    suspend fun rescheduleAll() = withContext(Dispatchers.IO) {
        schedulerMutex.withLock {
            rescheduleAllLocked()
        }
    }

    private suspend fun rescheduleAllLocked() {
        repository.getAllAlarms().forEach { alarm ->
            val regular = repository.getScheduledOccurrencesForAlarm(alarm.id)
            if (!alarm.enabled) {
                cancelScheduledLocked(alarm.id)
            } else if (regular.isEmpty()) {
                scheduleNextLocked(alarm)
            } else {
                regular.forEach(::scheduleExact)
            }
            repository.getScheduledSnoozesForAlarm(alarm.id).forEach(::scheduleExact)
        }
    }

    suspend fun scheduleNext(alarm: AlarmEntity): OccurrenceEntity? = withContext(Dispatchers.IO) {
        schedulerMutex.withLock {
            scheduleNextLocked(alarm)
        }
    }

    private suspend fun scheduleNextLocked(alarm: AlarmEntity): OccurrenceEntity? {
        repository.getScheduledOccurrencesForAlarm(alarm.id).forEach { existing ->
            alarmManager.cancel(triggerPendingIntent(existing))
            repository.setOccurrenceState(existing.id, OccurrenceState.DISMISSED)
        }
        repository.deleteScheduledOccurrences(alarm.id)
        if (!alarm.enabled) return null

        val triggerAt = OccurrenceCalculator.nextTrigger(alarm, clock(), zoneId())
            ?: return null
        val occurrence = repository.scheduleOccurrence(
            alarmId = alarm.id,
            triggerAtMillis = triggerAt,
        )
        scheduleExact(occurrence)
        return occurrence
    }

    suspend fun scheduleSnooze(
        alarmId: Long,
        triggerAtMillis: Long,
        parentOccurrenceId: String,
    ): OccurrenceEntity = withContext(Dispatchers.IO) {
        schedulerMutex.withLock {
            val occurrence = repository.scheduleOccurrence(
                alarmId = alarmId,
                triggerAtMillis = triggerAtMillis,
                kind = OccurrenceKind.SNOOZE,
                parentOccurrenceId = parentOccurrenceId,
            )
            scheduleExact(occurrence)
            occurrence
        }
    }

    suspend fun cancelScheduled(alarmId: Long) = withContext(Dispatchers.IO) {
        schedulerMutex.withLock {
            cancelScheduledLocked(alarmId)
        }
    }

    private suspend fun cancelScheduledLocked(alarmId: Long) {
        repository.getScheduledOccurrencesForAlarm(alarmId).forEach { occurrence ->
            alarmManager.cancel(triggerPendingIntent(occurrence))
            repository.setOccurrenceState(occurrence.id, OccurrenceState.DISMISSED)
        }
    }

    suspend fun cancelAllPending(alarmId: Long) = withContext(Dispatchers.IO) {
        schedulerMutex.withLock {
            repository.getAllScheduledOccurrencesForAlarm(alarmId).forEach { occurrence ->
                alarmManager.cancel(triggerPendingIntent(occurrence))
                repository.setOccurrenceState(occurrence.id, OccurrenceState.DISMISSED)
            }
        }
    }

    private fun scheduleExact(occurrence: OccurrenceEntity) {
        val pendingIntent = triggerPendingIntent(occurrence)
        if (canScheduleExactAlarms()) {
            val showIntent = PendingIntent.getActivity(
                appContext,
                occurrence.id.hashCode(),
                Intent(appContext, com.musicclock.alarm.ui.MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(occurrence.triggerAtMillis, showIntent),
                    pendingIntent,
                )
            }.onFailure {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    occurrence.triggerAtMillis,
                    pendingIntent,
                )
            }
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                occurrence.triggerAtMillis,
                pendingIntent,
            )
        }
    }

    private fun triggerPendingIntent(occurrence: OccurrenceEntity): PendingIntent {
        val intent = Intent(appContext, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_TRIGGER_OCCURRENCE
            putExtra(AlarmReceiver.EXTRA_OCCURRENCE_ID, occurrence.id)
        }
        return PendingIntent.getBroadcast(
            appContext,
            occurrence.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

