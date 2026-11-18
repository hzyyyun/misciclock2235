package com.musicclock.alarm.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromScheduleType(value: ScheduleType): String = value.name

    @TypeConverter
    fun toScheduleType(value: String): ScheduleType = ScheduleType.valueOf(value)

    @TypeConverter
    fun fromOccurrenceKind(value: OccurrenceKind): String = value.name

    @TypeConverter
    fun toOccurrenceKind(value: String): OccurrenceKind = OccurrenceKind.valueOf(value)

    @TypeConverter
    fun fromOccurrenceState(value: OccurrenceState): String = value.name

    @TypeConverter
    fun toOccurrenceState(value: String): OccurrenceState = OccurrenceState.valueOf(value)
}

