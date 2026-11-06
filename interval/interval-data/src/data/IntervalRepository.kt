package dev.justinsmith.interval.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.platform.IdGenerator
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.storage.SerialWriter
import dev.justinsmith.interval.storage.StorageError
import dev.justinsmith.interval.timer.Preset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The interval side's saved state, the one way into storage: a repository per kind of thing kept, [settings] (the
 * settings DataStore: the Timer's setup, cues, app settings and the workout under way), [presets], [history] and
 * [plans] (Room), each the store its screens take. Their writes go through one queue, on [scope], in the order
 * they were made, so a save of one kind can't overtake an earlier one of another: a preset applied and saved
 * lands as it was made. Everything is read into memory before [ready] turns true. What only the app does is here:
 * [importAll], [writeErrors] and [close].
 */
class IntervalRepository(
    preferences: DataStore<Preferences>,
    historyDao: HistoryDao,
    presetDao: PresetDao,
    plannedDao: PlannedDao,
    sportLogDao: SportLogDao,
    private val importDao: ImportDao,
    scope: CoroutineScope,
    private val clock: WallClock,
    private val ids: IdGenerator = IdGenerator.Random,
) {
    private val log = Logger.withTag("IntervalRepository")
    private val writer = SerialWriter(scope, log)

    /** Each save that failed, as it fails, for the app to say so: the write itself is dropped. */
    val writeErrors: SharedFlow<StorageError> get() = writer.errors

    /** The Timer's setup, the cue toggles, the app's settings and the workout under way. */
    val settings = SettingsRepository(preferences, writer, scope)

    /** The built-in presets and the user's own. */
    val presets = PresetRepository(presetDao, settings, writer, scope)

    /** The finished workouts and the sport days logged. */
    val history = HistoryRepository(historyDao, sportLogDao, writer, clock, ids, scope)

    /** The planned workouts. */
    val plans = PlanRepository(plannedDao, writer, clock, scope)

    private val _ready = MutableStateFlow(false)

    /** True once every repository has read what it keeps. Until then their values hold defaults. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch {
            listOf(settings.ready, presets.ready, history.ready, plans.ready).forEach { read -> read.first { it } }
            _ready.value = true
        }
    }

    /**
     * Takes no more writes and returns once those already made have landed: the container's last step before it
     * closes the database and cancels the scope, which would otherwise drop them.
     */
    suspend fun close() {
        writer.close()
    }

    /**
     * Adds what an import brings, all of it or none (one transaction), behind every write before it, and returns
     * once it has landed; a failure is thrown, nothing having been written. Each workout gets a uid of its own;
     * a plan naming a preset by [PlanWorkout.Interval.name] of one added here, or already here, links to it; a
     * sport logged on its day already stays as it is.
     */
    suspend fun importAll(
        history: List<HistoryEntry>,
        presets: List<Preset>,
        planned: List<PlannedWorkout>,
        sportDays: List<LoggedSport> = emptyList(),
    ) {
        val now = clock.nowMillis()
        val rows = history.map { it.copy(id = null).toEntity(uid = ids.newId(), now) }
        val presetRows = presets.map { it.copy(id = null).toEntity() }
        val planRows = planned.map { it.copy(id = 0).toEntity() }
        val names = planned.map { (it.workout as? PlanWorkout.Interval)?.takeIf { w -> w.presetId == null }?.name }
        val sportRows = sportDays.map { it.copy(id = 0).toEntity() }
        writer.await { importDao.importAll(rows, presetRows, planRows, names, sportRows) }
    }
}
