package com.musicclock.alarm.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicclock.alarm.data.ScheduleType
import com.musicclock.alarm.ring.RingSessionStore
import com.musicclock.alarm.ui.AlarmEditorViewModel
import com.musicclock.alarm.ui.UiTestTags

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditorScreen(
    viewModel: AlarmEditorViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeRing by RingSessionStore.state.collectAsStateWithLifecycle()
    val ringingThisAlarm = activeRing?.takeIf {
        state.alarmId > 0 && it.alarmId == state.alarmId
    }
    var showTimePicker by remember { mutableStateOf(false) }
    var showRingtonePicker by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.importRingtone(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Text(if (state.alarmId == 0L) "新建闹钟" else "编辑闹钟")
                },
                actions = {
                    if (state.alarmId > 0) {
                        IconButton(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.testTag(UiTestTags.EDITOR_DELETE),
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除闹钟")
                        }
                    }
                    TextButton(
                        onClick = { viewModel.save(onBack) },
                        enabled = !state.saving,
                    ) {
                        Text("保存")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (!state.error.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        state.error.orEmpty(),
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showTimePicker = true }
                            .testTag(UiTestTags.EDITOR_TIME)
                            .padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            formatClock(state.hour, state.minute),
                            fontSize = 56.sp,
                            lineHeight = 60.sp,
                            fontWeight = FontWeight.Light,
                        )
                        Text(
                            "上下滚动选择时间",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    if (ringingThisAlarm != null) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (ringingThisAlarm.snoozeEnabled) {
                                Button(
                                    onClick = {
                                        viewModel.snoozeRinging(ringingThisAlarm.occurrenceId)
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .testTag(UiTestTags.EDITOR_RING_SNOOZE),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(
                                        Icons.Outlined.Snooze,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("延时 5 分钟")
                                }
                            }
                            OutlinedButton(
                                onClick = {
                                    viewModel.dismissRinging(ringingThisAlarm.occurrenceId)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag(UiTestTags.EDITOR_RING_STOP),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error,
                                ),
                            ) {
                                Icon(
                                    Icons.Outlined.AlarmOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("停止响铃")
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = state.label,
                onValueChange = { value ->
                    viewModel.update { it.copy(label = value.take(40)) }
                },
                label = { Text("名称") },
                placeholder = { Text("例如：起床") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(UiTestTags.EDITOR_LABEL),
            )

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("重复", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.scheduleType == ScheduleType.ONE_SHOT,
                        onClick = {
                            viewModel.update { it.copy(scheduleType = ScheduleType.ONE_SHOT) }
                        },
                        label = { Text("仅一次") },
                        modifier = Modifier.testTag(UiTestTags.EDITOR_ONE_SHOT),
                    )
                    FilterChip(
                        selected = state.scheduleType == ScheduleType.WEEKLY,
                        onClick = {
                            viewModel.update {
                                it.copy(
                                    scheduleType = ScheduleType.WEEKLY,
                                    weekdaysMask = if (it.weekdaysMask == 0) 0b1111111 else it.weekdaysMask,
                                )
                            }
                        },
                        label = { Text("每周") },
                        modifier = Modifier.testTag(UiTestTags.EDITOR_WEEKLY),
                    )
                }
                if (state.scheduleType == ScheduleType.WEEKLY) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        WeekdayLabels.forEachIndexed { index, label ->
                            val selected = state.weekdaysMask and (1 shl index) != 0
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    viewModel.update {
                                        it.copy(weekdaysMask = it.weekdaysMask xor (1 shl index))
                                    }
                                },
                                label = { Text(label) },
                                modifier = Modifier.testTag(UiTestTags.weekday(index)),
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRingtonePicker = true }
                    .testTag(UiTestTags.EDITOR_RINGTONE),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("闹铃声音", style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.importedRingtones.firstOrNull { it.id == state.ringtoneId }?.displayName
                                ?: "内置低频提示音",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "选择",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Column {
                ToggleRow(
                    title = "震动提醒",
                    description = "响铃期间循环震动",
                    checked = state.vibrationEnabled,
                    testTag = UiTestTags.EDITOR_VIBRATION,
                    onCheckedChange = { checked ->
                        viewModel.update { it.copy(vibrationEnabled = checked) }
                    },
                )
                HorizontalDivider()
                ToggleRow(
                    title = "延后 5 分钟",
                    description = "响铃页显示 Snooze 按钮",
                    checked = state.snoozeEnabled,
                    testTag = UiTestTags.EDITOR_SNOOZE,
                    onCheckedChange = { checked ->
                        viewModel.update { it.copy(snoozeEnabled = checked) }
                    },
                )
                HorizontalDivider()
                ToggleRow(
                    title = "启用闹钟",
                    description = "关闭后保留设置但不触发",
                    checked = state.enabled,
                    testTag = UiTestTags.EDITOR_ENABLED,
                    onCheckedChange = { checked ->
                        viewModel.update { it.copy(enabled = checked) }
                    },
                )
            }

            Button(
                onClick = { viewModel.save(onBack) },
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag(UiTestTags.EDITOR_SAVE),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(if (state.saving) "保存中…" else "保存闹钟")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showTimePicker) {
        TimeWheelPickerSheet(
            initialHour = state.hour,
            initialMinute = state.minute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.update { it.copy(hour = hour, minute = minute) }
                showTimePicker = false
            },
        )
    }

    if (showRingtonePicker) {
        AlertDialog(
            onDismissRequest = { showRingtonePicker = false },
            title = { Text("选择闹铃声音") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    RingtoneChoice(
                        title = "内置低频提示音",
                        subtitle = "离线、随应用安装",
                        selected = state.ringtoneId == null,
                        onClick = {
                            viewModel.update { it.copy(ringtoneId = null) }
                            showRingtonePicker = false
                        },
                    )
                    state.importedRingtones.forEach { ringtone ->
                        RingtoneChoice(
                            title = ringtone.displayName,
                            subtitle = formatFileSize(ringtone.sizeBytes),
                            selected = state.ringtoneId == ringtone.id,
                            onClick = {
                                viewModel.update { it.copy(ringtoneId = ringtone.id) }
                                showRingtonePicker = false
                            },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            showRingtonePicker = false
                            filePicker.launch(arrayOf("audio/*", "application/ogg"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UiTestTags.EDITOR_IMPORT),
                    ) {
                        Text("从本地导入音频")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRingtonePicker = false }) {
                    Text("取消")
                }
            },
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("删除这个闹钟？") },
            text = { Text("待触发实例和 Snooze 也会一并取消。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.delete(onBack)
                    },
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag),
            )
        },
    )
}

@Composable
private fun RingtoneChoice(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { RadioButton(selected = selected, onClick = onClick) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun formatFileSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}
