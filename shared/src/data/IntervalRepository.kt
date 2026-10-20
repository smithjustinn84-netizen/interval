@file:OptIn(ExperimentalUuidApi::class)

package dev.justinsmith.interval.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.estimateCalories
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.plannedWorkSeconds
import dev.justinsmith.interval.timer.storedName
import dev.justinsmith.interval.timer.withPreset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Which feedback plays during a workout. */
data class CueSettings(
    val sound: Boolean = true,
    val voice: Boolean = true,
    val halfway: Boolean = true,
    val vibrate: Boolean = true,
)

/** How the app picks light or dark. */
enum class ThemeMode { System, Light, Dark }

/**
 * What the calorie estimates are worked out from. [weightKg] starts at [WeightUnit.Kg]'s default, so the
 * estimates show from the first run; null means the user turned them off.
 */
data class BodySettings(
    val weightKg: Double? = WeightUnit.Kg.default.toDouble(),
    val unit: WeightUnit = WeightUnit.Kg,
    val effort: Effort = Effort.Hard,
) {
    /**
     * The calories of [workSeconds] of [activity] at [effort] and [otherSeconds] recovering, or null
     * without a weight.
     */
    fun calories(workSeconds: Int, otherSeconds: Int, activity: Activity): Int? =
        weightKg?.let { estimateCalories(workSeconds, otherSeconds, it, effort, activity) }

    /** What [config] will burn if it runs as planned, get ready included; null without a weight. */
    fun plannedCalories(config: IntervalConfig): Int? {
        val work = config.rounds * config.workSeconds
        return calories(work, config.totalSeconds - work, config.activity)
    }
}

/** App-wide preferences that are not part of a workout's shape. */
data class AppSettings(
    val theme: ThemeMode = ThemeMode.Dark,
    val keepScreenOn: Boolean = true,
    val body: BodySettings = BodySettings(),
    /** Workouts a week the user aims for, within [WEEKLY_GOAL_RANGE]; null until they set one. */
    val weeklyGoal: Int? = null,
) {
    companion object {
        val WEEKLY_GOAL_RANGE = 1..14
    }
}

/**
 * A finished workout that ran for [elapsedSeconds], pauses excluded, to the end or [endedEarly]. Of that,
 * [workActiveSeconds] were work; null for one recorded before work time was kept. [id] is the one it is
 * stored under, null until it has been.
 */
data class HistoryEntry(
    val finishedAtEpochMillis: Long,
    val config: IntervalConfig,
    val elapsedSeconds: Int,
    val endedEarly: Boolean,
    val workActiveSeconds: Int? = null,
    val id: Long? = null,
) {
    /** The calories it burned by [body], or null without a weight. Older entries' work time is estimated. */
    fun calories(body: BodySettings): Int? {
        val work = (
            workActiveSeconds ?: config.plannedWorkSeconds(
                elapsedSeconds,
                endedEarly,
            )
            ).coerceAtMost(elapsedSeconds)
        return body.calories(work, elapsedSeconds - work, config.activity)
    }
}

/**
 * The app's saved state: the current setup, cue toggles, app settings, custom presets, workout history and
 * planned workouts.
 *
 * Settings live in [settings] (DataStore) and collections in Room ([historyDao], [presetDao], [plannedDao]).
 * Everything is read once into memory, after which [ready] turns true and every [StateFlow] holds what was
 * stored. Settings then change in memory first, synchronously, so a value set a moment ago is what the next
 * screen reads. Collections follow the database. Writes run in the background on [scope], one at a time in
 * the order they were made.
 */
