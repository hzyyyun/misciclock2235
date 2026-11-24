package com.musicclock.alarm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.ContextCompat
import com.google.common.truth.Truth.assertThat
import com.musicclock.alarm.data.AlarmEntity
import com.musicclock.alarm.data.OccurrenceState
import com.musicclock.alarm.data.ScheduleType
import com.musicclock.alarm.ring.AlarmRingService
import com.musicclock.alarm.ring.RingSessionStore
import com.musicclock.alarm.ui.MainActivity
import com.musicclock.alarm.ui.UiTestTags
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AlarmCrudUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository
        get() = (context.applicationContext as MusicClockApplication).container.repository
    private val label = "instrumented-crud-${System.currentTimeMillis()}"

    @After
    fun cleanUp() = runBlocking {
        repository.getRingingOccurrence()?.let { ringing ->
            ContextCompat.startForegroundService(
                context,
                AlarmRingService.actionIntent(
                    context,
                    AlarmRingService.ACTION_DISMISS,
                    ringing.id,
                ),
            )
        }
        repository.getAllAlarms()
            .filter { it.label == label || it.label.startsWith("$label-") }
            .forEach { repository.deleteAlarm(it.id) }
    }

    @Test
    fun createToggleEditAndDeleteAlarm() {
        composeRule.onNodeWithTag(UiTestTags.HOME_ADD_ALARM).performClick()
        composeRule.onNodeWithTag(UiTestTags.EDITOR_LABEL).performTextInput(label)
        composeRule.onNodeWithTag(UiTestTags.EDITOR_TIME).performScrollTo().performClick()
        composeRule.onNodeWithTag(UiTestTags.HOUR_WHEEL).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.MINUTE_WHEEL).assertIsDisplayed()
        val initialTime = selectedTimeText()
        composeRule.onNodeWithTag(UiTestTags.HOUR_WHEEL)
            .performTouchInput { swipeUp(durationMillis = 500) }
        waitUntil { selectedTimeText() != initialTime }
        composeRule.waitForIdle()
        val selectedHour = selectedTimeText().substringBefore(":").toInt()
        composeRule.onNodeWithTag(UiTestTags.TIME_PICKER_CONFIRM).performClick()
        composeRule.onNodeWithTag(UiTestTags.EDITOR_SAVE).performScrollTo().performClick()

        val alarm = waitForAlarm()
        assertThat(alarm.hour).isEqualTo(selectedHour)
        composeRule.onNodeWithTag(UiTestTags.alarmRow(alarm.id))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(label).assertIsDisplayed()

        composeRule.onNodeWithTag(UiTestTags.alarmSwitch(alarm.id))
            .performScrollTo()
            .performClick()
        waitUntil { runBlocking { repository.getAlarm(alarm.id)?.enabled } == false }

        composeRule.onNodeWithTag(UiTestTags.alarmRow(alarm.id))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(UiTestTags.EDITOR_DELETE).performClick()
        composeRule.onNodeWithText("删除").performClick()
        waitUntil { runBlocking { repository.getAlarm(alarm.id) } == null }
    }

    @Test
    fun ringingControlsOnlyAppearOnMatchingAlarmAndStopTheRing() {
        val ringingAlarm = createAlarm("$label-ring")
        val otherAlarm = createAlarm("$label-other")
        val occurrence = runBlocking {
            repository.scheduleOccurrence(
                alarmId = ringingAlarm.id,
                triggerAtMillis = System.currentTimeMillis(),
            )
        }
        ContextCompat.startForegroundService(
            context,
            AlarmRingService.triggerIntent(context, occurrence.id),
        )
        waitUntil { RingSessionStore.state.value?.occurrenceId == occurrence.id }

        composeRule.onNodeWithTag(UiTestTags.alarmRow(otherAlarm.id))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(UiTestTags.EDITOR_RING_STOP).assertDoesNotExist()
        composeRule.onNodeWithContentDescription("返回").performClick()

        composeRule.onNodeWithTag(UiTestTags.alarmRow(ringingAlarm.id))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(UiTestTags.EDITOR_RING_SNOOZE).assertIsDisplayed()
        composeRule.onNodeWithTag(UiTestTags.EDITOR_RING_STOP)
            .assertIsDisplayed()
            .performClick()

        waitUntil {
            runBlocking {
                repository.getOccurrence(occurrence.id)?.state == OccurrenceState.DISMISSED
            }
        }
        waitUntil { RingSessionStore.state.value == null }
        composeRule.onNodeWithTag(UiTestTags.EDITOR_RING_STOP).assertDoesNotExist()
    }

    @Test
    fun appLaunchDoesNotResumePersistedRingingSession() {
        val alarm = createAlarm("$label-stale")
        val now = System.currentTimeMillis()
        val persistedRinging = runBlocking {
            repository.scheduleOccurrence(
                alarmId = alarm.id,
                triggerAtMillis = now,
            ).copy(
                state = OccurrenceState.RINGING,
                ringStartedAtMillis = now,
                autoStopAtMillis = now + 60_000L,
            )
        }
        runBlocking {
            (context.applicationContext as MusicClockApplication)
                .container.database
                .alarmDao()
                .upsertOccurrence(persistedRinging)
        }

        composeRule.runOnUiThread { composeRule.activity.recreate() }
        Thread.sleep(1_000L)

        assertThat(RingSessionStore.state.value).isNull()
    }

    private fun createAlarm(alarmLabel: String): AlarmEntity = runBlocking {
        val alarm = AlarmEntity(
            label = alarmLabel,
            hour = 7,
            minute = 0,
            scheduleType = ScheduleType.WEEKLY,
            weekdaysMask = 0b1111111,
            snoozeEnabled = true,
            vibrationEnabled = false,
        )
        alarm.copy(id = repository.saveAlarm(alarm))
    }

    private fun waitForAlarm(): AlarmEntity {
        var result: AlarmEntity? = null
        waitUntil {
            result = runBlocking {
                repository.getAllAlarms().firstOrNull { it.label == label }
            }
            result != null
        }
        return requireNotNull(result)
    }

    private fun selectedTimeText(): String =
        composeRule.onNodeWithTag(UiTestTags.TIME_PICKER_VALUE)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }

    private fun waitUntil(timeoutMillis: Long = 8_000L, condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis, condition)
    }
}
