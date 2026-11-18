package com.musicclock.alarm.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private val volumeKey = intPreferencesKey("max_volume_percent")

    val maxVolumePercent: Flow<Int> = context.settingsDataStore.data.map { preferences ->
        (preferences[volumeKey] ?: DEFAULT_VOLUME_PERCENT).coerceIn(
            MIN_VOLUME_PERCENT,
            MAX_VOLUME_PERCENT,
        )
    }

    suspend fun setMaxVolumePercent(percent: Int) {
        context.settingsDataStore.edit { preferences ->
            preferences[volumeKey] = percent.coerceIn(MIN_VOLUME_PERCENT, MAX_VOLUME_PERCENT)
        }
    }

    companion object {
        const val DEFAULT_VOLUME_PERCENT = 40
        const val MIN_VOLUME_PERCENT = 10
        const val MAX_VOLUME_PERCENT = 80
    }
}

