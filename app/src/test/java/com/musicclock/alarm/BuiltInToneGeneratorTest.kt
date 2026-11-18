package com.musicclock.alarm

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.musicclock.alarm.audio.BuiltInToneGenerator
import java.io.File
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BuiltInToneGeneratorTest {
    @Test
    fun generatedFallbackIsAValidWaveContainer() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = BuiltInToneGenerator.ensure(context)
        val header = file.inputStream().use { it.readNBytes(12) }

        assertThat(file.length()).isGreaterThan(44L)
        assertThat(String(header.copyOfRange(0, 4), Charsets.US_ASCII)).isEqualTo("RIFF")
        assertThat(String(header.copyOfRange(8, 12), Charsets.US_ASCII)).isEqualTo("WAVE")
    }
}
