package dev.justinsmith.interval.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import dev.justinsmith.interval.fakes.saveCurrentAsPreset
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.platform.DATABASE_NAME
import dev.justinsmith.interval.platform.PREFERENCES_NAME
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Block
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The repository over real files: a DataStore and a Room database in a temp directory, with writes on
 * Dispatchers.IO as in the app. The common tests' fakes finish every write before a call returns; these
 * check the app still behaves when writes are genuinely asynchronous.
 */
@Suppress("detekt.TooManyFunctions") // One stored thing per test, side by side.
class StorageTest {
    private val directory: File = Files.createTempDirectory("interval-storage").toFile()

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    @Test
    fun `a change is readable straight away, before it reaches disk`() = withApp { repository ->
        repository.settings.updateConfig { it.copy(workSeconds = 50) }
        assertEquals(50, repository.settings.config.value.workSeconds)
    }

    @Test
    fun `everything survives a restart`() {
        val config = IntervalConfig(
            5,
            45,
            15,
            rounds = 12,
            warmUpSeconds = 300,
            coolDownSeconds = 120,
            activity = Activity.Kettlebell,
            sets = 3,
            setRestSeconds = 90,
            workStepSeconds = 15,
            then = listOf(Block(workSeconds = 60, restSeconds = 30, rounds = 4, sets = 2, workStepSeconds = -10)),
            blockRestSeconds = 120,
        )
        withApp { repository ->
            repository.settings.updateConfig { config }
            repository.plans.planOnce(Preset("Long", config).asPlan(), epochDay = 20_744)
            repository.settings.updateCueSettings { it.copy(halfway = false) }
            repository.settings.updateAppSettings {
                AppSettings(theme = ThemeMode.Light, keepScreenOn = false, reminderMinuteOfDay = 450)
            }
            repository.saveCurrentAsPreset("Long")
            repository.history.recordWorkout(config, elapsedSeconds = 300, endedEarly = true)
            settled(repository) {
                it.presets.customPresets.value.size == 1 && it.history.history.value.size == 1 &&
                    it.plans.planned.value.size == 1
            }
            settledOnDisk { it.toConfig() == config && !it.toCueSettings().halfway && !it.toAppSettings().keepScreenOn }
        }
        withApp { repository ->
            // Ready means every value is the stored one, not a default.
            assertTrue(repository.ready.value)
            assertEquals(config, repository.settings.config.value)
            assertEquals(CueSettings(halfway = false), repository.settings.cueSettings.value)
            assertEquals(
                AppSettings(theme = ThemeMode.Light, keepScreenOn = false, reminderMinuteOfDay = 450),
                repository.settings.appSettings.value,
            )
            assertEquals(Preset("Long", config, id = 1), repository.presets.customPresets.value.single())
            assertEquals(
                HistoryEntry(NOW, config, elapsedSeconds = 300, endedEarly = true, id = 1),
                repository.history.history.value.single(),
            )
            assertEquals(Preset("Long", config).asPlan(), repository.plans.planned.value.single().workout)
        }
    }

    @Test
    fun `after many quick changes the last one is what's saved`() {
        withApp { repository ->
            repeat(40) { repository.settings.updateConfig { it.stepped(ConfigField.Rounds, +1) } }
            val expected = IntervalConfig().rounds + 40
            assertEquals(expected, repository.settings.config.value.rounds)
            settledOnDisk { it.toConfig().rounds == expected }
        }
        withApp { repository -> assertEquals(IntervalConfig().rounds + 40, repository.settings.config.value.rounds) }
    }

