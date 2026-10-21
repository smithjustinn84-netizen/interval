package dev.justinsmith.interval.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.justinsmith.interval.fakes.saveCurrentAsPreset
import dev.justinsmith.interval.platform.DATABASE_NAME
import dev.justinsmith.interval.platform.PREFERENCES_NAME
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.stepped
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The repository over real files: a DataStore and a Room database in a temp directory, with writes on
 * Dispatchers.IO as in the app. The common tests' fakes finish every write before a call returns; these
 * check the app still behaves when writes are genuinely asynchronous.
 */
class StorageTest {
    private val directory: File = Files.createTempDirectory("interval-storage").toFile()

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `a change is readable straight away, before it reaches disk`() = withApp { repository ->
        repository.updateConfig { it.copy(workSeconds = 50) }
        assertEquals(50, repository.config.value.workSeconds)
    }

    @Test
    fun `everything survives a restart`() {
        val config = IntervalConfig(5, 45, 15, rounds = 12, activity = Activity.Kettlebell)
        withApp { repository ->
            repository.updateConfig { config }
            repository.updateCueSettings { it.copy(halfway = false) }
            repository.updateAppSettings { AppSettings(theme = ThemeMode.Light, keepScreenOn = false) }
            repository.saveCurrentAsPreset("Long")
            repository.recordWorkout(config, elapsedSeconds = 300, endedEarly = true)
            settled(repository) { it.customPresets.value.size == 1 && it.history.value.size == 1 }
            settledOnDisk { it.toConfig() == config && !it.toCueSettings().halfway && !it.toAppSettings().keepScreenOn }
        }
        withApp { repository ->
            // Ready means every value is the stored one, not a default.
            assertTrue(repository.ready.value)
            assertEquals(config, repository.config.value)
            assertEquals(CueSettings(halfway = false), repository.cueSettings.value)
            assertEquals(AppSettings(theme = ThemeMode.Light, keepScreenOn = false), repository.appSettings.value)
            assertEquals(Preset("Long", config, id = 1), repository.customPresets.value.single())
            assertEquals(
                HistoryEntry(NOW, config, elapsedSeconds = 300, endedEarly = true, id = 1),
                repository.history.value.single(),
            )
        }
    }

    @Test
    fun `after many quick changes the last one is what's saved`() {
        withApp { repository ->
            repeat(40) { repository.updateConfig { it.stepped(ConfigField.Rounds, +1) } }
            val expected = IntervalConfig().rounds + 40
            assertEquals(expected, repository.config.value.rounds)
            settledOnDisk { it.toConfig().rounds == expected }
        }
        withApp { repository -> assertEquals(IntervalConfig().rounds + 40, repository.config.value.rounds) }
    }

    @Test
    fun `a theme this build doesn't know reads as the default`() {
        withApp { _ -> settings.edit { it[stringPreferencesKey("theme")] = "Sepia" } }
        withApp { repository -> assertEquals(AppSettings().theme, repository.appSettings.value.theme) }
    }

