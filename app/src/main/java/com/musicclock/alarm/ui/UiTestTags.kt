package com.musicclock.alarm.ui

object UiTestTags {
    const val HOME_ADD_ALARM = "home_add_alarm"
    const val HOME_SETTINGS = "home_settings"
    const val HOME_EMPTY_ADD = "home_empty_add"
    const val EDITOR_TIME = "editor_time"
    const val EDITOR_LABEL = "editor_label"
    const val EDITOR_ONE_SHOT = "editor_one_shot"
    const val EDITOR_WEEKLY = "editor_weekly"
    const val EDITOR_RINGTONE = "editor_ringtone"
    const val EDITOR_VIBRATION = "editor_vibration"
    const val EDITOR_SNOOZE = "editor_snooze"
    const val EDITOR_ENABLED = "editor_enabled"
    const val EDITOR_SAVE = "editor_save"
    const val EDITOR_DELETE = "editor_delete"
    const val EDITOR_IMPORT = "editor_import"
    const val SETTINGS_VOLUME = "settings_volume"
    const val RING_SNOOZE = "ring_snooze"
    const val RING_DISMISS = "ring_dismiss"
    const val RING_RETRY = "ring_retry"

    fun alarmRow(id: Long) = "alarm_row_$id"

    fun alarmSwitch(id: Long) = "alarm_switch_$id"

    fun weekday(index: Int) = "editor_weekday_$index"
}
