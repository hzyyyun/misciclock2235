package com.musicclock.alarm.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    fun observeAlarms(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE enabled = 1 ORDER BY hour, minute, id")
    suspend fun getEnabledAlarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarms ORDER BY hour, minute, id")
    suspend fun getAllAlarms(): List<AlarmEntity>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getAlarm(id: Long): AlarmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlarm(alarm: AlarmEntity): Long

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteAlarm(id: Long)

    @Query("SELECT COUNT(*) FROM alarms WHERE ringtoneId = :ringtoneId")
    suspend fun countAlarmsUsingRingtone(ringtoneId: Long): Int

    @Query("SELECT * FROM occurrences WHERE triggerAtMillis > :now AND state IN ('SCHEDULED', 'QUEUED', 'RINGING') ORDER BY triggerAtMillis, createdAtMillis")
    suspend fun getPendingOccurrences(now: Long): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrences WHERE state IN ('SCHEDULED', 'QUEUED', 'RINGING') ORDER BY triggerAtMillis, createdAtMillis")
    fun observePendingOccurrences(): Flow<List<OccurrenceEntity>>

    @Query("SELECT * FROM occurrences WHERE alarmId = :alarmId AND state = 'SCHEDULED' AND kind = 'SCHEDULED'")
    suspend fun getScheduledOccurrencesForAlarm(alarmId: Long): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrences WHERE alarmId = :alarmId AND state = 'SCHEDULED' AND kind = 'SNOOZE'")
    suspend fun getScheduledSnoozesForAlarm(alarmId: Long): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrences WHERE alarmId = :alarmId AND state = 'SCHEDULED'")
    suspend fun getAllScheduledOccurrencesForAlarm(alarmId: Long): List<OccurrenceEntity>

    @Query("SELECT * FROM occurrences WHERE id = :id")
    suspend fun getOccurrence(id: String): OccurrenceEntity?

    @Query("SELECT * FROM occurrences WHERE state = 'RINGING' ORDER BY triggerAtMillis, createdAtMillis LIMIT 1")
    suspend fun getRingingOccurrence(): OccurrenceEntity?

    @Query("SELECT * FROM occurrences WHERE state = 'QUEUED' ORDER BY triggerAtMillis, createdAtMillis LIMIT 1")
    suspend fun getNextQueuedOccurrence(): OccurrenceEntity?

    @Query("SELECT * FROM occurrences WHERE state IN ('SCHEDULED', 'QUEUED', 'RINGING') AND triggerAtMillis >= :now ORDER BY CASE state WHEN 'RINGING' THEN 0 WHEN 'QUEUED' THEN 1 ELSE 2 END, triggerAtMillis LIMIT 1")
    suspend fun getNextPendingOccurrence(now: Long): OccurrenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOccurrence(occurrence: OccurrenceEntity)

    @Query("DELETE FROM occurrences WHERE alarmId = :alarmId AND state = 'SCHEDULED' AND kind = 'SCHEDULED'")
    suspend fun deleteScheduledOccurrences(alarmId: Long)

    @Query("DELETE FROM occurrences WHERE alarmId = :alarmId AND state IN ('SCHEDULED', 'QUEUED', 'RINGING')")
    suspend fun deletePendingOccurrences(alarmId: Long)

    @Query("UPDATE occurrences SET state = :state WHERE id = :id")
    suspend fun updateOccurrenceState(id: String, state: OccurrenceState)

    @Query("UPDATE occurrences SET state = :state WHERE state = :fromState")
    suspend fun updateAllOccurrencesState(fromState: OccurrenceState, state: OccurrenceState)

    @Query("SELECT * FROM ringtones ORDER BY importedAtMillis DESC")
    fun observeRingtones(): Flow<List<RingtoneEntity>>

    @Query("SELECT * FROM ringtones ORDER BY importedAtMillis DESC")
    suspend fun getRingtones(): List<RingtoneEntity>

    @Query("SELECT * FROM ringtones WHERE id = :id")
    suspend fun getRingtone(id: Long): RingtoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRingtone(ringtone: RingtoneEntity): Long

    @Query("DELETE FROM ringtones WHERE id = :id")
    suspend fun deleteRingtone(id: Long)
}