    @Test
    fun `a version 1 database opens with its history and presets kept`() {
        BundledSQLiteDriver().open(directory.resolve(DATABASE_NAME).absolutePath).use { connection ->
            V1_SCHEMA.forEach(connection::execSQL)
            connection.execSQL(
                "INSERT INTO history (finished_at, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "elapsed_seconds, ended_early) VALUES ($NOW, 10, 20, 10, 8, 250, 0)",
            )
            connection.execSQL(
                "INSERT INTO preset (name, prep_seconds, work_seconds, rest_seconds, rounds) " +
                    "VALUES ('Mine', 10, 40, 20, 6)",
            )
            connection.execSQL("PRAGMA user_version = 1")
        }
        withApp { repository ->
            val entry = repository.history.value.single()
            assertEquals(IntervalConfig(10, 20, 10, rounds = 8), entry.config)
            assertEquals(null, entry.workActiveSeconds)
            assertEquals(
                Preset("Mine", IntervalConfig(10, 40, 20, rounds = 6), id = 1),
                repository.customPresets.value.single(),
            )
        }
    }

    @Test
    fun `a version 2 database opens with its history kept and no plans`() {
        BundledSQLiteDriver().open(directory.resolve(DATABASE_NAME).absolutePath).use { connection ->
            V2_SCHEMA.forEach(connection::execSQL)
            connection.execSQL(
                "INSERT INTO history (finished_at, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "elapsed_seconds, ended_early, work_active_seconds, activity) " +
                    "VALUES ($NOW, 10, 20, 10, 8, 250, 0, 160, 'Rower')",
            )
            connection.execSQL("PRAGMA user_version = 2")
        }
        withApp { repository ->
            val entry = repository.history.value.single()
            assertEquals(IntervalConfig(10, 20, 10, rounds = 8, activity = Activity.Rower), entry.config)
            assertEquals(160, entry.workActiveSeconds)
            assertEquals(emptyList(), repository.planned.value)
        }
    }

    @Test
    fun `a version 3 database keeps its workouts and gives each a uid of its own`() {
        BundledSQLiteDriver().open(directory.resolve(DATABASE_NAME).absolutePath).use { connection ->
            V3_SCHEMA.forEach(connection::execSQL)
            listOf(250, 300, 350).forEach { elapsed ->
                connection.execSQL(
                    "INSERT INTO history (finished_at, prep_seconds, work_seconds, rest_seconds, rounds, " +
                        "elapsed_seconds, ended_early, work_active_seconds, activity) " +
                        "VALUES ($NOW + $elapsed, 10, 20, 10, 8, $elapsed, 0, 160, 'Rower')",
                )
            }
            connection.execSQL("PRAGMA user_version = 3")
        }
        withApp { repository ->
            assertEquals(listOf(350, 300, 250), repository.history.value.map { it.elapsedSeconds })
            val rows = database.historyDao().all()
            assertEquals(3, rows.map { it.uid }.toSet().size)
            assertTrue(rows.all { it.uid.length == 32 && !it.deleted && it.updatedAtEpochMillis == 0L })
            // The uid index is in place: a new workout still records beside the old ones.
            repository.recordWorkout(IntervalConfig(), elapsedSeconds = 1)
            settled(repository) { it.history.value.size == 4 }
        }
    }

    @Test
    fun `deleted workouts stay on disk as tombstones`() {
        withApp { repository ->
            repeat(3) { repository.recordWorkout(IntervalConfig(), elapsedSeconds = it + 1) }
            settled(repository) { it.history.value.size == 3 }
            clock = 2_000
            repository.deleteWorkout(requireNotNull(repository.history.value.first().id))
            settled(repository) { it.history.value.size == 2 }
            clock = 3_000
            repository.clearHistory()
            settled(repository) { it.history.value.isEmpty() }
        }
        withApp { repository ->
            assertEquals(emptyList(), repository.history.value)
            val rows = database.historyDao().all()
            assertTrue(rows.all { it.deleted })
            assertEquals(listOf(3_000L, 3_000L, 2_000L), rows.map { it.updatedAtEpochMillis })
        }
    }

    @Test
    fun `plans and a deleted workout survive a restart`() {
        val tabata = Preset("Tabata", IntervalConfig(10, 20, 10, rounds = 8))
        withApp { repository ->
            repository.planWeekly(tabata, setOf(1, 3))
            repository.planOnce(tabata, epochDay = 20_744)
            repository.recordWorkout(tabata.config, elapsedSeconds = 240)
            repository.recordWorkout(tabata.config, elapsedSeconds = 120)
            settled(repository) { it.planned.value.size == 3 && it.history.value.size == 2 }
            repository.deleteWorkout(requireNotNull(repository.history.value.last().id))
            repository.removePlanned(repository.planned.value.first().id)
            settled(repository) { it.planned.value.size == 2 && it.history.value.size == 1 }
        }
        withApp { repository ->
            assertEquals(
                listOf(PlanSchedule.Weekly(3), PlanSchedule.Once(20_744)),
                repository.planned.value.map { it.schedule },
            )
            assertEquals(120, repository.history.value.single().elapsedSeconds)
        }
    }

    @Test
    fun `history is newest first and presets keep their save order`() = withApp { repository ->
        repository.saveCurrentAsPreset("B")
        repository.saveCurrentAsPreset("A")
        clock = 1
        repository.recordWorkout(IntervalConfig(), elapsedSeconds = 1)
        clock = 3
        repository.recordWorkout(IntervalConfig(), elapsedSeconds = 3)
        clock = 2
        repository.recordWorkout(IntervalConfig(), elapsedSeconds = 2)
        settled(repository) { it.customPresets.value.size == 2 && it.history.value.size == 3 }

        assertEquals(listOf("B", "A"), repository.customPresets.value.map { it.name })
        assertEquals(listOf(3, 2, 1), repository.history.value.map { it.elapsedSeconds })

        repository.deletePreset(repository.customPresets.value.first())
        repository.clearHistory()
        settled(repository) { it.customPresets.value.size == 1 && it.history.value.isEmpty() }
        assertEquals(listOf("A"), repository.customPresets.value.map { it.name })
    }

    private var clock = NOW
    private lateinit var settings: DataStore<Preferences>
    private lateinit var database: AppDatabase

    /**
     * Opens the app's storage, waits for [IntervalRepository.ready], runs [block], then shuts down the way
     * the Koin container does: cancel the scope (releasing DataStore's file) and close the database.
     */
    private fun withApp(block: suspend (IntervalRepository) -> Unit) = runBlocking {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.IO)
        settings = PreferenceDataStoreFactory.createWithPath(scope = scope) {
            directory.resolve(PREFERENCES_NAME).absolutePath.toPath()
        }
        database = Room.databaseBuilder<AppDatabase>(name = directory.resolve(DATABASE_NAME).absolutePath)
            .buildAppDatabase()
        try {
            val repository = IntervalRepository(
                settings,
                database.historyDao(),
                database.presetDao(),
                database.plannedDao(),
                scope,
                clock = { clock },
                siblingDao = database.siblingSessionDao(),
            )
            repository.ready.first { it }
            block(repository)
        } finally {
            job.cancelAndJoin()
            database.close()
        }
    }

    /** Collections follow the database a moment after a write; waits for them to catch up. */
    private suspend fun settled(repository: IntervalRepository, done: (IntervalRepository) -> Boolean) {
        withTimeout(5.seconds) {
            while (!done(repository)) delay(10.milliseconds)
        }
    }

    private suspend fun settledOnDisk(done: (Preferences) -> Boolean) {
        settings.data.first(done)
    }

    private companion object {
        const val NOW = 1_000L
    }
}

