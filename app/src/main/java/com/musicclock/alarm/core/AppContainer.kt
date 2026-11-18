package com.musicclock.alarm.core

import android.content.Context
import com.musicclock.alarm.alarm.AlarmScheduler
import com.musicclock.alarm.data.AppDatabase
import com.musicclock.alarm.data.AppRepository
import com.musicclock.alarm.data.SettingsRepository

class AppContainer(context: Context) {
    val database: AppDatabase = AppDatabase.create(context)
    val repository = AppRepository(database.alarmDao())
    val settings = SettingsRepository(context)
    val alarmScheduler = AlarmScheduler(context, repository)
}
