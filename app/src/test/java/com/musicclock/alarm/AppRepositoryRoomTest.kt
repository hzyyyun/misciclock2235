package com.musicclock.alarm

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.musicclock.alarm.data.AlarmEntity
import com.musicclock.alarm.data.AppDatabase
import com.musicclock.alarm.data.AppRepository
import com.musicclock.alarm.data.OccurrenceState
import com.musicclock.alarm.data.OccurrenceKind
import com.musicclock.alarm.data.ScheduleType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppRepositoryRoomTest {
    private lateinit var database: AppDatabase
    private lateinit var repository: AppRepository
    private var now = 1_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = AppRepository(
            dao = database.alarmDao(),
            clock = { now },
            ringDurationMillis = 5_000L,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun twoCloseOccurrencesAreQueuedInsteadOfMixed() = runTest {
        val alarmId = repository.saveAlarm(
            AlarmEntity(
                label = "起床",
                hour = 7,
                minute = 0,
                scheduleType = ScheduleType.ONE_SHOT,
                oneShotAtMillis = 1_000L,
            ),
        )
        val first = repository.scheduleOccurrence(alarmId, 1_000L)
        val second = repository.scheduleOccurrence(alarmId, 2_000L)

        val claimedFirst = repository.claimOccurrence(first.id)
        val claimedSecond = repository.claimOccurrence(second.id)

        assertThat(claimedFirst?.state).isEqualTo(OccurrenceState.RINGING)
        assertThat(claimedSecond?.state).isEqualTo(OccurrenceState.QUEUED)

        repository.setOccurrenceState(first.id, OccurrenceState.DISMISSED)
        val promoted = repository.claimNextQueued()
        assertThat(promoted?.id).isEqualTo(second.id)
        assertThat(promoted?.state).isEqualTo(OccurrenceState.RINGING)
    }

    @Test
    fun deletingAlarmRemovesPendingOccurrences() = runTest {
        val alarmId = repository.saveAlarm(
            AlarmEntity(
                label = "起床",
                hour = 7,
                minute = 0,
                scheduleType = ScheduleType.WEEKLY,
                weekdaysMask = 0b1111111,
            ),
        )
        repository.scheduleOccurrence(alarmId, 5_000L)

        repository.deleteAlarm(alarmId)

        assertThat(repository.getPendingOccurrences()).isEmpty()
        assertThat(repository.getAlarm(alarmId)).isNull()
    }

    @Test
    fun replacingRegularScheduleKeepsIndependentSnooze() = runTest {
        val alarmId = repository.saveAlarm(
            AlarmEntity(
                label = "起床",
                hour = 7,
                minute = 0,
                scheduleType = ScheduleType.WEEKLY,
                weekdaysMask = 0b1111111,
            ),
        )
        repository.scheduleOccurrence(alarmId, 5_000L)
        repository.scheduleOccurrence(
            alarmId = alarmId,
            triggerAtMillis = 6_000L,
            kind = OccurrenceKind.SNOOZE,
            parentOccurrenceId = "parent",
        )

        repository.deleteScheduledOccurrences(alarmId)

        assertThat(repository.getScheduledOccurrencesForAlarm(alarmId)).isEmpty()
        assertThat(repository.getScheduledSnoozesForAlarm(alarmId)).hasSize(1)
    }

    @Test
    fun terminalOccurrenceCannotBeClaimedAgain() = runTest {
        val alarmId = repository.saveAlarm(
            AlarmEntity(
                label = "起床",
                hour = 7,
                minute = 0,
                scheduleType = ScheduleType.ONE_SHOT,
            ),
        )
        val occurrence = repository.scheduleOccurrence(alarmId, 5_000L)
        repository.setOccurrenceState(occurrence.id, OccurrenceState.DISMISSED)

        assertThat(repository.claimOccurrence(occurrence.id)).isNull()
    }

    @Test
    fun ringingOccurrenceCannotBeClaimedTwice() = runTest {
        val alarmId = repository.saveAlarm(
            AlarmEntity(
                label = "起床",
                hour = 7,
                minute = 0,
                scheduleType = ScheduleType.ONE_SHOT,
            ),
        )
        val occurrence = repository.scheduleOccurrence(alarmId, 5_000L)

        assertThat(repository.claimOccurrence(occurrence.id)?.state)
            .isEqualTo(OccurrenceState.RINGING)
        assertThat(repository.claimOccurrence(occurrence.id)).isNull()
    }

    @Test
    fun ringDeadlineIsAbsoluteAndClearedOnDismiss() = runTest {
        now = 10_000L
        val alarmId = repository.saveAlarm(
            AlarmEntity(
                label = "deadline",
                hour = 7,
                minute = 0,
                scheduleType = ScheduleType.ONE_SHOT,
            ),
        )
        val occurrence = repository.scheduleOccurrence(alarmId, 5_000L)

        val ringing = requireNotNull(repository.claimOccurrence(occurrence.id))
        assertThat(ringing.ringStartedAtMillis).isEqualTo(10_000L)
        assertThat(ringing.autoStopAtMillis).isEqualTo(15_000L)

        now = 12_000L
        val recovered = repository.ensureRingTiming(ringing)
        assertThat(recovered.autoStopAtMillis).isEqualTo(15_000L)

        repository.setOccurrenceState(occurrence.id, OccurrenceState.DISMISSED)
        val dismissed = requireNotNull(repository.getOccurrence(occurrence.id))
        assertThat(dismissed.ringStartedAtMillis).isNull()
        assertThat(dismissed.autoStopAtMillis).isNull()
    }
}

