package com.musicclock.alarm

import android.media.AudioDeviceInfo
import com.google.common.truth.Truth.assertThat
import com.musicclock.alarm.audio.AudioOutputSnapshot
import com.musicclock.alarm.audio.HeadsetRoutePolicy
import org.junit.Test

class HeadsetRoutePolicyTest {
    @Test
    fun selectsOnlyHeadsetAndIgnoresSpeakerOrder() {
        val target = HeadsetRoutePolicy.selectTarget(
            listOf(
                device(1, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "speaker"),
                device(2, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "wired"),
            ),
        )

        assertThat(target?.deviceId).isEqualTo(2)
    }

    @Test
    fun preferredConnectedHeadsetWins() {
        val target = HeadsetRoutePolicy.selectTarget(
            listOf(
                device(3, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "a2dp"),
                device(4, AudioDeviceInfo.TYPE_USB_HEADSET, "usb", preferred = true),
            ),
        )

        assertThat(target?.deviceId).isEqualTo(4)
    }

    @Test
    fun returnsNullWithoutHeadset() {
        val target = HeadsetRoutePolicy.selectTarget(
            listOf(
                device(1, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "speaker"),
                device(2, AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, "earpiece"),
            ),
        )

        assertThat(target).isNull()
    }

    @Test
    fun recognizesBluetoothScoAndHearingAid() {
        assertThat(HeadsetRoutePolicy.isHeadsetType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO)).isTrue()
        assertThat(HeadsetRoutePolicy.isHeadsetType(AudioDeviceInfo.TYPE_HEARING_AID)).isTrue()
    }

    private fun device(
        id: Int,
        type: Int,
        label: String,
        preferred: Boolean = false,
    ) = AudioOutputSnapshot(id, type, label, preferred)
}