    @Test
    fun `a theme this build doesn't know reads as the default`() {
        withApp { _ -> settings.edit { it[stringPreferencesKey("theme")] = "Sepia" } }
        withApp { repository -> assertEquals(AppSettings().theme, repository.settings.appSettings.value.theme) }
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
            val entry = repository.history.history.value.single()
            assertEquals(IntervalConfig(10, 20, 10, rounds = 8), entry.config)
            assertEquals(null, entry.workActiveSeconds)
            settled(repository) { it.presets.customPresets.value.isNotEmpty() }
            assertEquals(
                Preset("Mine", IntervalConfig(10, 40, 20, rounds = 6), id = 1),
                repository.presets.customPresets.value.single(),
            )
        }
    }

    @Test
    fun `a stored value out of range reads as the nearest in range and loses nothing beside it`() {
        BundledSQLiteDriver().open(directory.resolve(DATABASE_NAME).absolutePath).use { connection ->
            V1_SCHEMA.forEach(connection::execSQL)
            connection.execSQL(
                "INSERT INTO history (finished_at, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "elapsed_seconds, ended_early) VALUES ($NOW, 10, 20, 10, 0, 250, 0), " +
                    "(${NOW + 1}, 10, 20, 10, 8, 250, 0)",
            )
            connection.execSQL(
                "INSERT INTO preset (name, prep_seconds, work_seconds, rest_seconds, rounds) " +
                    "VALUES ('Broken', 10, 1, 20, 0)",
            )
            connection.execSQL("PRAGMA user_version = 1")
        }
        withApp { _ ->
            settings.edit {
                it[intPreferencesKey("rounds")] = 0
                it[intPreferencesKey("work")] = 1
            }
        }
        withApp { repository ->
            assertEquals(1, repository.settings.config.value.rounds)
            assertEquals(5, repository.settings.config.value.workSeconds)
            assertEquals(listOf(8, 1), repository.history.history.value.map { it.config.rounds })
            settled(repository) { it.presets.customPresets.value.isNotEmpty() }
            assertEquals(IntervalConfig(10, 5, 20, rounds = 1), repository.presets.customPresets.value.single().config)
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
            val entry = repository.history.history.value.single()
            assertEquals(IntervalConfig(10, 20, 10, rounds = 8, activity = Activity.Rower), entry.config)
            assertEquals(160, entry.workActiveSeconds)
            assertEquals(emptyList(), repository.plans.planned.value)
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
            assertEquals(listOf(350, 300, 250), repository.history.history.value.map { it.elapsedSeconds })
            val rows = database.historyDao().all()
            assertEquals(3, rows.map { it.uid }.toSet().size)
            assertTrue(rows.all { it.uid.length == 32 && !it.deleted && it.updatedAtEpochMillis == 0L })
            // The uid index is in place: a new workout still records beside the old ones.
            repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 1)
            settled(repository) { it.history.history.value.size == 4 }
        }
    }

    @Test
    fun `a version 4 database drops the sibling's workouts and keeps its own`() {
        BundledSQLiteDriver().open(directory.resolve(DATABASE_NAME).absolutePath).use { connection ->
            V4_SCHEMA.forEach(connection::execSQL)
            connection.execSQL(
                "INSERT INTO history (finished_at, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "elapsed_seconds, ended_early, work_active_seconds, activity, uid, updated_at, deleted) " +
                    "VALUES ($NOW, 10, 20, 10, 8, 240, 0, 160, 'Rower', 'a1', $NOW, 0)",
            )
            connection.execSQL(
                "INSERT INTO sibling_session VALUES ('b1', 'Bells', $NOW, 900, NULL, 'Kettlebell', 'Swings', '{}', 1)",
            )
            connection.execSQL("PRAGMA user_version = 4")
        }
        withApp { repository ->
            assertEquals(listOf(240), repository.history.history.value.map { it.elapsedSeconds })
        }
        BundledSQLiteDriver().open(directory.resolve(DATABASE_NAME).absolutePath).use { connection ->
            connection.prepare("SELECT name FROM sqlite_master WHERE name = 'sibling_session'").use {
                assertFalse(it.step())
            }
        }
    }

    @Test
    fun `a version 5 database keeps its plans as interval workouts`() {
        BundledSQLiteDriver().open(directory.resolve(DATABASE_NAME).absolutePath).use { connection ->
            V4_SCHEMA.forEach(connection::execSQL)
            connection.execSQL("DROP TABLE sibling_session")
            connection.execSQL(
                "INSERT INTO planned (weekday, epoch_day, name, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "activity, preset_id, created_at) VALUES (2, NULL, 'Tabata', 10, 20, 10, 8, 'General', NULL, $NOW)",
            )
            connection.execSQL("PRAGMA user_version = 5")
        }
        withApp { repository ->
            val plan = repository.plans.planned.value.single()
            assertEquals(PlanSchedule.Weekly(2), plan.schedule)
            assertEquals(PlanWorkout.Interval("Tabata", IntervalConfig(10, 20, 10, rounds = 8)), plan.workout)
        }
    }

    @Test
    fun `a version 9 database keeps its workouts presets and plans as one set`() {
        BundledSQLiteDriver().open(directory.resolve(DATABASE_NAME).absolutePath).use { connection ->
            V9_SCHEMA.forEach(connection::execSQL)
            connection.execSQL(
                "INSERT INTO history (finished_at, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "elapsed_seconds, ended_early, uid, updated_at) VALUES ($NOW, 10, 20, 10, 8, 240, 0, 'a', $NOW)",
            )
            connection.execSQL(
                "INSERT INTO preset (name, prep_seconds, work_seconds, rest_seconds, rounds) " +
                    "VALUES ('Mine', 10, 40, 20, 6)",
            )
            connection.execSQL(
                "INSERT INTO planned (weekday, epoch_day, name, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "activity, preset_id, created_at) VALUES (2, NULL, 'Tabata', 10, 20, 10, 8, 'General', NULL, $NOW)",
            )
            connection.execSQL("PRAGMA user_version = 9")
        }
        withApp { repository ->
            val configs = listOf(
                repository.history.history.value.single().config,
                repository.presets.customPresets.value.single().config,
                (repository.plans.planned.value.single().workout as PlanWorkout.Interval).config,
            )
            configs.forEach {
                assertEquals(1, it.sets)
                assertEquals(0, it.setRestSeconds)
            }
        }
    }

    @Test
    fun `a version 10 database keeps its workouts presets and plans with no ladder`() {
        BundledSQLiteDriver().open(directory.resolve(DATABASE_NAME).absolutePath).use { connection ->
            V10_SCHEMA.forEach(connection::execSQL)
            connection.execSQL(
                "INSERT INTO history (finished_at, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "elapsed_seconds, ended_early, uid, updated_at, sets, set_rest_seconds) " +
                    "VALUES ($NOW, 10, 20, 10, 8, 240, 0, 'a', $NOW, 2, 60)",
            )
            connection.execSQL(
                "INSERT INTO preset (name, prep_seconds, work_seconds, rest_seconds, rounds) " +
                    "VALUES ('Mine', 10, 40, 20, 6)",
            )
            connection.execSQL(
                "INSERT INTO planned (weekday, epoch_day, name, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "activity, preset_id, created_at) VALUES (2, NULL, 'Tabata', 10, 20, 10, 8, 'General', NULL, $NOW)",
            )
            connection.execSQL("PRAGMA user_version = 10")
        }
        withApp { repository ->
            val history = repository.history.history.value.single().config
            assertEquals(2 to 60, history.sets to history.setRestSeconds)
            val configs = listOf(
                history,
                repository.presets.customPresets.value.single().config,
                (repository.plans.planned.value.single().workout as PlanWorkout.Interval).config,
            )
            configs.forEach { assertEquals(0, it.workStepSeconds) }
        }
    }

    @Test
    fun `a version 11 database keeps its workouts presets and plans in one block`() {
        BundledSQLiteDriver().open(directory.resolve(DATABASE_NAME).absolutePath).use { connection ->
            V11_SCHEMA.forEach(connection::execSQL)
            connection.execSQL(
                "INSERT INTO history (finished_at, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "elapsed_seconds, ended_early, uid, updated_at, sets, set_rest_seconds, work_step_seconds) " +
                    "VALUES ($NOW, 10, 20, 10, 8, 240, 0, 'a', $NOW, 2, 60, 5)",
            )
            connection.execSQL(
                "INSERT INTO preset (name, prep_seconds, work_seconds, rest_seconds, rounds) " +
                    "VALUES ('Mine', 10, 40, 20, 6)",
            )
            connection.execSQL(
                "INSERT INTO planned (weekday, epoch_day, name, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "activity, preset_id, created_at) VALUES (2, NULL, 'Tabata', 10, 20, 10, 8, 'General', NULL, $NOW)",
            )
            connection.execSQL("PRAGMA user_version = 11")
        }
        withApp { repository ->
            val history = repository.history.history.value.single().config
            assertEquals(5, history.workStepSeconds)
            val configs = listOf(
                history,
                repository.presets.customPresets.value.single().config,
                (repository.plans.planned.value.single().workout as PlanWorkout.Interval).config,
            )
            configs.forEach {
                assertEquals(1, it.blocks.size)
                assertEquals(0, it.blockRestSeconds)
            }
        }
    }

    @Test
    fun `a version 12 database keeps its plans and takes sport days from then on`() {
        BundledSQLiteDriver().open(directory.resolve(DATABASE_NAME).absolutePath).use { connection ->
            V12_SCHEMA.forEach(connection::execSQL)
            connection.execSQL(
                "INSERT INTO planned (weekday, epoch_day, name, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "activity, preset_id, created_at) VALUES (2, NULL, 'Tabata', 10, 20, 10, 8, 'General', NULL, $NOW)",
            )
            connection.execSQL(
                "INSERT INTO planned (weekday, epoch_day, name, prep_seconds, work_seconds, rest_seconds, rounds, " +
                    "activity, preset_id, created_at, lift) VALUES (4, NULL, '', 0, 0, 0, 0, NULL, NULL, $NOW, " +
                    "'program_day')",
            )
            connection.execSQL("PRAGMA user_version = 12")
        }
        withApp { repository ->
            val plans = repository.plans.planned.value.map { it.workout }
            assertEquals("Tabata", (plans.first() as PlanWorkout.Interval).name)
            assertEquals(PlanWorkout.Lift.ProgramDay, plans.last())
            assertEquals(emptyList(), repository.history.sportDays.value)

            repository.plans.planWeekly(PlanWorkout.SportDay(Sport.Bjj), setOf(4))
            repository.history.logSport(Sport.Custom("Judo"), epochDay = 20_744)
            repository.history.logSport(Sport.Custom("Judo"), epochDay = 20_744)
            repository.history.logSport(Sport.Bjj, epochDay = 20_744)
            repository.history.unlogSport(Sport.Bjj, epochDay = 20_744)
            settled(repository) { it.plans.planned.value.size == 3 && it.history.sportDays.value.size == 1 }
        }
        withApp { repository ->
            assertEquals(PlanWorkout.SportDay(Sport.Bjj), repository.plans.planned.value.last().workout)
            val logged = repository.history.sportDays.value.single()
            assertEquals(Sport.Custom("Judo"), logged.sport)
            assertEquals(20_744L, logged.epochDay)
        }
    }

    @Test
    fun `deleted workouts stay on disk as tombstones`() {
        withApp { repository ->
            repeat(3) { repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = it + 1) }
            settled(repository) { it.history.history.value.size == 3 }
            clock = 2_000
            repository.history.deleteWorkout(requireNotNull(repository.history.history.value.first().id))
            settled(repository) { it.history.history.value.size == 2 }
            clock = 3_000
            repository.history.clearHistory()
            settled(repository) { it.history.history.value.isEmpty() }
        }
        withApp { repository ->
            assertEquals(emptyList(), repository.history.history.value)
            val rows = database.historyDao().all()
            assertTrue(rows.all { it.deleted })
            assertEquals(listOf(3_000L, 3_000L, 2_000L), rows.map { it.updatedAtEpochMillis })
        }
    }

    @Test
    fun `a deleted workout comes back on restore and stays back after a restart`() {
        withApp { repository ->
            repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 240)
            settled(repository) { it.history.history.value.size == 1 }
            val id = requireNotNull(repository.history.history.value.single().id)
            clock = 2_000
            repository.history.deleteWorkout(id)
            settled(repository) { it.history.history.value.isEmpty() }
            clock = 3_000
            repository.history.restoreWorkout(id)
            settled(repository) { it.history.history.value.size == 1 }
        }
        withApp { repository ->
            assertEquals(listOf(240), repository.history.history.value.map { it.elapsedSeconds })
            val row = database.historyDao().all().single()
            assertTrue(!row.deleted && row.updatedAtEpochMillis == 3_000L)
        }
    }

    @Test
    fun `plans and a deleted workout survive a restart`() {
        val tabata = Preset("Tabata", IntervalConfig(10, 20, 10, rounds = 8))
        withApp { repository ->
            repository.plans.planWeekly(tabata.asPlan(), setOf(1, 3))
            repository.plans.planOnce(tabata.asPlan(), epochDay = 20_744)
            repository.plans.planWeekly(PlanWorkout.Lift.ProgramDay, setOf(5))
            repository.history.recordWorkout(tabata.config, elapsedSeconds = 240)
            repository.history.recordWorkout(tabata.config, elapsedSeconds = 120)
            settled(repository) { it.plans.planned.value.size == 4 && it.history.history.value.size == 2 }
            repository.history.deleteWorkout(requireNotNull(repository.history.history.value.last().id))
            repository.plans.removePlanned(repository.plans.planned.value.first().id)
            settled(repository) { it.plans.planned.value.size == 3 && it.history.history.value.size == 1 }
        }
        withApp { repository ->
            assertEquals(
                listOf(PlanSchedule.Weekly(3), PlanSchedule.Once(20_744), PlanSchedule.Weekly(5)),
                repository.plans.planned.value.map { it.schedule },
            )
            assertEquals(
                listOf(tabata.asPlan(), tabata.asPlan(), PlanWorkout.Lift.ProgramDay),
                repository.plans.planned.value.map { it.workout },
            )
            assertEquals(120, repository.history.history.value.single().elapsedSeconds)
        }
    }

    @Test
    fun `history is newest first and presets keep their save order`() = withApp { repository ->
        repository.saveCurrentAsPreset("B")
        repository.saveCurrentAsPreset("A")
        clock = 1
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 1)
        clock = 3
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 3)
        clock = 2
        repository.history.recordWorkout(IntervalConfig(), elapsedSeconds = 2)
        settled(repository) { it.presets.customPresets.value.size == 2 && it.history.history.value.size == 3 }

        assertEquals(listOf("B", "A"), repository.presets.customPresets.value.map { it.name })
        assertEquals(listOf(3, 2, 1), repository.history.history.value.map { it.elapsedSeconds })

        repository.presets.deletePreset(repository.presets.customPresets.value.first())
        repository.history.clearHistory()
        settled(repository) { it.presets.customPresets.value.size == 1 && it.history.history.value.isEmpty() }
        assertEquals(listOf("A"), repository.presets.customPresets.value.map { it.name })
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
                database.sportLogDao(),
                database.importDao(),
                scope,
                clock = { clock },
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

