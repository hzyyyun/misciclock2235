package com.musicclock.alarm.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler

data class HeadsetTarget(
    val deviceId: Int,
    val label: String,
    val type: Int,
)

class AudioRouteController(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun connectedHeadset(): AudioDeviceInfo? {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val preferred: List<AudioDeviceInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                audioManager.getAudioDevicesForAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
            }.getOrDefault(emptyList<AudioDeviceInfo>())
        } else {
            emptyList()
        }
        val preferredIds = preferred.mapTo(mutableSetOf()) { it.id }
        val target = HeadsetRoutePolicy.selectTarget(
            outputs.map { device ->
                AudioOutputSnapshot(
                    deviceId = device.id,
                    type = device.type,
                    label = device.productName.toString().ifBlank {
                        defaultHeadsetLabel(device.type)
                    },
                    preferred = device.id in preferredIds,
                )
            },
        ) ?: return null
        return outputs.firstOrNull { it.id == target.deviceId }
    }

    fun currentHeadsetTarget(): HeadsetTarget? = connectedHeadset()?.let { device ->
        HeadsetTarget(
            deviceId = device.id,
            label = device.productName.toString().ifBlank { defaultHeadsetLabel(device.type) },
            type = device.type,
        )
    }

    fun isHeadset(device: AudioDeviceInfo): Boolean = HeadsetRoutePolicy.isHeadsetType(device.type)

    fun isDeviceAvailable(deviceId: Int): Boolean =
        audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.id == deviceId }

    fun registerDeviceCallback(handler: Handler, onChange: () -> Unit): AudioDeviceCallback {
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = onChange()

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = onChange()
        }
        audioManager.registerAudioDeviceCallback(callback, handler)
        return callback
    }

    fun unregisterDeviceCallback(callback: AudioDeviceCallback) {
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    private fun defaultHeadsetLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙耳机"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB 耳机"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        -> "有线耳机"
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            type == AudioDeviceInfo.TYPE_BLE_HEADSET
        ) {
            "蓝牙 LE 耳机"
        } else {
            "耳机"
        }
    }
}

