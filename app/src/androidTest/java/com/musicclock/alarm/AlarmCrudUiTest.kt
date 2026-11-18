package com.musicclock.alarm

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.musicclock.alarm.data.AlarmEntity
import com.musicclock.alarm.data.ScheduleType
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
        repository.getAllAlarms()
            .filter { it.label == label }
            .forEach { repository.deleteAlarm(it.id) }
    }

    @Test
    fun createToggleEditAndDeleteAlarm() {
        composeRule.onNodeWithTag(UiTestTags.HOME_ADD_ALARM).performClick()
        composeRule.onNodeWithTag(UiTestTags.EDITOR_LABEL).performTextInput(label)
        composeRule.onNodeWithTag(UiTestTags.EDITOR_SAVE).performScrollTo().performClick()

        val alarm = waitForAlarm()
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

    private fun waitUntil(timeoutMillis: Long = 8_000L, condition: () -> Boolean) {
        composeRule.waitUntil(timeoutMillis, condition)
    }
}
