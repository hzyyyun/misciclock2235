package com.musicclock.alarm.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioRouting
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface PlaybackEvent {
    data class Started(val routedToHeadset: Boolean) : PlaybackEvent
    data class RouteRejected(val reason: String) : PlaybackEvent
    data class Disconnected(val reason: String) : PlaybackEvent
    data class Interrupted(val reason: String) : PlaybackEvent
    data class DecodeError(val message: String) : PlaybackEvent
    data class TimedOut(val reason: String) : PlaybackEvent
}

class HeadsetSafePlayer(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val routeController = AudioRouteController(context)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var routingListener: AudioRouting.OnRoutingChangedListener? = null
    private var eventListener: ((PlaybackEvent) -> Unit)? = null
    private var preferredHeadsetId: Int? = null
    private var targetGain = 0.4f
    private var stopped = true
    private var sessionId = 0L
    private var routeConfirmed = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (stopped) return@OnAudioFocusChangeListener
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                stop(releaseFocus = false)
                eventListener?.invoke(PlaybackEvent.Interrupted("音频焦点被其他应用占用"))
            }
        }
    }

    suspend fun play(
        file: File,
        headset: AudioDeviceInfo?,
        maxGain: Float,
        maxDurationMillis: Long? = null,
        onEvent: (PlaybackEvent) -> Unit,
    ) = withContext(Dispatchers.Main.immediate) {
        stop()
        eventListener = onEvent
        targetGain = maxGain.coerceIn(0.05f, 0.8f)
        preferredHeadsetId = headset?.id
        stopped = false
        routeConfirmed = false
        val currentSession = ++sessionId

        var pendingPlayer: MediaPlayer? = null
        val mediaPlayer = try {
            withContext(Dispatchers.IO) {
                MediaPlayer().also { pendingPlayer = it }.apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(
                                if (headset != null) {
                                    AudioAttributes.USAGE_MEDIA
                                } else {
                                    AudioAttributes.USAGE_ALARM
                                },
                            )
                            .setContentType(
                                if (headset != null) {
                                    AudioAttributes.CONTENT_TYPE_MUSIC
                                } else {
                                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                                },
                            )
                            .build(),
                    )
                    setDataSource(file.absolutePath)
                    isLooping = true
                    prepare()
                }
            }
        } catch (error: Exception) {
            runCatching { pendingPlayer?.release() }
            stopped = true
            eventListener?.invoke(PlaybackEvent.DecodeError(error.message ?: "无法读取铃声文件"))
            return@withContext
        }

        if (stopped || currentSession != sessionId) {
            mediaPlayer.release()
            return@withContext
        }

        player = mediaPlayer
        mediaPlayer.setOnErrorListener { _, what, extra ->
            if (!stopped) {
                stop()
                eventListener?.invoke(PlaybackEvent.DecodeError("无法解码铃声（$what/$extra）"))
            }
            true
        }

        if (headset != null) {
            mediaPlayer.setVolume(0f, 0f)
            if (!mediaPlayer.setPreferredDevice(headset)) {
                stop()
                eventListener?.invoke(PlaybackEvent.RouteRejected("系统拒绝耳机音频路由"))
                return@withContext
            }
        } else {
            mediaPlayer.setVolume(targetGain, targetGain)
        }

        val focusResult = requestFocus(headset != null)
        if (focusResult != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            stop(releaseFocus = false)
            eventListener?.invoke(PlaybackEvent.Interrupted("系统未授予音频焦点"))
            return@withContext
        }

        routingListener = AudioRouting.OnRoutingChangedListener { routing ->
            if (!stopped && currentSession == sessionId) {
                if (routeConfirmed) {
                    verifyCurrentRoute(routing.routedDevice)
                }
            }
        }.also { listener ->
            mediaPlayer.addOnRoutingChangedListener(listener, mainHandler)
        }

        try {
            mediaPlayer.start()
        } catch (error: Exception) {
            stop()
            eventListener?.invoke(PlaybackEvent.DecodeError(error.message ?: "播放失败"))
            return@withContext
        }

        maxDurationMillis?.takeIf { it > 0L }?.let { duration ->
            val expectedSession = currentSession
            val stopAtElapsed = SystemClock.elapsedRealtime() + duration
            mainHandler.postAtTime(
                {
                    if (!stopped && expectedSession == sessionId) {
                        stop()
                        eventListener?.invoke(PlaybackEvent.TimedOut("响铃已达到时间上限"))
                    }
                },
                stopAtElapsed,
            )
        }

        if (headset != null) {
            mainHandler.postDelayed({
                if (!stopped && currentSession == sessionId) {
                    val routed = mediaPlayer.routedDevice
                    if (routed != null && routed.id == headset.id && routeController.isHeadset(routed)) {
                        eventListener?.invoke(PlaybackEvent.Started(true))
                        routeConfirmed = true
                        rampVolume(mediaPlayer, currentSession)
                        pollRoute(mediaPlayer, currentSession)
                    } else {
                        stop()
                        eventListener?.invoke(PlaybackEvent.RouteRejected("无法确认耳机输出，已保持静音"))
                    }
                }
            }, ROUTE_CONFIRM_DELAY_MS)
        } else {
            eventListener?.invoke(PlaybackEvent.Started(false))
        }
    }

    fun verifyRouteAfterDeviceChange() {
        if (stopped) return
        val expectedId = preferredHeadsetId ?: return
        if (!routeController.isDeviceAvailable(expectedId)) {
            stop()
            eventListener?.invoke(PlaybackEvent.Disconnected("耳机已断开，播放已暂停"))
            return
        }
        if (!routeConfirmed) return
        val routed = player?.routedDevice
        if (routed == null || routed.id != expectedId || !routeController.isHeadset(routed)) {
            stop()
            eventListener?.invoke(PlaybackEvent.Disconnected("耳机已断开，播放已暂停"))
        }
    }

    fun stop(releaseFocus: Boolean = true) {
        stopped = true
        routeConfirmed = false
        sessionId++
        mainHandler.removeCallbacksAndMessages(null)
        routingListener?.let { listener ->
            runCatching { player?.removeOnRoutingChangedListener(listener) }
        }
        routingListener = null
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        if (releaseFocus) {
            focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        }
        focusRequest = null
    }

    private fun verifyCurrentRoute(routed: AudioDeviceInfo?) {
        val expected = preferredHeadsetId ?: return
        if (routed == null || routed.id != expected || !routeController.isHeadset(routed)) {
            stop()
            eventListener?.invoke(PlaybackEvent.Disconnected("音频路由已离开耳机，播放已暂停"))
        }
    }

    private fun rampVolume(mediaPlayer: MediaPlayer, currentSession: Long) {
        val steps = 12
        for (step in 1..steps) {
            mainHandler.postDelayed({
                if (!stopped && currentSession == sessionId && player === mediaPlayer) {
                    val gain = targetGain * step / steps
                    mediaPlayer.setVolume(gain, gain)
                }
            }, step * 25L)
        }
    }

    private fun pollRoute(mediaPlayer: MediaPlayer, currentSession: Long) {
        mainHandler.postDelayed({
            if (!stopped && currentSession == sessionId && player === mediaPlayer) {
                verifyCurrentRoute(mediaPlayer.routedDevice)
                if (!stopped) pollRoute(mediaPlayer, currentSession)
            }
        }, ROUTE_POLL_INTERVAL_MS)
    }

    private fun requestFocus(useMediaUsage: Boolean): Int {
        val attributes = AudioAttributes.Builder()
            .setUsage(
                if (useMediaUsage) AudioAttributes.USAGE_MEDIA else AudioAttributes.USAGE_ALARM,
            )
            .setContentType(
                if (useMediaUsage) {
                    AudioAttributes.CONTENT_TYPE_MUSIC
                } else {
                    AudioAttributes.CONTENT_TYPE_SONIFICATION
                },
            )
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .setAcceptsDelayedFocusGain(false)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request)
    }

    companion object {
        private const val ROUTE_CONFIRM_DELAY_MS = 700L
        private const val ROUTE_POLL_INTERVAL_MS = 250L
    }
}