/** Version 4 as it shipped (`shared/schemas/.../4.json`), with the sibling app's table. */
private val V4_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`finished_at` INTEGER NOT NULL, `prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, " +
        "`rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `elapsed_seconds` INTEGER NOT NULL, " +
        "`ended_early` INTEGER NOT NULL, `work_active_seconds` INTEGER, `activity` TEXT, " +
        "`uid` TEXT NOT NULL DEFAULT '', `updated_at` INTEGER NOT NULL DEFAULT 0, " +
        "`deleted` INTEGER NOT NULL DEFAULT 0)",
    "CREATE INDEX IF NOT EXISTS `index_history_finished_at` ON `history` (`finished_at`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_history_uid` ON `history` (`uid`)",
    "CREATE TABLE IF NOT EXISTS `preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
        "`prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, `rest_seconds` INTEGER NOT NULL, " +
        "`rounds` INTEGER NOT NULL, `activity` TEXT)",
    "CREATE TABLE IF NOT EXISTS `planned` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `weekday` INTEGER, " +
        "`epoch_day` INTEGER, `name` TEXT NOT NULL, `prep_seconds` INTEGER NOT NULL, " +
        "`work_seconds` INTEGER NOT NULL, `rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, " +
        "`activity` TEXT, `preset_id` INTEGER, " +
        "`created_at` INTEGER NOT NULL)",
    "CREATE TABLE IF NOT EXISTS `sibling_session` (`uid` TEXT NOT NULL, `source` TEXT NOT NULL, " +
        "`finished_at` INTEGER NOT NULL, `elapsed_seconds` INTEGER NOT NULL, `active_seconds` INTEGER, " +
        "`activity` TEXT, `title` TEXT NOT NULL, `detail_json` TEXT NOT NULL, `updated_at` INTEGER NOT NULL, " +
        "PRIMARY KEY(`uid`))",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '0095628ceeb945b942ead22982ab6429')",
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

