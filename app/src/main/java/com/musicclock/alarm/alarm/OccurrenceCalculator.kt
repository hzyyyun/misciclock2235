package com.musicclock.alarm.alarm

import com.musicclock.alarm.data.AlarmEntity
import com.musicclock.alarm.data.ScheduleType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object OccurrenceCalculator {
    fun nextTrigger(
        alarm: AlarmEntity,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (!alarm.enabled) return null
        return when (alarm.scheduleType) {
            ScheduleType.ONE_SHOT -> alarm.oneShotAtMillis?.takeIf { it > nowMillis }
            ScheduleType.WEEKLY -> nextWeekly(alarm, nowMillis, zoneId)
        }
    }

    private fun nextWeekly(alarm: AlarmEntity, nowMillis: Long, zoneId: ZoneId): Long? {
        if (alarm.weekdaysMask == 0) return null
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        val alarmTime = LocalTime.of(alarm.hour, alarm.minute)

        for (offset in 0..8) {
            val candidateDate = now.toLocalDate().plusDays(offset.toLong())
            val weekdayBit = 1 shl (candidateDate.dayOfWeek.value - 1)
            if (alarm.weekdaysMask and weekdayBit == 0) continue

            val candidate = resolveLocalTime(candidateDate, alarmTime, zoneId)
            if (candidate.toInstant().toEpochMilli() > nowMillis) {
                return candidate.toInstant().toEpochMilli()
            }
        }
        return null
    }

    fun oneShotAt(
        nowMillis: Long,
        hour: Int,
        minute: Int,
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zoneId)
        var candidate = normalizeToday(now.toLocalDate(), hour, minute, zoneId)
        if (candidate.toInstant().toEpochMilli() <= nowMillis) {
            candidate = normalizeToday(now.toLocalDate().plusDays(1), hour, minute, zoneId)
        }
        return candidate.toInstant().toEpochMilli()
    }

    private fun normalizeToday(
        date: LocalDate,
        hour: Int,
        minute: Int,
        zoneId: ZoneId,
    ): ZonedDateTime {
        val target = LocalTime.of(hour, minute)
        val candidate = ZonedDateTime.of(date, target, zoneId)
        return if (candidate.toLocalTime() == target) {
            candidate
        } else {
            resolveLocalTime(date, target, zoneId)
        }
    }

    private fun resolveLocalTime(
        date: LocalDate,
        target: LocalTime,
        zoneId: ZoneId,
    ): ZonedDateTime {
        val direct = ZonedDateTime.of(date, target, zoneId)
        if (direct.toLocalTime() == target) return direct

        val targetMinute = target.hour * 60 + target.minute
        for (minuteOfDay in targetMinute until 24 * 60) {
            val requested = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
            val candidate = ZonedDateTime.of(date, requested, zoneId)
            if (candidate.toLocalDate() == date && candidate.toLocalTime() == requested) {
                return candidate
            }
        }
        return ZonedDateTime.of(date, LocalTime.MAX.withNano(0), zoneId)
    }
}

