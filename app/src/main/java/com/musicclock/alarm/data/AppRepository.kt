package com.musicclock.alarm.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AppRepository(
    private val dao: AlarmDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val claimMutex = Mutex()

    val alarms: Flow<List<AlarmEntity>> = dao.observeAlarms()
    val ringtones: Flow<List<RingtoneEntity>> = dao.observeRingtones()
    val pendingOccurrences: Flow<List<OccurrenceEntity>> = dao.observePendingOccurrences()

    suspend fun getAllAlarms(): List<AlarmEntity> = dao.getAllAlarms()

    suspend fun getAlarm(id: Long): AlarmEntity? = dao.getAlarm(id)

    suspend fun saveAlarm(alarm: AlarmEntity): Long = dao.upsertAlarm(alarm)

    suspend fun deleteAlarm(id: Long) {
        dao.deletePendingOccurrences(id)
        dao.deleteAlarm(id)
    }

    suspend fun getEnabledAlarms(): List<AlarmEntity> = dao.getEnabledAlarms()

    suspend fun getRingtones(): List<RingtoneEntity> = dao.getRingtones()

    suspend fun getRingtone(id: Long): RingtoneEntity? = dao.getRingtone(id)

    suspend fun saveRingtone(ringtone: RingtoneEntity): Long = dao.upsertRingtone(ringtone)

    suspend fun ringtoneUsageCount(id: Long): Int = dao.countAlarmsUsingRingtone(id)

    suspend fun deleteRingtone(id: Long) = dao.deleteRingtone(id)

    suspend fun scheduleOccurrence(
        alarmId: Long,
        triggerAtMillis: Long,
        kind: OccurrenceKind = OccurrenceKind.SCHEDULED,
        parentOccurrenceId: String? = null,
    ): OccurrenceEntity {
        val occurrence = OccurrenceEntity(
            id = UUID.randomUUID().toString(),
            alarmId = alarmId,
            triggerAtMillis = triggerAtMillis,
            kind = kind,
            state = OccurrenceState.SCHEDULED,
            parentOccurrenceId = parentOccurrenceId,
            createdAtMillis = clock(),
        )
        dao.upsertOccurrence(occurrence)
        return occurrence
    }

    suspend fun getOccurrence(id: String): OccurrenceEntity? = dao.getOccurrence(id)

    suspend fun getPendingOccurrences(): List<OccurrenceEntity> =
        dao.getPendingOccurrences(clock())

    suspend fun getScheduledOccurrencesForAlarm(alarmId: Long): List<OccurrenceEntity> =
        dao.getScheduledOccurrencesForAlarm(alarmId)

    suspend fun getScheduledSnoozesForAlarm(alarmId: Long): List<OccurrenceEntity> =
        dao.getScheduledSnoozesForAlarm(alarmId)

    suspend fun getAllScheduledOccurrencesForAlarm(alarmId: Long): List<OccurrenceEntity> =
        dao.getAllScheduledOccurrencesForAlarm(alarmId)

    suspend fun deleteScheduledOccurrences(alarmId: Long) =
        dao.deleteScheduledOccurrences(alarmId)

    suspend fun getRingingOccurrence(): OccurrenceEntity? = dao.getRingingOccurrence()

    suspend fun getNextQueuedOccurrence(): OccurrenceEntity? = dao.getNextQueuedOccurrence()

    suspend fun getNextPendingOccurrence(): OccurrenceEntity? = dao.getNextPendingOccurrence(clock())

    suspend fun setOccurrenceState(id: String, state: OccurrenceState) =
        dao.updateOccurrenceState(id, state)

    suspend fun recoverRingingAsQueued() =
        dao.updateAllOccurrencesState(OccurrenceState.RINGING, OccurrenceState.QUEUED)

    suspend fun claimOccurrence(id: String): OccurrenceEntity? = claimMutex.withLock {
        val occurrence = dao.getOccurrence(id) ?: return@withLock null
        if (occurrence.state == OccurrenceState.DISMISSED ||
            occurrence.state == OccurrenceState.SNOOZED ||
            occurrence.state == OccurrenceState.MISSED ||
            occurrence.state == OccurrenceState.RINGING
        ) {
            return@withLock null
        }
        val ringing = dao.getRingingOccurrence()
        val nextState = if (ringing == null) OccurrenceState.RINGING else OccurrenceState.QUEUED
        dao.updateOccurrenceState(occurrence.id, nextState)
        occurrence.copy(state = nextState)
    }

    suspend fun claimNextQueued(): OccurrenceEntity? = claimMutex.withLock {
        if (dao.getRingingOccurrence() != null) return@withLock null
        val occurrence = dao.getNextQueuedOccurrence() ?: return@withLock null
        dao.updateOccurrenceState(occurrence.id, OccurrenceState.RINGING)
        occurrence.copy(state = OccurrenceState.RINGING)
    }
}