/** Version 9 as it shipped (`schemas/.../9.json`). */
private val V9_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`finished_at` INTEGER NOT NULL, `prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, " +
        "`rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `elapsed_seconds` INTEGER NOT NULL, " +
        "`ended_early` INTEGER NOT NULL, `work_active_seconds` INTEGER, `activity` TEXT, " +
        "`uid` TEXT NOT NULL DEFAULT '', `updated_at` INTEGER NOT NULL DEFAULT 0, " +
        "`deleted` INTEGER NOT NULL DEFAULT 0, `effort` INTEGER, `note` TEXT, " +
        "`warm_up_seconds` INTEGER NOT NULL DEFAULT 0, `cool_down_seconds` INTEGER NOT NULL DEFAULT 0, " +
        "`started_at` INTEGER, `average_bpm` INTEGER, `peak_bpm` INTEGER)",
    "CREATE INDEX IF NOT EXISTS `index_history_finished_at` ON `history` (`finished_at`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_history_uid` ON `history` (`uid`)",
    "CREATE TABLE IF NOT EXISTS `preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`name` TEXT NOT NULL, `prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, " +
        "`rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `activity` TEXT, " +
        "`warm_up_seconds` INTEGER NOT NULL DEFAULT 0, `cool_down_seconds` INTEGER NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS `planned` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`weekday` INTEGER, `epoch_day` INTEGER, `name` TEXT NOT NULL, `prep_seconds` INTEGER NOT NULL, " +
        "`work_seconds` INTEGER NOT NULL, `rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, " +
        "`activity` TEXT, `preset_id` INTEGER, `created_at` INTEGER NOT NULL, `lift` TEXT, " +
        "`warm_up_seconds` INTEGER NOT NULL DEFAULT 0, `cool_down_seconds` INTEGER NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, " +
        "'4fe20881722ab4102459d597d42e8ddd')",
)