// The one way into storage, so every collection's reads and writes are here, side by side.
@Suppress("detekt.TooManyFunctions")
class IntervalRepository(
    private val settings: DataStore<Preferences>,
    private val historyDao: HistoryDao,
    private val presetDao: PresetDao,
    private val plannedDao: PlannedDao,
    private val scope: CoroutineScope,
    private val clock: WallClock,
) {
    private val log = Logger.withTag("IntervalRepository")

    private val _ready = MutableStateFlow(false)

    /** True once every value below has been read from disk. Until then they hold defaults. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _config = MutableStateFlow(IntervalConfig())
    val config: StateFlow<IntervalConfig> = _config.asStateFlow()

    private val _cueSettings = MutableStateFlow(CueSettings())
    val cueSettings: StateFlow<CueSettings> = _cueSettings.asStateFlow()

    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    private val _customPresets = MutableStateFlow(emptyList<Preset>())

    /** In the order they were saved. */
    val customPresets: StateFlow<List<Preset>> = _customPresets.asStateFlow()

    private val _presets = MutableStateFlow(builtInPresets)

    /** Every preset the lists show: the built-ins, then [customPresets]. Updated with it, never behind. */
    val presets: StateFlow<List<Preset>> = _presets.asStateFlow()

    private val _history = MutableStateFlow(emptyList<HistoryEntry>())

    // Queued in call order and run one at a time, so a save can't overtake an earlier one (a clear
    // landing before the insert it followed, presets stored out of order).
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    /** Newest first. */
    val history: StateFlow<List<HistoryEntry>> = _history.asStateFlow()

    private val _planned = MutableStateFlow(emptyList<PlannedWorkout>())

    /** In the order they were planned. */
    val planned: StateFlow<List<PlannedWorkout>> = _planned.asStateFlow()

    init {
        scope.launch {
            for (write in writes) runLogged(write)
        }
        scope.launch {
            val presetsLoaded = CompletableDeferred<Unit>()
            val historyLoaded = CompletableDeferred<Unit>()
            val plannedLoaded = CompletableDeferred<Unit>()
            launch {
                presetDao.observeAll().orEmptyOnError("presets").collect { rows ->
                    val custom = rows.map { it.toDomain() }
                    _customPresets.value = custom
                    _presets.value = builtInPresets + custom
                    presetsLoaded.complete(Unit)
                }
            }
            launch {
                historyDao.observeAll().orEmptyOnError("history").collect { rows ->
                    _history.value = rows.map { it.toDomain() }
                    historyLoaded.complete(Unit)
                }
            }
            launch {
                plannedDao.observeAll().orEmptyOnError("plans").collect { rows ->
                    _planned.value = rows.mapNotNull { it.toDomain() }
                    plannedLoaded.complete(Unit)
                }
            }
            // An unreadable settings file falls back to the defaults rather than keeping the app from starting.
            val stored = settings.data
                .catch { e ->
                    log.e(e) { "Couldn't read settings" }
                    emit(emptyPreferences())
                }
                .first()
            _config.value = stored.toConfig()
            _cueSettings.value = stored.toCueSettings()
            _appSettings.value = stored.toAppSettings()
            presetsLoaded.await()
            historyLoaded.await()
            plannedLoaded.await()
            _ready.value = true
        }
    }

    fun updateConfig(transform: (IntervalConfig) -> IntervalConfig) {
        updateSetting(_config, transform) { prefs, config -> prefs.write(config) }
    }

    fun updateCueSettings(transform: (CueSettings) -> CueSettings) {
        updateSetting(_cueSettings, transform) { prefs, cues -> prefs.write(cues) }
    }

    fun updateAppSettings(transform: (AppSettings) -> AppSettings) {
        updateSetting(_appSettings, transform) { prefs, app -> prefs.write(app) }
    }

    // In memory at once, so the next read sees it; on disk in the background, the value as it is then.
    private fun <T> updateSetting(
        value: MutableStateFlow<T>,
        transform: (T) -> T,
        write: (MutablePreferences, T) -> Unit,
    ) {
        value.update(transform)
        persist { settings.edit { write(it, value.value) } }
    }

    /** Puts [preset] on the Timer, keeping the user's get-ready countdown and, mostly, activity: see [withPreset]. */
    fun applyPreset(preset: Preset) {
        updateConfig { it.withPreset(preset) }
    }

    /**
     * Saves [preset] as a new one of the user's own, under its [storedName]: a blank name becomes its
     * shape's ("40/20 ×6"), and one another preset has gets a number ("Tabata 2").
     */
    fun savePreset(preset: Preset) {
        persist { presetDao.insert(preset.copy(id = null).named().toEntity()) }
    }

    /** Saves [preset] as a new one of the user's own and puts its shape on the Timer. */
    fun saveAndApplyPreset(preset: Preset) {
        applyPreset(preset)
        savePreset(preset)
    }

    /**
     * Rewrites one of the user's own presets, found by its id: its name (kept unique among the others) and
     * shape. When it is the preset on the Timer the new shape goes there too, so the two stay in step
     * rather than leaving the old shape on the Timer, unsaved.
     */
    fun updatePreset(preset: Preset) {
        if (preset.id == null) return
        // Asked now: once the write lands, the list already holds the new shape.
        val onTimer = _presets.value.matching(_config.value)?.id == preset.id
        persist { presetDao.update(preset.named().toEntity()) }
        if (onTimer) applyPreset(preset)
    }

    /** Removes one of the user's own presets. Built-ins have no id and are left alone. */
    fun deletePreset(preset: Preset) {
        val id = preset.id ?: return
        persist { presetDao.delete(id) }
    }

    // Named inside the write against what is stored, not against [presets]: that follows the database, so
    // two saves in a row would both miss the other and take the same name.
    private suspend fun Preset.named(): Preset =
        copy(name = storedName(builtInPresets + presetDao.all().map { it.toDomain() }))

    /**
     * Records a workout that ran for [elapsedSeconds], [workSeconds] of them working; [endedEarly] when it
     * stopped before the last round.
     */
    fun recordWorkout(
        config: IntervalConfig,
        elapsedSeconds: Int,
        endedEarly: Boolean = false,
        workSeconds: Int? = null,
    ) {
        val now = clock.nowMillis()
        val entry = HistoryEntry(now, config, elapsedSeconds, endedEarly, workSeconds)
        persist { historyDao.insert(entry.toEntity(uid = Uuid.random().toHexString(), updatedAtEpochMillis = now)) }
    }

    /**
     * Removes one workout from the history, by the id it is stored under. Its row stays behind, marked
     * deleted, so a sibling app that has it learns it is gone.
     */
    fun deleteWorkout(id: Long) {
        val now = clock.nowMillis()
        persist { historyDao.delete(id, now) }
    }

    /** Removes every workout from the history, leaving each row marked deleted as [deleteWorkout] does. */
    fun clearHistory() {
        val now = clock.nowMillis()
        persist { historyDao.clear(now) }
    }

    /** Plans [preset] for every week on each of [weekdays] (1 for Monday through 7): one plan per day. */
    fun planWeekly(preset: Preset, weekdays: Set<Int>) {
        val days = weekdays.filter { it in 1..DAYS_PER_WEEK }.sorted()
        if (days.isEmpty()) return
        persist { plannedDao.insert(days.map { preset.planned(PlanSchedule.Weekly(it)).toEntity() }) }
    }

    /** Plans [preset] once, on [epochDay]. */
    fun planOnce(preset: Preset, epochDay: Long) {
        persist { plannedDao.insert(listOf(preset.planned(PlanSchedule.Once(epochDay)).toEntity())) }
    }

    fun removePlanned(id: Long) {
        persist { plannedDao.delete(id) }
    }

    private fun Preset.planned(schedule: PlanSchedule) = PlannedWorkout(
        id = 0,
        name = name,
        config = config,
        schedule = schedule,
        presetId = id,
        createdAtEpochMillis = clock.nowMillis(),
    )

    // A failed read shows an empty list at once, so the app still starts, then is tried again a few times:
    // `catch` alone ends the flow, and one bad read would stop the list following the database for good.
    private fun <T> Flow<List<T>>.orEmptyOnError(what: String): Flow<List<T>> =
        retryWhen { e, attempt ->
            log.e(e) { "Couldn't read $what" }
            if (attempt == 0L) emit(emptyList())
            val again = e !is CancellationException && attempt < READ_RETRIES
            if (again) delay(READ_RETRY_DELAY_MILLIS * (attempt + 1))
            again
        }.catch { emit(emptyList()) }

    private fun persist(write: suspend () -> Unit) {
        writes.trySend(write)
    }

    // A failed write is logged, never thrown: losing one save must not crash a workout.
    @Suppress("detekt.TooGenericExceptionCaught")
    private suspend fun runLogged(write: suspend () -> Unit) {
        try {
            write()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.e(e) { "Couldn't save" }
        }
    }

    private companion object {
        const val READ_RETRIES = 3L
        const val READ_RETRY_DELAY_MILLIS = 500L
    }
}
