package com.musicclock.alarm.data

object AlarmTimingPolicy {
    const val EARLY_TRIGGER_TOLERANCE_MILLIS = 2_000L

    fun isDue(triggerAtMillis: Long, nowMillis: Long): Boolean =
        triggerAtMillis <= nowMillis + EARLY_TRIGGER_TOLERANCE_MILLIS
}
