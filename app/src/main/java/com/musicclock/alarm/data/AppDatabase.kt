package com.musicclock.alarm.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AlarmEntity::class, RingtoneEntity::class, OccurrenceEntity::class],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "musicclock.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE occurrences ADD COLUMN ringStartedAtMillis INTEGER",
                )
                database.execSQL(
                    "ALTER TABLE occurrences ADD COLUMN autoStopAtMillis INTEGER",
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN ringStopMode TEXT NOT NULL DEFAULT 'DURATION'",
                )
                database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN ringDurationSeconds INTEGER NOT NULL DEFAULT 300",
                )
                database.execSQL(
                    "ALTER TABLE alarms ADD COLUMN ringLoopCount INTEGER NOT NULL DEFAULT 1",
                )
            }
        }
    }
}