/** Version 1 as it shipped (`shared/schemas/.../1.json`), for opening an old database. */
private val V1_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`finished_at` INTEGER NOT NULL, `prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, " +
        "`rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `elapsed_seconds` INTEGER NOT NULL, " +
        "`ended_early` INTEGER NOT NULL)",
    "CREATE INDEX IF NOT EXISTS `index_history_finished_at` ON `history` (`finished_at`)",
    "CREATE TABLE IF NOT EXISTS `preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
        "`prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, `rest_seconds` INTEGER NOT NULL, " +
        "`rounds` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '4677e5a0ef7fb56c481c142c0985192c')",
)

/** Version 2 as it shipped (`shared/schemas/.../2.json`). */
private val V2_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`finished_at` INTEGER NOT NULL, `prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, " +
        "`rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `elapsed_seconds` INTEGER NOT NULL, " +
        "`ended_early` INTEGER NOT NULL, `work_active_seconds` INTEGER, `activity` TEXT)",
    "CREATE INDEX IF NOT EXISTS `index_history_finished_at` ON `history` (`finished_at`)",
    "CREATE TABLE IF NOT EXISTS `preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
        "`prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, `rest_seconds` INTEGER NOT NULL, " +
        "`rounds` INTEGER NOT NULL, `activity` TEXT)",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, 'ae976cb070b104a8eac8cee51f224e70')",
)

/** Version 3 as it shipped (`shared/schemas/.../3.json`). */
private val V3_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`finished_at` INTEGER NOT NULL, `prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, " +
        "`rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `elapsed_seconds` INTEGER NOT NULL, " +
        "`ended_early` INTEGER NOT NULL, `work_active_seconds` INTEGER, `activity` TEXT)",
    "CREATE INDEX IF NOT EXISTS `index_history_finished_at` ON `history` (`finished_at`)",
    "CREATE TABLE IF NOT EXISTS `preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
        "`prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, `rest_seconds` INTEGER NOT NULL, " +
        "`rounds` INTEGER NOT NULL, `activity` TEXT)",
    "CREATE TABLE IF NOT EXISTS `planned` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `weekday` INTEGER, " +
        "`epoch_day` INTEGER, `name` TEXT NOT NULL, `prep_seconds` INTEGER NOT NULL, " +
        "`work_seconds` INTEGER NOT NULL, `rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, " +
        "`activity` TEXT, `preset_id` INTEGER, " +
        "`created_at` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '280580113cb327b642526bc22a7229ce')",
)
