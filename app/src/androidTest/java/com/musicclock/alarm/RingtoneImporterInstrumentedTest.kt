package com.musicclock.alarm

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.musicclock.alarm.audio.BuiltInToneGenerator
import com.musicclock.alarm.audio.RingtoneImportResult
import com.musicclock.alarm.audio.RingtoneImporter
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RingtoneImporterInstrumentedTest {
    @Test
    fun copiesAndDecodesWaveFromContentProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val importer = RingtoneImporter(context)
        val source = File(context.cacheDir, "acceptance.wav")
        BuiltInToneGenerator.ensure(context).copyTo(source, overwrite = true)
        val uri = Uri.fromFile(source)

        val result = runBlocking { importer.import(uri) }

        assertThat(result).isInstanceOf(RingtoneImportResult.Success::class.java)
        val ringtone = (result as RingtoneImportResult.Success).ringtone
        val copied = File(ringtone.privatePath)
        assertThat(copied.isFile).isTrue()
        assertThat(copied.length()).isEqualTo(ringtone.sizeBytes)
        val header = copied.inputStream().use { it.readNBytes(12) }
        assertThat(String(header.copyOfRange(0, 4), Charsets.US_ASCII)).isEqualTo("RIFF")
        assertThat(String(header.copyOfRange(8, 12), Charsets.US_ASCII)).isEqualTo("WAVE")
        copied.delete()
        source.delete()
    }
}
