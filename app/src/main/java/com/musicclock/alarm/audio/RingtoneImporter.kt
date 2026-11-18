package com.musicclock.alarm.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import com.musicclock.alarm.data.RingtoneEntity
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface RingtoneImportResult {
    data class Success(val ringtone: RingtoneEntity) : RingtoneImportResult
    data class Failure(val message: String) : RingtoneImportResult
}

class RingtoneImporter(private val context: Context) {
    suspend fun import(uri: Uri): RingtoneImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val displayName = queryDisplayName(uri) ?: "本地音频"
        val extension = displayName.substringAfterLast('.', "").lowercase()
        val mime = resolver.getType(uri)

        if (extension !in SUPPORTED_EXTENSIONS && mime?.startsWith("audio/") != true) {
            return@withContext RingtoneImportResult.Failure(
                "不支持此文件类型。请选择 mp3、wav、m4a、aac、ogg 或 flac。",
            )
        }

        val importDir = File(context.filesDir, "ringtones").apply { mkdirs() }
        val safeExtension = extension.takeIf { it in SUPPORTED_EXTENSIONS } ?: "audio"
        val destination = File(importDir, "ringtone_${UUID.randomUUID()}.$safeExtension")

        try {
            resolver.openInputStream(uri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext RingtoneImportResult.Failure("无法读取所选文件")

            if (!destination.isFile || destination.length() <= 0L) {
                destination.delete()
                return@withContext RingtoneImportResult.Failure("所选文件为空")
            }

            val duration = validateAndGetDuration(destination)
                ?: run {
                    destination.delete()
                    return@withContext RingtoneImportResult.Failure(
                        "当前设备无法解码此音频，请换一个 mp3、wav、m4a、aac、ogg 或 flac 文件。",
                    )
                }

            RingtoneImportResult.Success(
                RingtoneEntity(
                    displayName = displayName,
                    privatePath = destination.absolutePath,
                    mimeType = mime,
                    durationMs = duration,
                    sizeBytes = destination.length(),
                    importedAtMillis = System.currentTimeMillis(),
                ),
            )
        } catch (error: SecurityException) {
            destination.delete()
            RingtoneImportResult.Failure("文件读取授权已失效，请重新选择。")
        } catch (error: Exception) {
            destination.delete()
            RingtoneImportResult.Failure(error.message ?: "导入音频失败")
        }
    }

    fun delete(ringtone: RingtoneEntity) {
        runCatching { File(ringtone.privatePath).delete() }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun validateAndGetDuration(file: File): Long? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            if (duration != null && duration > 0L) return duration
        } catch (_: Exception) {
            // Try MediaPlayer below because platform decoders differ by OEM.
        } finally {
            runCatching { retriever.release() }
        }

        val player = MediaPlayer()
        return try {
            player.setDataSource(file.absolutePath)
            player.prepare()
            player.duration.toLong().takeIf { it > 0L }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { player.release() }
        }
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("mp3", "wav", "m4a", "aac", "ogg", "flac")
    }
}

