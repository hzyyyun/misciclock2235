package com.musicclock.alarm

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.google.common.truth.Truth.assertThat
import com.musicclock.alarm.alarm.AlarmScheduler
import com.musicclock.alarm.data.AlarmEntity
import com.musicclock.alarm.data.AppRepository
import com.musicclock.alarm.data.OccurrenceKind
import com.musicclock.alarm.data.OccurrenceState
import com.musicclock.alarm.data.ScheduleType
import com.musicclock.alarm.ring.AlarmRingService
import com.musicclock.alarm.ring.RingSessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmServiceInstrumentedTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container
        get() = (context.applicationContext as MusicClockApplication).container
    private val repository: AppRepository
        get() = container.repository
    private val scheduler: AlarmScheduler
        get() = container.alarmScheduler
    private val testAlarmIds = mutableListOf<Long>()

    @After
    fun cleanUp() {
        runBlocking {
            val ringing = repository.getRingingOccurrence()
            if (ringing != null) {
                context.startService(
                    AlarmRingService.actionIntent(context, AlarmRingService.ACTION_DISMISS, ringing.id),
                )
                waitFor { repository.getOccurrence(ringing.id)?.state == OccurrenceState.DISMISSED }
            }
            testAlarmIds.forEach { alarmId ->
                scheduler.cancelAllPending(alarmId)
                repository.deleteAlarm(alarmId)
            }
            context.stopService(Intent(context, AlarmRingService::class.java))
        }
    }

    @Test
    fun snoozeCreatesIndependentFiveMinuteOccurrence() {
        runBlocking {
            val alarm = createAlarm("instrumented-snooze")
            val parent = repository.scheduleOccurrence(alarm.id, System.currentTimeMillis())
            startRing(parent.id)

            waitFor { repository.getRingingOccurrence()?.id == parent.id }
            assertThat(RingSessionStore.state.value?.snoozeEnabled).isTrue()

            val beforeSnooze = System.currentTimeMillis()
            context.startService(
                AlarmRingService.actionIntent(context, AlarmRingService.ACTION_SNOOZE, parent.id),
            )

            waitFor {
                repository.getScheduledSnoozesForAlarm(alarm.id)
                    .any { it.parentOccurrenceId == parent.id }
            }
            assertThat(repository.getOccurrence(parent.id)?.state).isEqualTo(OccurrenceState.SNOOZED)
            val snooze = repository.getScheduledSnoozesForAlarm(alarm.id)
                .first { it.parentOccurrenceId == parent.id }
            assertThat(snooze.kind).isEqualTo(OccurrenceKind.SNOOZE)
            assertThat(snooze.triggerAtMillis - beforeSnooze)
                .isAtLeast(4 * 60 * 1000L + 50 * 1000L)
            assertThat(snooze.triggerAtMillis - beforeSnooze)
                .isAtMost(5 * 60 * 1000L + 10 * 1000L)
        }
    }

    @Test
    fun secondTriggerQueuesAndPromotesAfterDismissal() {
        runBlocking {
            val alarm = createAlarm("instrumented-queue")
            val first = repository.scheduleOccurrence(alarm.id, System.currentTimeMillis())
            val second = repository.scheduleOccurrence(alarm.id, System.currentTimeMillis() + 1_000L)

            startRing(first.id)
            waitFor { repository.getRingingOccurrence()?.id == first.id }
            startRing(second.id)
            waitFor { repository.getOccurrence(second.id)?.state == OccurrenceState.QUEUED }

            context.startService(
                AlarmRingService.actionIntent(context, AlarmRingService.ACTION_DISMISS, first.id),
            )
            waitFor { repository.getRingingOccurrence()?.id == second.id }
            assertThat(repository.getOccurrence(first.id)?.state).isEqualTo(OccurrenceState.DISMISSED)
            assertThat(repository.getOccurrence(second.id)?.state).isEqualTo(OccurrenceState.RINGING)
        }
    }

    @Test
    fun exactAlarmBroadcastStartsForegroundRingingService() {
        runBlocking {
            grantExactAlarmAppOp()
            waitFor(timeoutMillis = 5_000L) { scheduler.canScheduleExactAlarms() }
            assertThat(scheduler.canScheduleExactAlarms()).isTrue()
            val wantedTriggerAt = System.currentTimeMillis() + 15_000L
            val alarm = createAlarm("instrumented-exact", wantedTriggerAt)
            val occurrence = requireNotNull(scheduler.scheduleNext(alarm))

            waitFor(timeoutMillis = 45_000L) {
                repository.getRingingOccurrence()?.id == occurrence.id
            }
            assertThat(repository.getOccurrence(occurrence.id)?.state)
                .isEqualTo(OccurrenceState.RINGING)
            assertThat(occurrence.triggerAtMillis).isEqualTo(wantedTriggerAt)
        }
    }

    @Test
    fun exactAlarmTriggersWhileScreenIsOff() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        runBlocking {
            grantExactAlarmAppOp()
            waitFor(timeoutMillis = 5_000L) { scheduler.canScheduleExactAlarms() }
            val alarm = createAlarm(
                "instrumented-screen-off",
                System.currentTimeMillis() + 15_000L,
            )
            val occurrence = requireNotNull(scheduler.scheduleNext(alarm))

            device.sleep()
            try {
                waitFor(timeoutMillis = 45_000L) {
                    repository.getRingingOccurrence()?.id == occurrence.id
                }
            } finally {
                device.wakeUp()
                device.pressMenu()
            }
        }
    }

    private suspend fun createAlarm(
        labelPrefix: String,
        triggerAtMillis: Long = System.currentTimeMillis() + 60_000L,
    ): AlarmEntity {
        val now = System.currentTimeMillis()
        val alarm = AlarmEntity(
            label = "$labelPrefix-$now",
            hour = 7,
            minute = 0,
            scheduleType = ScheduleType.ONE_SHOT,
            oneShotAtMillis = triggerAtMillis,
            snoozeEnabled = true,
            vibrationEnabled = false,
        )
        val id = repository.saveAlarm(alarm)
        testAlarmIds += id
        return alarm.copy(id = id)
    }

    private fun startRing(occurrenceId: String) {
        ContextCompat.startForegroundService(
            context,
            AlarmRingService.triggerIntent(context, occurrenceId),
        )
    }

    private fun grantExactAlarmAppOp() {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand(
                "cmd appops set com.musicclock.alarm SCHEDULE_EXACT_ALARM allow",
            )
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }

    private fun waitFor(timeoutMillis: Long = 10_000L, condition: suspend () -> Boolean) {
        runBlocking {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return@runBlocking
                delay(100L)
            }
            check(condition()) { "Timed out waiting for instrumented condition" }
        }
    }
}
