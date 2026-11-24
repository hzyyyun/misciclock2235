package com.musicclock.alarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ScheduleType {
    ONE_SHOT,
    WEEKLY,
}

enum class RingStopMode {
    DURATION,
    PLAY_COUNT,
}

@Entity(tableName = "alarms")
data class AlarmEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val label: String,
    val hour: Int,
    val minute: Int,
    val scheduleType: ScheduleType,
    val weekdaysMask: Int = 0,
    val oneShotAtMillis: Long? = null,
    val enabled: Boolean = true,
    val ringtoneId: Long? = null,
    val vibrationEnabled: Boolean = true,
    val snoozeEnabled: Boolean = true,
    val ringStopMode: RingStopMode = RingStopMode.DURATION,
    val ringDurationSeconds: Int = 300,
    val ringLoopCount: Int = 1,
)

