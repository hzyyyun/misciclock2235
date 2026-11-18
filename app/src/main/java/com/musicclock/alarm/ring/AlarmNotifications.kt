package com.musicclock.alarm.ring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.os.Build
import androidx.core.app.NotificationCompat
import com.musicclock.alarm.R

class AlarmNotifications(private val context: Context) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun createChannels() {
        val ringChannel = NotificationChannel(
            CHANNEL_RING,
            "闹钟响铃",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "当前响铃、耳机路由和延后提醒"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setAllowBubbles(false)
            }
        }
        val statusChannel = NotificationChannel(
            CHANNEL_STATUS,
            "闹钟状态",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "精确闹钟权限、铃声失效和调度状态"
            setSound(null, null)
            enableVibration(false)
        }
        manager.createNotificationChannels(listOf(ringChannel, statusChannel))
    }

    fun placeholder(): Notification = baseBuilder(CHANNEL_RING)
        .setContentTitle("正在准备闹钟")
        .setContentText("耳机优先播放与震动即将开始")
        .setOngoing(true)
        .build()

    fun ringing(state: RingUiState): Notification {
        val builder = baseBuilder(CHANNEL_RING)
            .setContentTitle(state.alarmLabel.ifBlank { "闹钟" })
            .setContentText("${formatTime(state.hour, state.minute)} · ${state.audioStatus}")
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(ringActivityPendingIntent(state.occurrenceId))

        if (canUseFullScreenIntent()) {
            builder.setFullScreenIntent(ringActivityPendingIntent(state.occurrenceId), true)
        }

        if (state.snoozeEnabled) {
            builder.addAction(
                0,
                "延后 5 分钟",
                servicePendingIntent(
                    state.occurrenceId,
                    AlarmRingService.ACTION_SNOOZE,
                    requestOffset = 1,
                ),
            )
        }
        builder.addAction(
            0,
            "关闭闹钟",
            servicePendingIntent(
                state.occurrenceId,
                AlarmRingService.ACTION_DISMISS,
                requestOffset = 2,
            ),
        )

        if (state.routeFailed) {
            builder.addAction(
                0,
                "重试耳机",
                servicePendingIntent(
                    state.occurrenceId,
                    AlarmRingService.ACTION_RETRY_AUDIO,
                    requestOffset = 3,
                ),
            )
        }
        return builder.build()
    }

    fun queued(alarmLabel: String): Notification = baseBuilder(CHANNEL_STATUS)
        .setContentTitle("另一个闹钟正在等待")
        .setContentText("${alarmLabel.ifBlank { "闹钟" }}将在当前闹钟处理后提醒")
        .setCategory(NotificationCompat.CATEGORY_STATUS)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    fun status(title: String, message: String, occurrenceId: String? = null): Notification {
        val builder = baseBuilder(CHANNEL_STATUS)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
        if (occurrenceId != null) {
            builder.setContentIntent(ringActivityPendingIntent(occurrenceId))
        }
        return builder.build()
    }

    fun notifyStatus(title: String, message: String, occurrenceId: String? = null) {
        manager.notify(STATUS_NOTIFICATION_ID, status(title, message, occurrenceId))
    }

    fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
            manager.canUseFullScreenIntent()

    private fun baseBuilder(channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setColor(0xff17392f.toInt())
            .setDefaults(0)
            .setSound(null)

    private fun ringActivityPendingIntent(occurrenceId: String): PendingIntent {
        val intent = Intent(context, RingActivity::class.java).apply {
            putExtra(AlarmRingService.EXTRA_OCCURRENCE_ID, occurrenceId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            occurrenceId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun servicePendingIntent(
        occurrenceId: String,
        action: String,
        requestOffset: Int,
    ): PendingIntent {
        val intent = AlarmRingService.actionIntent(context, action, occurrenceId)
        return PendingIntent.getService(
            context,
            occurrenceId.hashCode() * 31 + requestOffset,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

    companion object {
        const val CHANNEL_RING = "alarm_ringing_v1"
        const val CHANNEL_STATUS = "alarm_status_v1"
        const val RING_NOTIFICATION_ID = 1001
        const val STATUS_NOTIFICATION_ID = 1002
    }
}

