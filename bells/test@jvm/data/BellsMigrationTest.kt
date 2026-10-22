package dev.justinsmith.bells.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A database left at an earlier version opens at the current one with its rows intact. The old schema is
 * written out here as its `schemas/` JSON has it, so the test keeps working as later versions come.
 */
class BellsMigrationTest {
    private val file = File.createTempFile("bells-migration", ".db").also { it.delete() }

    @AfterTest
    fun cleanUp() {
        File(file.parentFile, file.name + "-wal").delete()
        File(file.parentFile, file.name + "-shm").delete()
        file.delete()
    }

    /** Version 6, with a finished session of one press set and a program the user made. */
    private fun writeVersion6() {
        BundledSQLiteDriver().open(file.absolutePath).apply {
            VERSION_6.forEach { execSQL(it) }
            execSQL("INSERT INTO session VALUES ('s1', 0, 60000, 'Press', 60000, 0, NULL)")
            execSQL("INSERT INTO lift_set VALUES (1, 's1', 'Press', 16.0, 5, 'Left', 10, 0)")
            execSQL("INSERT INTO custom_program VALUES ('p1', 'Mine', 3, 4, '[]', 0)")
            execSQL("PRAGMA user_version = 6")
            close()
        }
    }

    @Test
    fun `version 6 opens at version 7 with its sets as they were`() = runBlocking {
        writeVersion6()
        val database = Room.databaseBuilder<BellsDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        try {
            assertEquals(
                listOf(SetEntity(1, "s1", "Press", 16.0, 5, "Left", 10)),
                database.sessionDao().allSets(),
            )
            assertEquals("Mine", database.customProgramDao().observeAll().first().single().name)
            val added = CustomExerciseEntity("e1", "Copenhagen plank", "Bodyweight", "Time", true, "Core", "", false, 0)
            database.customExerciseDao().upsert(added)
            assertEquals(listOf(added), database.customExerciseDao().observeAll().first())
        } finally {
            database.close()
        }
    }

    private companion object {
        /** `schemas/…/6.json`: its tables and indices, then its identity, which Room checks on opening. */
        val VERSION_6 = listOf(
            "CREATE TABLE IF NOT EXISTS `session` (`uid` TEXT NOT NULL, `started_at` INTEGER NOT NULL, " +
                "`finished_at` INTEGER, `title` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, " +
                "`deleted` INTEGER NOT NULL, `program_id` TEXT, PRIMARY KEY(`uid`))",
            "CREATE INDEX IF NOT EXISTS `index_session_started_at` ON `session` (`started_at`)",
            "CREATE TABLE IF NOT EXISTS `lift_set` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`session_uid` TEXT NOT NULL, `exercise` TEXT NOT NULL, `bell_kg` REAL NOT NULL, " +
                "`reps` INTEGER NOT NULL, `side` TEXT, `logged_at` INTEGER NOT NULL, " +
                "`doubled` INTEGER NOT NULL DEFAULT 0)",
            "CREATE INDEX IF NOT EXISTS `index_lift_set_session_uid` ON `lift_set` (`session_uid`)",
            "CREATE TABLE IF NOT EXISTS `bell` (`kg` REAL NOT NULL, PRIMARY KEY(`kg`))",
            "CREATE TABLE IF NOT EXISTS `following` (`id` INTEGER NOT NULL, `program_id` TEXT NOT NULL, " +
                "`started_at` INTEGER NOT NULL, PRIMARY KEY(`id`))",
            "CREATE TABLE IF NOT EXISTS `custom_program` (`uid` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`days_per_week` INTEGER NOT NULL, `weeks` INTEGER NOT NULL, `days` TEXT NOT NULL, " +
                "`updated_at` INTEGER NOT NULL, PRIMARY KEY(`uid`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) " +
                "VALUES(42, 'c6fdc105cf3d5b7b4559f8af9607d990')",
        )
    }
}
