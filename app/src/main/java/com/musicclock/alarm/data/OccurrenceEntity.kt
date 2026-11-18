package com.musicclock.alarm.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class OccurrenceKind {
    SCHEDULED,
    SNOOZE,
}

enum class OccurrenceState {
    SCHEDULED,
    QUEUED,
    RINGING,
    SNOOZED,
    DISMISSED,
    MISSED,
}

@Entity(
    tableName = "occurrences",
    indices = [
        Index("alarmId"),
        Index(value = ["state", "triggerAtMillis"]),
    ],
)
data class OccurrenceEntity(
    @PrimaryKey
    val id: String,
    val alarmId: Long,
    val triggerAtMillis: Long,
    val kind: OccurrenceKind,
    val state: OccurrenceState,
    val parentOccurrenceId: String? = null,
    val createdAtMillis: Long,
)

