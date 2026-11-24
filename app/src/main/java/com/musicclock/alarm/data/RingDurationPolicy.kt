package com.musicclock.alarm.data

object RingDurationPolicy {
    const val MIN_DURATION_MILLIS = 5_000L
    const val MAX_DURATION_MILLIS = 10 * 60 * 1000L
    const val DEFAULT_DURATION_SECONDS = 300

    fun effectiveDurationMillis(
        alarm: AlarmEntity,
        singleRingtoneDurationMillis: Long,
    ): Long {
        val requested = when (alarm.ringStopMode) {
            RingStopMode.DURATION -> alarm.ringDurationSeconds * 1_000L
            RingStopMode.PLAY_COUNT -> {
                if (singleRingtoneDurationMillis <= 0L) {
                    DEFAULT_DURATION_SECONDS * 1_000L
                } else {
                    singleRingtoneDurationMillis * alarm.ringLoopCount.coerceAtLeast(1)
                }
            }
        }
        return requested.coerceIn(MIN_DURATION_MILLIS, MAX_DURATION_MILLIS)
    }

    fun maxLoopCount(singleRingtoneDurationMillis: Long): Int {
        if (singleRingtoneDurationMillis <= 0L) return 1
        return (MAX_DURATION_MILLIS / singleRingtoneDurationMillis)
            .coerceAtLeast(1L)
            .coerceAtMost(100L)
            .toInt()
    }
}
