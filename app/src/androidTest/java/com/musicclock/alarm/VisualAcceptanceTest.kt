package com.musicclock.alarm

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.musicclock.alarm.data.AlarmEntity
import com.musicclock.alarm.data.OccurrenceState
import com.musicclock.alarm.data.ScheduleType
import com.musicclock.alarm.ring.AlarmRingService
import com.musicclock.alarm.ring.RingActivity
import com.musicclock.alarm.ring.RingSessionStore
import com.musicclock.alarm.ui.MainActivity
import com.musicclock.alarm.ui.UiTestTags
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualAcceptanceTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val repository
        get() = (context.applicationContext as MusicClockApplication).container.repository
    private val device = UiDevice.getInstance(instrumentation)
    private var alarmId: Long? = null
    private var occurrenceId: String? = null
    private var headsUpWasChanged = false

    @After
    fun cleanUp() {
        runBlocking {
            occurrenceId?.let { id ->
                context.startService(
                    AlarmRingService.actionIntent(context, AlarmRingService.ACTION_DISMISS, id),
                )
                waitFor { repository.getOccurrence(id)?.state == OccurrenceState.DISMISSED }
            }
            alarmId?.let { repository.deleteAlarm(it) }
            context.stopService(Intent(context, AlarmRingService::class.java))
            if (headsUpWasChanged) {
                device.executeShellCommand("settings put global heads_up_notifications_enabled 1")
            }
        }
    }

    @Test
    fun captureHomeEditorAndRingScreens() = runBlocking {
        val outputDir = File(
            requireNotNull(context.getExternalFilesDir("acceptance")),
            "api35-${System.currentTimeMillis()}",
        ).apply { mkdirs() }

        composeRule.waitForIdle()
        device.waitForIdle()
        Thread.sleep(1_000L)
        capture("01-home.png", outputDir)
        composeRule.onNodeWithTag(UiTestTags.HOME_ADD_ALARM).performClick()
        composeRule.onNodeWithTag(UiTestTags.EDITOR_LABEL).assertIsDisplayed()
        composeRule.waitForIdle()
        device.waitForIdle()
        Thread.sleep(1_000L)
        capture("02-editor.png", outputDir)
        composeRule.onNodeWithTag(UiTestTags.EDITOR_TIME).performClick()
        composeRule.onNodeWithTag(UiTestTags.HOUR_WHEEL).assertIsDisplayed()
        composeRule.waitForIdle()
        Thread.sleep(500L)
        capture("03-time-wheel.png", outputDir)
        composeRule.onNodeWithTag(UiTestTags.TIME_PICKER_CANCEL).performClick()

        val alarm = createAlarm()
        occurrenceId = repository.scheduleOccurrence(
            alarmId = alarm.id,
            triggerAtMillis = System.currentTimeMillis(),
        ).id
        device.executeShellCommand("settings put global heads_up_notifications_enabled 0")
        headsUpWasChanged = true
        ContextCompat.startForegroundService(
            context,
            AlarmRingService.triggerIntent(context, requireNotNull(occurrenceId)),
        )
        waitFor { RingSessionStore.state.value != null }

        val ringActivity = instrumentation.startActivitySync(
            Intent(context, RingActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
        device.waitForIdle()
        device.executeShellCommand("cmd statusbar collapse")
        Thread.sleep(7_000L)
        device.waitForIdle()
        capture("04-ring.png", outputDir)
        instrumentation.runOnMainSync { ringActivity.finish() }
    }

    private suspend fun createAlarm(): AlarmEntity {
        val alarm = AlarmEntity(
            label = "验收响铃",
            hour = 7,
            minute = 30,
            scheduleType = ScheduleType.ONE_SHOT,
            oneShotAtMillis = System.currentTimeMillis() + 60_000L,
            vibrationEnabled = false,
        )
        alarmId = repository.saveAlarm(alarm)
        return alarm.copy(id = requireNotNull(alarmId))
    }

    private fun capture(fileName: String, outputDir: File) {
        assertTrue(
            "Failed to capture $fileName",
            device.takeScreenshot(File(outputDir, fileName)),
        )
    }

    private fun waitFor(timeoutMillis: Long = 10_000L, condition: suspend () -> Boolean) {
        runBlocking {
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                if (condition()) return@runBlocking
                delay(100L)
            }
            check(condition()) { "Timed out waiting for visual acceptance state" }
        }
    }
}
