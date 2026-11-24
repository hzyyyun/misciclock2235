package com.musicclock.alarm.ring

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RingUiState(
    val occurrenceId: String,
    val alarmId: Long,
    val alarmLabel: String,
    val hour: Int,
    val minute: Int,
    val routeLabel: String,
    val audioStatus: String,
    val snoozeEnabled: Boolean,
    val routeFailed: Boolean = false,
    val autoStopped: Boolean = false,
)

object RingSessionStore {
    private val mutableState = MutableStateFlow<RingUiState?>(null)
    val state = mutableState.asStateFlow()

    fun update(value: RingUiState?) {
        mutableState.value = value
    }

    fun patch(transform: (RingUiState) -> RingUiState) {
        mutableState.value = mutableState.value?.let(transform)
    }
}

