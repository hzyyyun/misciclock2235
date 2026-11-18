package com.musicclock.alarm.ring

import android.app.KeyguardManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.musicclock.alarm.ui.theme.MusicClockTheme

class RingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        getSystemService(KeyguardManager::class.java).requestDismissKeyguard(this, null)

        setContent {
            MusicClockTheme {
                RingContent(onClose = ::finish)
            }
        }
    }
}

@Composable
private fun RingContent(onClose: () -> Unit) {
    val context = LocalContext.current
    val state by RingSessionStore.state.collectAsStateWithLifecycle()
    BackHandler(enabled = true) {}

    LaunchedEffect(state) {
        if (state == null) onClose()
    }
    val current = state ?: return

    fun send(action: String) {
        ContextCompat.startForegroundService(
            context,
            AlarmRingService.actionIntent(context, action, current.occurrenceId),
        )
    }

    Scaffold(containerColor = Color(0xFF10221C)) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    color = Color(0xFF213D33),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Headphones,
                            contentDescription = null,
                            tint = Color(0xFFA8DDC6),
                            modifier = Modifier.size(19.dp),
                        )
                        Text(
                            current.routeLabel,
                            color = Color(0xFFDDEAE4),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "%02d:%02d".format(current.hour, current.minute),
                    color = Color(0xFFF4C95D),
                    fontSize = 76.sp,
                    lineHeight = 82.sp,
                    fontWeight = FontWeight.Light,
                )
                Text(
                    current.alarmLabel.ifBlank { "闹钟" },
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Vibration,
                        contentDescription = null,
                        tint = Color(0xFFA8DDC6),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        current.audioStatus,
                        color = Color(0xFFDDEAE4),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (current.routeFailed) {
                    OutlinedButton(
                        onClick = { send(AlarmRingService.ACTION_RETRY_AUDIO) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFFDDEAE4),
                        ),
                    ) {
                        Text("重新连接后重试耳机播放")
                    }
                }
                if (current.snoozeEnabled) {
                    Button(
                        onClick = { send(AlarmRingService.ACTION_SNOOZE) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFF4C95D),
                            contentColor = Color(0xFF17251F),
                        ),
                    ) {
                        Text("延后 5 分钟", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                Button(
                    onClick = { send(AlarmRingService.ACTION_DISMISS) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF315448),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("关闭闹钟", fontSize = 17.sp)
                }
            }
        }
    }
}
