package com.musicclock.alarm.audio

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class VibrationController(context: Context) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    fun startAlarmPattern() {
        if (!vibrator.hasVibrator()) return
        vibrator.cancel()
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 800, 700),
                0,
            ),
        )
    }

    fun safetyPulse() {
        if (!vibrator.hasVibrator()) return
        vibrator.cancel()
        vibrator.vibrate(
            VibrationEffect.createWaveform(
                longArrayOf(0, 250, 180, 250, 180, 400),
                -1,
            ),
        )
    }

    fun stop() {
        vibrator.cancel()
    }
}

