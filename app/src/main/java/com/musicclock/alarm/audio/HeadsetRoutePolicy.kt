package com.musicclock.alarm.audio

import android.media.AudioDeviceInfo
import android.os.Build

data class AudioOutputSnapshot(
    val deviceId: Int,
    val type: Int,
    val label: String,
    val preferred: Boolean = false,
)

data class HeadsetRouteTarget(
    val deviceId: Int,
    val type: Int,
    val label: String,
)

object HeadsetRoutePolicy {
    fun isHeadsetType(type: Int): Boolean = when (type) {
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_HEARING_AID,
        -> true
        else -> Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            type == AudioDeviceInfo.TYPE_BLE_HEADSET
    }

    fun selectTarget(outputs: List<AudioOutputSnapshot>): HeadsetRouteTarget? {
        val selected = outputs.firstOrNull { it.preferred && isHeadsetType(it.type) }
            ?: outputs.firstOrNull { isHeadsetType(it.type) }
            ?: return null
        return HeadsetRouteTarget(
            deviceId = selected.deviceId,
            type = selected.type,
            label = selected.label,
        )
    }
}
