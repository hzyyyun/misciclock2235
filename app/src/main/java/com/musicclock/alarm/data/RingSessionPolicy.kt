package com.musicclock.alarm.data

object RingSessionPolicy {
    const val MAX_RING_DURATION_MILLIS = 10 * 60 * 1000L

    fun deadlineFrom(startedAtMillis: Long): Long =
        startedAtMillis + MAX_RING_DURATION_MILLIS
}
