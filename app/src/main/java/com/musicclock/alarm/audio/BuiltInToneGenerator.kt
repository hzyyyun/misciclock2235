package com.musicclock.alarm.audio

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

object BuiltInToneGenerator {
    private const val SAMPLE_RATE = 44_100
    private const val SECONDS = 2.4

    fun ensure(context: Context): File {
        val ringtoneDir = File(context.filesDir, "ringtones").apply { mkdirs() }
        val target = File(ringtoneDir, "builtin_chime.wav")
        if (target.exists() && target.length() > 44L) return target

        val temp = File(ringtoneDir, "builtin_chime.tmp")
        temp.writeBytes(createWave())
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        return target
    }

    private fun createWave(): ByteArray {
        val sampleCount = (SAMPLE_RATE * SECONDS).toInt()
        val pcm = ByteArrayOutputStream(sampleCount * 2 + 44)
        val dataSize = sampleCount * 2

        pcm.writeAscii("RIFF")
        pcm.writeLeInt(36 + dataSize)
        pcm.writeAscii("WAVE")
        pcm.writeAscii("fmt ")
        pcm.writeLeInt(16)
        pcm.writeLeShort(1)
        pcm.writeLeShort(1)
        pcm.writeLeInt(SAMPLE_RATE)
        pcm.writeLeInt(SAMPLE_RATE * 2)
        pcm.writeLeShort(2)
        pcm.writeLeShort(16)
        pcm.writeAscii("data")
        pcm.writeLeInt(dataSize)

        val notes = listOf(
            Note(start = 0.00, duration = 0.42, frequency = 880.0),
            Note(start = 0.48, duration = 0.42, frequency = 659.25),
            Note(start = 0.96, duration = 0.58, frequency = 783.99),
        )
        for (index in 0 until sampleCount) {
            val time = index.toDouble() / SAMPLE_RATE
            var value = 0.0
            notes.forEach { note ->
                val local = time - note.start
                if (local in 0.0..note.duration) {
                    val envelope = exp(-4.5 * local) * (1.0 - local / note.duration)
                    value += sin(2.0 * PI * note.frequency * local) * envelope * 0.28
                }
            }
            val sample = (value.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
            pcm.writeLeShort(sample)
        }
        return pcm.toByteArray()
    }

    private data class Note(val start: Double, val duration: Double, val frequency: Double)

    private fun ByteArrayOutputStream.writeAscii(value: String) {
        write(value.toByteArray(Charsets.US_ASCII))
    }

    private fun ByteArrayOutputStream.writeLeInt(value: Int) {
        write(value and 0xff)
        write(value shr 8 and 0xff)
        write(value shr 16 and 0xff)
        write(value shr 24 and 0xff)
    }

    private fun ByteArrayOutputStream.writeLeShort(value: Int) {
        write(value and 0xff)
        write(value shr 8 and 0xff)
    }
}

