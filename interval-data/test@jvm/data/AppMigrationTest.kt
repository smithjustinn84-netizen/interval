package dev.justinsmith.interval.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * A database left at an earlier version opens at the current one with its rows intact. The old schemas are
 * written out here as the builds of the time left them, so the test keeps working as later versions come.
 */
class AppMigrationTest {
    private val file = File.createTempFile("interval-migration", ".db").also { it.delete() }

    @AfterTest
    fun cleanUp() {
        File(file.parentFile, file.name + "-wal").delete()
        File(file.parentFile, file.name + "-shm").delete()
        file.delete()
    }

    /** Version 2 as [tables] lay it out, with a saved preset and a finished workout. */
    private fun writeVersion2(tables: List<String>) {
        BundledSQLiteDriver().open(file.absolutePath).apply {
            tables.forEach { execSQL(it) }
            execSQL(
                "INSERT INTO preset (id, name, prep_seconds, work_seconds, rest_seconds, rounds) " +
                    "VALUES (1, 'Mine', 10, 30, 15, 8)",
            )
            execSQL(
                "INSERT INTO history (id, finished_at, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "elapsed_seconds, ended_early, work_active_seconds) VALUES (1, 1000, 10, 30, 15, 8, 370, 0, 240)",
            )
            execSQL("PRAGMA user_version = 2")
            close()
        }
    }

    private fun open(): AppDatabase = Room.databaseBuilder<AppDatabase>(name = file.absolutePath).buildAppDatabase()

    private fun opensWithRowsKept() = runBlocking {
        val database = open()
        try {
            val preset = database.presetDao().all().single()
            assertEquals(PresetEntity(1, "Mine", 10, 30, 15, 8, activity = null), preset)
            val workout = database.historyDao().all().single()
            assertEquals(1000L, workout.finishedAtEpochMillis)
            assertEquals(240, workout.workActiveSeconds)
            assertNull(workout.activity)
            val added = PresetEntity(0, "Rowing", 5, 60, 30, 4, activity = "Rowing")
            val id = database.presetDao().insert(added)
            assertEquals(added.copy(id = id), database.presetDao().all().last())
        } finally {
            database.close()
        }
    }

    @Test
    fun `version 2 without activity opens at the current version with its rows kept`() {
        writeVersion2(VERSION_2_BEFORE_ACTIVITY)
        opensWithRowsKept()
    }

    @Test
    fun `version 2 as its schema has it opens at the current version with its rows kept`() {
        writeVersion2(VERSION_2)
        opensWithRowsKept()
    }

    private companion object {
        /**
         * Version 2 as the first build of 18 October 2026 wrote it (`db61c16`): `work_active_seconds` and no
         * `activity`, which the next build added without a new version. Copied from such a database.
         */
        val VERSION_2_BEFORE_ACTIVITY = listOf(
            "CREATE TABLE `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`finished_at` INTEGER NOT NULL, `prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, " +
                "`rest_seconds` INTEGER NOT NULL, " +
                "`rounds` INTEGER NOT NULL, `elapsed_seconds` INTEGER NOT NULL, `ended_early` INTEGER NOT NULL, " +
                "`work_active_seconds` INTEGER DEFAULT NULL)",
            "CREATE INDEX `index_history_finished_at` ON `history` (`finished_at`)",
            "CREATE TABLE `preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, `rest_seconds` INTEGER NOT NULL, " +
                "`rounds` INTEGER NOT NULL)",
        )

        /** Version 2 as `schemas/…/2.json` has it. */
        val VERSION_2 = listOf(
            "CREATE TABLE `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`finished_at` INTEGER NOT NULL, `prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, " +
                "`rest_seconds` INTEGER NOT NULL, " +
                "`rounds` INTEGER NOT NULL, `elapsed_seconds` INTEGER NOT NULL, `ended_early` INTEGER NOT NULL, " +
                "`work_active_seconds` INTEGER, `activity` TEXT)",
            "CREATE INDEX `index_history_finished_at` ON `history` (`finished_at`)",
            "CREATE TABLE `preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, `rest_seconds` INTEGER NOT NULL, " +
                "`rounds` INTEGER NOT NULL, `activity` TEXT)",
        )
    }
}