/** Version 10 as it shipped (`schemas/.../10.json`). */
private val V10_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`finished_at` INTEGER NOT NULL, `prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, " +
        "`rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `elapsed_seconds` INTEGER NOT NULL, " +
        "`ended_early` INTEGER NOT NULL, `work_active_seconds` INTEGER, `activity` TEXT, " +
        "`uid` TEXT NOT NULL DEFAULT '', `updated_at` INTEGER NOT NULL DEFAULT 0, " +
        "`deleted` INTEGER NOT NULL DEFAULT 0, `effort` INTEGER, `note` TEXT, " +
        "`warm_up_seconds` INTEGER NOT NULL DEFAULT 0, `cool_down_seconds` INTEGER NOT NULL DEFAULT 0, " +
        "`started_at` INTEGER, `average_bpm` INTEGER, `peak_bpm` INTEGER, " +
        "`sets` INTEGER NOT NULL DEFAULT 1, `set_rest_seconds` INTEGER NOT NULL DEFAULT 0)",
    "CREATE INDEX IF NOT EXISTS `index_history_finished_at` ON `history` (`finished_at`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_history_uid` ON `history` (`uid`)",
    "CREATE TABLE IF NOT EXISTS `preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`name` TEXT NOT NULL, `prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, " +
        "`rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `activity` TEXT, " +
        "`warm_up_seconds` INTEGER NOT NULL DEFAULT 0, `cool_down_seconds` INTEGER NOT NULL DEFAULT 0, " +
        "`sets` INTEGER NOT NULL DEFAULT 1, `set_rest_seconds` INTEGER NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS `planned` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`weekday` INTEGER, `epoch_day` INTEGER, `name` TEXT NOT NULL, `prep_seconds` INTEGER NOT NULL, " +
        "`work_seconds` INTEGER NOT NULL, `rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, " +
        "`activity` TEXT, `preset_id` INTEGER, `created_at` INTEGER NOT NULL, `lift` TEXT, " +
        "`warm_up_seconds` INTEGER NOT NULL DEFAULT 0, `cool_down_seconds` INTEGER NOT NULL DEFAULT 0, " +
        "`sets` INTEGER NOT NULL DEFAULT 1, `set_rest_seconds` INTEGER NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, " +
        "'a394e861d59345f1403fe7834341fb90')",
)

