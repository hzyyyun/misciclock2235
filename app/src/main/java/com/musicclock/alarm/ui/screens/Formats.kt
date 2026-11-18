package com.musicclock.alarm.ui.screens

import com.musicclock.alarm.data.AlarmEntity
import com.musicclock.alarm.data.ScheduleType

val WeekdayLabels = listOf("一", "二", "三", "四", "五", "六", "日")

fun formatClock(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

fun alarmRepeatSummary(alarm: AlarmEntity): String = when (alarm.scheduleType) {
    ScheduleType.ONE_SHOT -> "仅一次"
    ScheduleType.WEEKLY -> when (alarm.weekdaysMask) {
        0 -> "未选择重复日"
        0b0011111 -> "工作日"
        0b1111111 -> "每天"
        else -> WeekdayLabels.mapIndexedNotNull { index, label ->
            if (alarm.weekdaysMask and (1 shl index) != 0) "周$label" else null
        }.joinToString("、")
    }
}

fun countdownLabel(nowMillis: Long, triggerAtMillis: Long): String {
    val remainingMinutes = ((triggerAtMillis - nowMillis).coerceAtLeast(0L) + 59_999L) / 60_000L
    if (remainingMinutes <= 1L) return "不足 1 分钟"
    val days = remainingMinutes / (24 * 60)
    val hours = remainingMinutes / 60 % 24
    val minutes = remainingMinutes % 60
    return buildString {
        if (days > 0) append("${days}天")
        if (hours > 0) append("${hours}小时")
        if (days == 0L && minutes > 0) append("${minutes}分钟")
    }
}

