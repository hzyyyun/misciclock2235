package com.musicclock.alarm.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatterySaver
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicclock.alarm.audio.AudioRouteController
import com.musicclock.alarm.core.AppPermissionStatus
import com.musicclock.alarm.core.PermissionStatus
import com.musicclock.alarm.ui.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val persistedVolume by viewModel.maxVolumePercent.collectAsStateWithLifecycle()
    var volume by remember { mutableFloatStateOf(persistedVolume.toFloat()) }
    var permissions by remember { mutableStateOf(PermissionStatus.read(context)) }
    var headsetLabel by remember {
        mutableStateOf(AudioRouteController(context).currentHeadsetTarget()?.label ?: "未连接耳机")
    }
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissions = PermissionStatus.read(context)
    }

    LaunchedEffect(persistedVolume) {
        volume = persistedVolume.toFloat()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissions = PermissionStatus.read(context)
                headsetLabel = AudioRouteController(context).currentHeadsetTarget()?.label
                    ?: "未连接耳机"
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("设置与隐私") },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        Icons.Outlined.Headphones,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Column {
                        Text(
                            "耳机优先，断连即暂停",
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "当前：$headsetLabel",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "应用音量上限",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${volume.roundToInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Slider(
                    value = volume,
                    onValueChange = { volume = it },
                    onValueChangeFinished = {
                        viewModel.setVolume(volume.roundToInt())
                    },
                    valueRange = 10f..80f,
                    steps = 69,
                )
                Text(
                    "仅限制应用内部播放增益，不修改手机系统音量。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text("权限与可靠性", style = MaterialTheme.typography.titleLarge)
            PermissionRow(
                title = "精确闹钟",
                description = if (permissions.exactAlarm) {
                    "已允许，按设定时间触发"
                } else {
                    "未允许，只能尽力调度，无法保证准点"
                },
                granted = permissions.exactAlarm,
                action = "打开设置",
                onClick = {
                    runCatching { context.startActivity(PermissionStatus.exactAlarmSettings(context)) }
                },
            )
            HorizontalDivider()
            PermissionRow(
                title = "通知",
                description = if (permissions.notifications) {
                    "已允许响铃和路由状态通知"
                } else {
                    "未允许，锁屏提示和故障通知可能不可见"
                },
                granted = permissions.notifications,
                action = "授权",
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        context.startActivity(PermissionStatus.notificationSettings(context))
                    }
                },
            )
            HorizontalDivider()
            PermissionRow(
                title = "全屏响铃",
                description = if (permissions.fullScreenIntent) {
                    "锁屏时可打开响铃页"
                } else {
                    "未允许，将退回高优先级通知"
                },
                granted = permissions.fullScreenIntent,
                action = "打开设置",
                onClick = {
                    runCatching {
                        context.startActivity(PermissionStatus.fullScreenSettings(context))
                    }.onFailure {
                        context.startActivity(PermissionStatus.notificationSettings(context))
                    }
                },
            )
            HorizontalDivider()
            PermissionRow(
                title = "电池限制",
                description = if (permissions.batteryUnrestricted) {
                    "系统不会对此应用启用常见电池优化"
                } else {
                    "厂商省电策略可能延迟或中断后台闹钟"
                },
                granted = permissions.batteryUnrestricted,
                action = "系统设置",
                onClick = {
                    runCatching { context.startActivity(PermissionStatus.batterySettings(context)) }
                },
            )

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.NotificationsActive, contentDescription = null)
                        Text("勿扰与静音", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "震动和耳机声音仍受系统勿扰、静音策略及耳机自身音量控制。应用不会申请覆盖勿扰模式的权限。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Shield, contentDescription = null)
                        Text("完全离线", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "应用未声明 INTERNET 权限，不使用分析、云同步或崩溃上报。闹钟、设置和导入的音频只保存在本机私有目录，系统备份已禁用。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "时间来自系统本地时钟，不进行网络校时。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.BatterySaver, contentDescription = null)
                    Text(
                        "部分厂商还提供“自启动”或“后台运行”开关。系统若强行停止应用，Android 会取消后续提醒，需要手动重新打开应用。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Text(
                "耳机闹钟 1.0 · Android",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!granted) {
            TextButton(onClick = onClick) { Text(action) }
        } else {
            Text(
                "已开启",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