private val V11_SCHEMA = listOf(
    "CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`finished_at` INTEGER NOT NULL, `prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, " +
        "`rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `elapsed_seconds` INTEGER NOT NULL, " +
        "`ended_early` INTEGER NOT NULL, `work_active_seconds` INTEGER, `activity` TEXT, " +
        "`uid` TEXT NOT NULL DEFAULT '', `updated_at` INTEGER NOT NULL DEFAULT 0, " +
        "`deleted` INTEGER NOT NULL DEFAULT 0, `effort` INTEGER, `note` TEXT, " +
        "`warm_up_seconds` INTEGER NOT NULL DEFAULT 0, `cool_down_seconds` INTEGER NOT NULL DEFAULT 0, " +
        "`started_at` INTEGER, `average_bpm` INTEGER, `peak_bpm` INTEGER, " +
        "`sets` INTEGER NOT NULL DEFAULT 1, `set_rest_seconds` INTEGER NOT NULL DEFAULT 0, " +
        "`work_step_seconds` INTEGER NOT NULL DEFAULT 0)",
    "CREATE INDEX IF NOT EXISTS `index_history_finished_at` ON `history` (`finished_at`)",
    "CREATE UNIQUE INDEX IF NOT EXISTS `index_history_uid` ON `history` (`uid`)",
    "CREATE TABLE IF NOT EXISTS `preset` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`name` TEXT NOT NULL, `prep_seconds` INTEGER NOT NULL, `work_seconds` INTEGER NOT NULL, " +
        "`rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, `activity` TEXT, " +
        "`warm_up_seconds` INTEGER NOT NULL DEFAULT 0, `cool_down_seconds` INTEGER NOT NULL DEFAULT 0, " +
        "`sets` INTEGER NOT NULL DEFAULT 1, `set_rest_seconds` INTEGER NOT NULL DEFAULT 0, " +
        "`work_step_seconds` INTEGER NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS `planned` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
        "`weekday` INTEGER, `epoch_day` INTEGER, `name` TEXT NOT NULL, `prep_seconds` INTEGER NOT NULL, " +
        "`work_seconds` INTEGER NOT NULL, `rest_seconds` INTEGER NOT NULL, `rounds` INTEGER NOT NULL, " +
        "`activity` TEXT, `preset_id` INTEGER, `created_at` INTEGER NOT NULL, `lift` TEXT, " +
        "`warm_up_seconds` INTEGER NOT NULL DEFAULT 0, `cool_down_seconds` INTEGER NOT NULL DEFAULT 0, " +
        "`sets` INTEGER NOT NULL DEFAULT 1, `set_rest_seconds` INTEGER NOT NULL DEFAULT 0, " +
        "`work_step_seconds` INTEGER NOT NULL DEFAULT 0)",
    "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
    "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, " +
        "'c40d90c87a0193c000faf15b659e4cb1')",
)

/** Version 12 as it shipped (`schemas/.../12.json`): version 11 and the columns Room's migration to 12 adds. */
private val V12_SCHEMA = V11_SCHEMA.dropLast(1) + listOf("history", "preset", "planned").flatMap { table ->
    listOf(
        "ALTER TABLE `$table` ADD COLUMN `then_blocks` TEXT NOT NULL DEFAULT '[]'",
        "ALTER TABLE `$table` ADD COLUMN `block_rest_seconds` INTEGER NOT NULL DEFAULT 0",
    )
} + "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3b0e16ac34a6b1ae62a67e2c86ad1f43')"
