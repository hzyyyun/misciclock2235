package com.musicclock.alarm.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musicclock.alarm.MusicClockApplication
import com.musicclock.alarm.alarm.OccurrenceCalculator
import com.musicclock.alarm.audio.RingtoneImportResult
import com.musicclock.alarm.audio.RingtoneImporter
import com.musicclock.alarm.data.AlarmEntity
import com.musicclock.alarm.data.RingtoneEntity
import com.musicclock.alarm.data.ScheduleType
import com.musicclock.alarm.ring.AlarmRingService
import java.time.ZoneId
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val nowMillis: Long = System.currentTimeMillis(),
    val alarms: List<AlarmEntity> = emptyList(),
    val nextOccurrenceAtMillis: Long? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as MusicClockApplication
    private val repository = app.container.repository
    private val scheduler = app.container.alarmScheduler

    val state: StateFlow<HomeUiState> = combine(
        repository.alarms,
        repository.pendingOccurrences,
        tickingClock(),
    ) { alarms, occurrences, now ->
        val next = occurrences
            .filter { it.triggerAtMillis > now }
            .minOfOrNull { it.triggerAtMillis }
        HomeUiState(
            nowMillis = now,
            alarms = alarms,
            nextOccurrenceAtMillis = next,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(),
    )

    fun setEnabled(alarm: AlarmEntity, enabled: Boolean) {
        viewModelScope.launch {
            val updated = if (enabled && alarm.scheduleType == ScheduleType.ONE_SHOT) {
                val oneShotAt = OccurrenceCalculator.oneShotAt(
                    System.currentTimeMillis(),
                    alarm.hour,
                    alarm.minute,
                    ZoneId.systemDefault(),
                )
                alarm.copy(enabled = true, oneShotAtMillis = oneShotAt)
            } else {
                alarm.copy(enabled = enabled)
            }
            repository.saveAlarm(updated)
            if (enabled) {
                scheduler.scheduleNext(updated)
            } else {
                scheduler.cancelScheduled(alarm.id)
            }
        }
    }

    fun refreshScheduling() {
        viewModelScope.launch { scheduler.rescheduleAll() }
    }
}

data class EditorUiState(
    val loading: Boolean = true,
    val alarmId: Long = 0,
    val label: String = "",
    val hour: Int = 7,
    val minute: Int = 0,
    val scheduleType: ScheduleType = ScheduleType.ONE_SHOT,
    val weekdaysMask: Int = 0,
    val ringtoneId: Long? = null,
    val vibrationEnabled: Boolean = true,
    val snoozeEnabled: Boolean = true,
    val enabled: Boolean = true,
    val saving: Boolean = false,
    val error: String? = null,
    val importedRingtones: List<RingtoneEntity> = emptyList(),
)

class AlarmEditorViewModel(
    application: Application,
    private val alarmId: Long,
) : AndroidViewModel(application) {
    private val app = application as MusicClockApplication
    private val repository = app.container.repository
    private val scheduler = app.container.alarmScheduler
    private val importer = RingtoneImporter(application)
    private val mutableState = MutableStateFlow(EditorUiState(alarmId = alarmId))
    val state: StateFlow<EditorUiState> = mutableState
    init {
        viewModelScope.launch {
            repository.ringtones.collect { ringtones ->
                mutableState.update { it.copy(importedRingtones = ringtones) }
            }
        }
        viewModelScope.launch {
            val existing = if (alarmId > 0) repository.getAlarm(alarmId) else null
            if (existing != null) {
                mutableState.update {
                    it.copy(
                        loading = false,
                        label = existing.label,
                        hour = existing.hour,
                        minute = existing.minute,
                        scheduleType = existing.scheduleType,
                        weekdaysMask = existing.weekdaysMask,
                        ringtoneId = existing.ringtoneId,
                        vibrationEnabled = existing.vibrationEnabled,
                        snoozeEnabled = existing.snoozeEnabled,
                        enabled = existing.enabled,
                    )
                }
            } else {
                mutableState.update { it.copy(loading = false) }
            }
        }
    }

    fun update(transform: (EditorUiState) -> EditorUiState) {
        mutableState.update(transform)
    }

    fun importRingtone(uri: Uri) {
        viewModelScope.launch {
            mutableState.update { it.copy(error = null) }
            when (val result = importer.import(uri)) {
                is RingtoneImportResult.Success -> {
                    val id = repository.saveRingtone(result.ringtone)
                    mutableState.update { it.copy(ringtoneId = id) }
                }

                is RingtoneImportResult.Failure -> {
                    mutableState.update { it.copy(error = result.message) }
                }
            }
        }
    }

    fun snoozeRinging(occurrenceId: String) {
        sendRingAction(AlarmRingService.ACTION_SNOOZE, occurrenceId)
    }

    fun dismissRinging(occurrenceId: String) {
        sendRingAction(AlarmRingService.ACTION_DISMISS, occurrenceId)
    }

    private fun sendRingAction(action: String, occurrenceId: String) {
        val application = getApplication<Application>()
        ContextCompat.startForegroundService(
            application,
            AlarmRingService.actionIntent(application, action, occurrenceId),
        )
    }

    fun save(onSaved: () -> Unit) {
        val current = mutableState.value
        if (current.scheduleType == ScheduleType.WEEKLY && current.weekdaysMask == 0) {
            mutableState.update { it.copy(error = "每周重复至少选择一天") }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(saving = true, error = null) }
            val now = System.currentTimeMillis()
            val oneShotAt = if (current.scheduleType == ScheduleType.ONE_SHOT) {
                OccurrenceCalculator.oneShotAt(
                    now,
                    current.hour,
                    current.minute,
                    ZoneId.systemDefault(),
                )
            } else {
                null
            }
            val entity = AlarmEntity(
                id = current.alarmId,
                label = current.label.trim().ifBlank { "闹钟" }.take(40),
                hour = current.hour,
                minute = current.minute,
                scheduleType = current.scheduleType,
                weekdaysMask = if (current.scheduleType == ScheduleType.WEEKLY) {
                    current.weekdaysMask
                } else {
                    0
                },
                oneShotAtMillis = oneShotAt,
                enabled = current.enabled,
                ringtoneId = current.ringtoneId,
                vibrationEnabled = current.vibrationEnabled,
                snoozeEnabled = current.snoozeEnabled,
            )
            val savedId = if (entity.id == 0L) repository.saveAlarm(entity) else {
                repository.saveAlarm(entity)
                entity.id
            }
            val saved = entity.copy(id = savedId)
            if (saved.enabled) scheduler.scheduleNext(saved)
            mutableState.update { it.copy(saving = false, alarmId = savedId) }
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = mutableState.value.alarmId
        if (id == 0L) {
            onDeleted()
            return
        }
        viewModelScope.launch {
            scheduler.cancelAllPending(id)
            repository.deleteAlarm(id)
            onDeleted()
        }
    }

    companion object {
        fun factory(application: Application, alarmId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AlarmEditorViewModel(application, alarmId) as T
            }
    }
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val settings = (application as MusicClockApplication).container.settings
    val maxVolumePercent: StateFlow<Int> = settings.maxVolumePercent.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        40,
    )

    fun setVolume(percent: Int) {
        viewModelScope.launch { settings.setMaxVolumePercent(percent) }
    }
}

private fun tickingClock() = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(1_000)
    }
}
