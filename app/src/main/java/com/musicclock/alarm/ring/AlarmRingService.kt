package com.musicclock.alarm.ring

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.musicclock.alarm.MusicClockApplication
import com.musicclock.alarm.alarm.AlarmScheduler
import com.musicclock.alarm.audio.AudioRouteController
import com.musicclock.alarm.audio.BuiltInToneGenerator
import com.musicclock.alarm.audio.HeadsetSafePlayer
import com.musicclock.alarm.audio.PlaybackEvent
import com.musicclock.alarm.audio.VibrationController
import com.musicclock.alarm.data.AlarmEntity
import com.musicclock.alarm.data.AppRepository
import com.musicclock.alarm.data.OccurrenceEntity
import com.musicclock.alarm.data.OccurrenceKind
import com.musicclock.alarm.data.OccurrenceState
import com.musicclock.alarm.data.ScheduleType
import com.musicclock.alarm.data.SettingsRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class AlarmRingService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repository: AppRepository
    private lateinit var scheduler: AlarmScheduler
    private lateinit var settings: SettingsRepository
    private lateinit var notifications: AlarmNotifications
    private lateinit var player: HeadsetSafePlayer
    private lateinit var vibration: VibrationController
    private lateinit var routeController: AudioRouteController
    private var deviceCallback: android.media.AudioDeviceCallback? = null

    private var activeOccurrence: OccurrenceEntity? = null
    private var activeAlarm: AlarmEntity? = null
    private var timeoutJob: Job? = null
    private var fallbackAttempted = false
    private var foregroundStarted = false
    private val triggerMutex = Mutex()

    override fun onCreate() {
        super.onCreate()
        val container = (application as MusicClockApplication).container
        repository = container.repository
        scheduler = container.alarmScheduler
        settings = container.settings
        notifications = AlarmNotifications(this).also { it.createChannels() }
        player = HeadsetSafePlayer(this)
        vibration = VibrationController(this)
        routeController = AudioRouteController(this)
        deviceCallback = routeController.registerDeviceCallback(mainHandler()) {
            player.verifyRouteAfterDeviceChange()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        when (intent?.action) {
            ACTION_TRIGGER -> {
                val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID)
                if (occurrenceId != null) {
                    serviceScope.launch { processTrigger(occurrenceId) }
                }
            }

            ACTION_DISMISS -> {
                val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID)
                serviceScope.launch { dismiss(occurrenceId) }
            }

            ACTION_SNOOZE -> {
                val occurrenceId = intent.getStringExtra(EXTRA_OCCURRENCE_ID)
                serviceScope.launch { snooze(occurrenceId) }
            }

            ACTION_RETRY_AUDIO -> {
                activeOccurrence?.let { occurrence ->
                    activeAlarm?.let { alarm ->
                        startRing(occurrence, alarm, retry = true)
                    }
                }
            }

            null -> serviceScope.launch { recoverSession() }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        timeoutJob?.cancel()
        player.stop()
        vibration.stop()
        deviceCallback?.let { routeController.unregisterDeviceCallback(it) }
        RingSessionStore.update(null)
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun processTrigger(occurrenceId: String) = triggerMutex.withLock {
        if (activeOccurrence?.id == occurrenceId) return@withLock
        val claimed = repository.claimOccurrence(occurrenceId)
        if (claimed == null) {
            val existing = repository.getOccurrence(occurrenceId)
            if (existing?.state == OccurrenceState.RINGING && activeOccurrence == null) {
                val alarm = repository.getAlarm(existing.alarmId)
                if (alarm != null) {
                    advanceRecurringAlarm(alarm, existing)
                    startRing(existing, alarm)
                } else {
                    repository.setOccurrenceState(existing.id, OccurrenceState.MISSED)
                    promoteNext()
                }
            } else {
                stopIfIdle()
            }
            return@withLock
        }
        val alarm = repository.getAlarm(claimed.alarmId) ?: run {
            repository.setOccurrenceState(claimed.id, OccurrenceState.MISSED)
            promoteNext()
            return@withLock
        }
        advanceRecurringAlarm(alarm, claimed)

        when (claimed.state) {
            OccurrenceState.RINGING -> startRing(claimed, alarm)
            OccurrenceState.QUEUED -> updateQueuedNotification(alarm)
            else -> stopIfIdle()
        }
    }

    private suspend fun recoverSession() = triggerMutex.withLock {
        if (activeOccurrence != null) return@withLock
        val ringing = repository.getRingingOccurrence() ?: repository.claimNextQueued()
        if (ringing == null) {
            stopIfIdle()
            return@withLock
        }
        val alarm = repository.getAlarm(ringing.alarmId)
        if (alarm == null) {
            repository.setOccurrenceState(ringing.id, OccurrenceState.MISSED)
            promoteNext()
        } else {
            startRing(ringing, alarm)
        }
    }

    private suspend fun advanceRecurringAlarm(alarm: AlarmEntity, occurrence: OccurrenceEntity) {
        if (occurrence.kind != OccurrenceKind.SCHEDULED) return
        when (alarm.scheduleType) {
            ScheduleType.ONE_SHOT -> repository.saveAlarm(alarm.copy(enabled = false))
            ScheduleType.WEEKLY -> scheduler.scheduleNext(alarm)
        }
    }

    private fun startRing(
        occurrence: OccurrenceEntity,
        alarm: AlarmEntity,
        retry: Boolean = false,
        useBuiltIn: Boolean = false,
    ) {
        activeOccurrence = occurrence
        activeAlarm = alarm
        timeoutJob?.cancel()
        player.stop()
        vibration.stop()
        if (!retry) fallbackAttempted = false

        val headset = routeController.connectedHeadset()
        val routeLabel = headset?.productName?.toString()?.ifBlank { "已连接耳机" } ?: "未连接耳机"
        RingSessionStore.update(
            RingUiState(
                occurrenceId = occurrence.id,
                alarmLabel = alarm.label,
                hour = alarm.hour,
                minute = alarm.minute,
                routeLabel = routeLabel,
                audioStatus = if (headset != null) "正在确认耳机路由，暂时静音" else "使用系统默认闹铃输出",
                snoozeEnabled = alarm.snoozeEnabled,
            ),
        )
        publishRingingNotification()

        if (alarm.vibrationEnabled) vibration.startAlarmPattern()

        serviceScope.launch {
            val maxVolume = settings.maxVolumePercent.first() / 100f
            if (activeOccurrence?.id != occurrence.id) return@launch
            val soundFile = resolveSoundFile(alarm, useBuiltIn)
            if (soundFile == null) {
                onDecodeFailure("铃声文件不存在或无法读取")
                return@launch
            }
            if (activeOccurrence?.id != occurrence.id) return@launch
            player.play(soundFile, routeController.connectedHeadset(), maxVolume) { event ->
                onPlaybackEvent(event, alarm)
            }
        }

        timeoutJob = serviceScope.launch {
            delay(AUTO_STOP_MILLIS)
            if (activeOccurrence?.id == occurrence.id) {
                RingSessionStore.patch { it.copy(audioStatus = "已自动静音", autoStopped = true) }
                repository.setOccurrenceState(occurrence.id, OccurrenceState.MISSED)
                notifications.notifyStatus(
                    "闹钟已自动静音",
                    "${alarm.label.ifBlank { "闹钟" }}已响铃 10 分钟，请手动关闭或检查是否错过。",
                )
                finishActive(promoteNext = true)
            }
        }
    }

    private suspend fun resolveSoundFile(alarm: AlarmEntity, useBuiltIn: Boolean): File? {
        if (!useBuiltIn && alarm.ringtoneId != null) {
            val ringtone = repository.getRingtone(alarm.ringtoneId)
            val file = ringtone?.privatePath?.let(::File)
            if (file != null && file.isFile && file.length() > 0L) return file
            return null
        }
        return runCatching { BuiltInToneGenerator.ensure(this) }.getOrNull()
    }

    private fun onPlaybackEvent(event: PlaybackEvent, alarm: AlarmEntity) {
        when (event) {
            is PlaybackEvent.Started -> {
                RingSessionStore.patch {
                    it.copy(
                        audioStatus = if (event.routedToHeadset) "正在耳机中播放" else "正在通过系统默认输出播放",
                        routeFailed = false,
                    )
                }
                publishRingingNotification()
            }

            is PlaybackEvent.RouteRejected,
            is PlaybackEvent.Disconnected,
            is PlaybackEvent.Interrupted,
            -> {
                val message = when (event) {
                    is PlaybackEvent.RouteRejected -> event.reason
                    is PlaybackEvent.Disconnected -> event.reason
                    is PlaybackEvent.Interrupted -> event.reason
                    else -> "播放已暂停"
                }
                RingSessionStore.patch {
                    it.copy(
                        audioStatus = "$message，震动继续",
                        routeFailed = true,
                    )
                }
                if (!alarm.vibrationEnabled) vibration.safetyPulse()
                publishRingingNotification()
            }

            is PlaybackEvent.DecodeError -> onDecodeFailure(event.message)
        }
    }

    private fun onDecodeFailure(message: String) {
        val alarm = activeAlarm ?: return
        if (!fallbackAttempted && alarm.ringtoneId != null) {
            fallbackAttempted = true
            RingSessionStore.patch {
                it.copy(
                    audioStatus = "$message，已改用内置提示音",
                    routeFailed = true,
                )
            }
            player.stop()
            serviceScope.launch {
                val fallbackOccurrenceId = activeOccurrence?.id ?: return@launch
                val maxVolume = settings.maxVolumePercent.first() / 100f
                if (activeOccurrence?.id != fallbackOccurrenceId) return@launch
                val fallback = resolveSoundFile(alarm, useBuiltIn = true) ?: return@launch
                player.play(fallback, routeController.connectedHeadset(), maxVolume) { event ->
                    onPlaybackEvent(event, alarm)
                }
            }
        } else {
            RingSessionStore.patch {
                it.copy(audioStatus = "$message，请检查铃声；震动继续", routeFailed = true)
            }
            if (!alarm.vibrationEnabled) vibration.safetyPulse()
            notifications.notifyStatus(
                "铃声不可用",
                "请在“${alarm.label.ifBlank { "闹钟" }}”中重新选择本地铃声。",
                activeOccurrence?.id,
            )
            publishRingingNotification()
        }
    }

    private suspend fun snooze(occurrenceId: String?) {
        val occurrence = activeOccurrence
        if (occurrence == null || occurrence.id != occurrenceId) return
        val alarm = activeAlarm ?: return
        if (!alarm.snoozeEnabled) return

        repository.setOccurrenceState(occurrence.id, OccurrenceState.SNOOZED)
        scheduler.scheduleSnooze(
            alarmId = occurrence.alarmId,
            triggerAtMillis = System.currentTimeMillis() + SNOOZE_MILLIS,
            parentOccurrenceId = occurrence.id,
        )
        finishActive(promoteNext = true)
    }

    private suspend fun dismiss(occurrenceId: String?) {
        val occurrence = activeOccurrence
        if (occurrence == null || occurrence.id != occurrenceId) {
            if (occurrenceId != null) {
                repository.setOccurrenceState(occurrenceId, OccurrenceState.DISMISSED)
            }
            stopIfIdle()
            return
        }
        repository.setOccurrenceState(occurrence.id, OccurrenceState.DISMISSED)
        finishActive(promoteNext = true)
    }

    private fun finishActive(promoteNext: Boolean) {
        timeoutJob?.cancel()
        timeoutJob = null
        player.stop()
        vibration.stop()
        activeOccurrence = null
        activeAlarm = null
        RingSessionStore.update(null)

        if (!promoteNext) {
            stopIfIdle()
            return
        }
        serviceScope.launch {
            val next = repository.claimNextQueued()
            if (next == null) {
                stopIfIdle()
                return@launch
            }
            val alarm = repository.getAlarm(next.alarmId)
            if (alarm == null) {
                repository.setOccurrenceState(next.id, OccurrenceState.MISSED)
                finishActive(promoteNext = true)
            } else {
                startRing(next, alarm)
            }
        }
    }

    private fun promoteNext() {
        finishActive(promoteNext = true)
    }

    private fun stopIfIdle() {
        if (activeOccurrence == null) {
            RingSessionStore.update(null)
            stopForeground(Service.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
            stopSelf()
        }
    }

    private fun ensureForeground() {
        if (foregroundStarted) return
        val notification = notifications.placeholder()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                AlarmNotifications.RING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(AlarmNotifications.RING_NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun publishRingingNotification() {
        val state = RingSessionStore.state.value ?: return
        val notification = notifications.ringing(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                AlarmNotifications.RING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(AlarmNotifications.RING_NOTIFICATION_ID, notification)
        }
        foregroundStarted = true
    }

    private fun updateQueuedNotification(alarm: AlarmEntity) {
        notifications.notifyStatus(
            "另一个闹钟正在等待",
            "${alarm.label.ifBlank { "闹钟" }}将在当前闹钟处理后提醒。",
        )
    }

    private fun mainHandler() = android.os.Handler(android.os.Looper.getMainLooper())

    companion object {
        const val ACTION_TRIGGER = "com.musicclock.alarm.action.TRIGGER"
        const val ACTION_SNOOZE = "com.musicclock.alarm.action.SNOOZE"
        const val ACTION_DISMISS = "com.musicclock.alarm.action.DISMISS"
        const val ACTION_RETRY_AUDIO = "com.musicclock.alarm.action.RETRY_AUDIO"
        const val EXTRA_OCCURRENCE_ID = "occurrence_id"

        const val SNOOZE_MILLIS = 5 * 60 * 1000L
        const val AUTO_STOP_MILLIS = 10 * 60 * 1000L

        fun triggerIntent(context: Context, occurrenceId: String): Intent =
            Intent(context, AlarmRingService::class.java).apply {
                action = ACTION_TRIGGER
                putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            }

        fun actionIntent(context: Context, action: String, occurrenceId: String): Intent =
            Intent(context, AlarmRingService::class.java).apply {
                this.action = action
                putExtra(EXTRA_OCCURRENCE_ID, occurrenceId)
            }
    }
}
