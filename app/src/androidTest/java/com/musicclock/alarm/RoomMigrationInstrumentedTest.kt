package com.musicclock.alarm

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.musicclock.alarm.data.AppDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationInstrumentedTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migration1To2PreservesOccurrenceAndAddsRingTiming() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO alarms (
                    id, label, hour, minute, scheduleType, weekdaysMask,
                    oneShotAtMillis, enabled, ringtoneId, vibrationEnabled, snoozeEnabled
                ) VALUES (1, 'migration', 7, 0, 'ONE_SHOT', 0, NULL, 1, NULL, 1, 1)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO occurrences (
                    id, alarmId, triggerAtMillis, kind, state,
                    parentOccurrenceId, createdAtMillis
                ) VALUES ('occurrence-1', 1, 1000, 'SCHEDULED', 'SCHEDULED', NULL, 500)
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            AppDatabase.MIGRATION_1_2,
        )

        migrated.query(
            "SELECT ringStartedAtMillis, autoStopAtMillis FROM occurrences WHERE id = 'occurrence-1'",
        ).use { cursor ->
            assertThat(cursor.moveToFirst()).isTrue()
            assertThat(cursor.isNull(0)).isTrue()
            assertThat(cursor.isNull(1)).isTrue()
        }
        migrated.close()
    }

    companion object {
        private const val TEST_DATABASE = "migration-test"
    }
}
