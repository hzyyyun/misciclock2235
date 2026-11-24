package com.musicclock.alarm

import com.google.common.truth.Truth.assertThat
import com.musicclock.alarm.alarm.OccurrenceCalculator
import com.musicclock.alarm.data.AlarmEntity
import com.musicclock.alarm.data.ScheduleType
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class OccurrenceCalculatorTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun weeklyAlarmFindsNextSelectedDay() {
        val now = LocalDateTime.of(2026, 11, 18, 6, 30).atZone(shanghai)
        val alarm = weeklyAlarm(hour = 7, minute = 0, weekdaysMask = 1)

        val next = OccurrenceCalculator.nextTrigger(
            alarm,
            now.toInstant().toEpochMilli(),
            shanghai,
        )

        val expected = LocalDateTime.of(2026, 11, 23, 7, 0).atZone(shanghai)
        assertThat(next).isEqualTo(expected.toInstant().toEpochMilli())
    }

    @Test
    fun weeklyAlarmUsesTodayWhenTimeIsStillAhead() {
        val now = LocalDateTime.of(2026, 11, 18, 6, 30).atZone(shanghai)
        val wednesdayBit = 1 shl 2
        val alarm = weeklyAlarm(hour = 7, minute = 0, weekdaysMask = wednesdayBit)

        val next = OccurrenceCalculator.nextTrigger(
            alarm,
            now.toInstant().toEpochMilli(),
            shanghai,
        )

        val expected = LocalDateTime.of(2026, 11, 18, 7, 0).atZone(shanghai)
        assertThat(next).isEqualTo(expected.toInstant().toEpochMilli())
    }

    @Test
    fun disabledAndPastOneShotDoNotTrigger() {
        val now = LocalDateTime.of(2026, 11, 18, 8, 0).atZone(shanghai)
        val alarm = AlarmEntity(
            id = 1,
            label = "起床",
            hour = 7,
            minute = 0,
            scheduleType = ScheduleType.ONE_SHOT,
            oneShotAtMillis = now.minusMinutes(2).toInstant().toEpochMilli(),
        )

        assertThat(OccurrenceCalculator.nextTrigger(alarm, now.toInstant().toEpochMilli(), shanghai))
            .isNull()
        assertThat(
            OccurrenceCalculator.nextTrigger(
                alarm.copy(enabled = false, oneShotAtMillis = now.plusMinutes(2).toInstant().toEpochMilli()),
                now.toInstant().toEpochMilli(),
                shanghai,
            ),
        ).isNull()
    }

    @Test
    fun nonexistentDaylightSavingTimeMovesToFirstValidMinute() {
        val newYork = ZoneId.of("America/New_York")
        val now = LocalDateTime.of(2026, 3, 7, 8, 0).atZone(newYork)
        val sundayBit = 1 shl 6
        val alarm = weeklyAlarm(hour = 2, minute = 30, weekdaysMask = sundayBit)

        val next = OccurrenceCalculator.nextTrigger(
            alarm,
            now.toInstant().toEpochMilli(),
            newYork,
        )

        val local = java.time.Instant.ofEpochMilli(requireNotNull(next)).atZone(newYork)
        assertThat(local.toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 3, 8))
        assertThat(local.toLocalTime()).isEqualTo(java.time.LocalTime.of(3, 0))
    }

    @Test
    fun oneShotAtUsesTomorrowWhenTodayHasPassed() {
        val now = LocalDateTime.of(2026, 11, 18, 8, 0).atZone(shanghai)

        val next = OccurrenceCalculator.oneShotAt(
            now.toInstant().toEpochMilli(),
            7,
            0,
            shanghai,
        )

        assertThat(next).isEqualTo(
            LocalDateTime.of(2026, 11, 19, 7, 0).atZone(shanghai).toInstant().toEpochMilli(),
        )
    }

    @Test
    fun oneShotAtKeepsTodayWhenAlarmIsLaterThatMorning() {
        val now = LocalDateTime.of(2026, 11, 18, 0, 25).atZone(shanghai)

        val next = OccurrenceCalculator.oneShotAt(
            now.toInstant().toEpochMilli(),
            6,
            30,
            shanghai,
        )

        assertThat(next).isEqualTo(
            LocalDateTime.of(2026, 11, 18, 6, 30).atZone(shanghai).toInstant().toEpochMilli(),
        )
    }

    private fun weeklyAlarm(hour: Int, minute: Int, weekdaysMask: Int) = AlarmEntity(
        id = 1,
        label = "起床",
        hour = hour,
        minute = minute,
        scheduleType = ScheduleType.WEEKLY,
        weekdaysMask = weekdaysMask,
    )
}

