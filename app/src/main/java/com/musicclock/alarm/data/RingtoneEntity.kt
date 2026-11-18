package com.musicclock.alarm.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ringtones")
data class RingtoneEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String,
    val privatePath: String,
    val mimeType: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val importedAtMillis: Long,
)

