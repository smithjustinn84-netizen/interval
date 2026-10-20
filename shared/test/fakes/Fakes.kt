package dev.justinsmith.interval.fakes

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.justinsmith.core.sync.NoSiblingFeed
import dev.justinsmith.core.sync.SiblingFeed
import dev.justinsmith.interval.data.HistoryDao
import dev.justinsmith.interval.data.HistoryEntity
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.data.PlannedDao
import dev.justinsmith.interval.data.PlannedEntity
import dev.justinsmith.interval.data.PresetDao
import dev.justinsmith.interval.data.PresetEntity
import dev.justinsmith.interval.data.SiblingSessionDao
import dev.justinsmith.interval.data.SiblingSessionEntity
import dev.justinsmith.interval.di.StorageScope
import dev.justinsmith.interval.di.appModules
import dev.justinsmith.interval.platform.AppInfo
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.platform.Speech
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.dateOfEpochDay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** A [DataStore] held in memory. Updates apply one at a time, as DataStore's do. */
class InMemoryDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}

/** A [HistoryDao] over a list, newest first like the real query. */
class FakeHistoryDao : HistoryDao {
    private val rows = MutableStateFlow(emptyList<HistoryEntity>())
    private var nextId = 1L

    override fun observeAll(): Flow<List<HistoryEntity>> =
        rows.map { all ->
            all.filterNot { it.deleted }
                .sortedWith(compareByDescending<HistoryEntity> { it.finishedAtEpochMillis }.thenByDescending { it.id })
        }

    override suspend fun all(): List<HistoryEntity> = rows.value.sortedBy { it.id }

    // As Room's autoGenerate does: an id of 0 takes the next one, any other is kept. Like the unique
    // index, a uid already stored is refused.
    override suspend fun insert(entry: HistoryEntity) {
        check(rows.value.none { it.uid == entry.uid }) { "UNIQUE constraint failed: history.uid" }
        val id = if (entry.id == 0L) nextId++ else entry.id.also { nextId = maxOf(nextId, it + 1) }
        rows.update { it + entry.copy(id = id) }
    }

    override suspend fun delete(id: Long, now: Long) {
        rows.update { all -> all.map { if (it.id == id) it.copy(deleted = true, updatedAtEpochMillis = now) else it } }
    }

    override suspend fun clear(now: Long) {
        rows.update { all -> all.map { if (it.deleted) it else it.copy(deleted = true, updatedAtEpochMillis = now) } }
    }
}

/** A [SiblingSessionDao] over a map keyed by uid. */
class FakeSiblingSessionDao : SiblingSessionDao {
    private val rows = MutableStateFlow(emptyMap<String, SiblingSessionEntity>())

    override fun observeAll(): Flow<List<SiblingSessionEntity>> =
        rows.map { all -> all.values.sortedByDescending { it.finishedAtEpochMillis } }

    override suspend fun all(): List<SiblingSessionEntity> = rows.value.values.toList()

    override suspend fun upsert(session: SiblingSessionEntity) {
        rows.update { it + (session.uid to session) }
    }

    override suspend fun delete(uid: String) {
        rows.update { it - uid }
    }
}

/** A [PlannedDao] over a list, in the order planned like the real query. */
class FakePlannedDao : PlannedDao {
    private val rows = MutableStateFlow(emptyList<PlannedEntity>())
    private var nextId = 1L

    override fun observeAll(): Flow<List<PlannedEntity>> = rows

    override suspend fun insert(planned: List<PlannedEntity>) {
        val stored = planned.map { row ->
            row.copy(id = if (row.id == 0L) nextId++ else row.id.also { nextId = maxOf(nextId, it + 1) })
        }
        rows.update { all -> (all + stored).sortedBy { it.id } }
    }

    override suspend fun delete(id: Long) {
        rows.update { all -> all.filterNot { it.id == id } }
    }
}

/** A [PresetDao] over a list, in save order like the real query. */
class FakePresetDao : PresetDao {
    private val rows = MutableStateFlow(emptyList<PresetEntity>())
    private var nextId = 1L

    override fun observeAll(): Flow<List<PresetEntity>> = rows

    override suspend fun all(): List<PresetEntity> = rows.value

    override suspend fun insert(preset: PresetEntity): Long {
        val id = if (preset.id == 0L) nextId++ else preset.id.also { nextId = maxOf(nextId, it + 1) }
        rows.update { all -> (all + preset.copy(id = id)).sortedBy { it.id } }
        return id
    }

    override suspend fun update(preset: PresetEntity) {
        rows.update { all -> all.map { if (it.id == preset.id) preset else it } }
    }

    override suspend fun delete(id: Long) {
        rows.update { all -> all.filterNot { it.id == id } }
    }
}

/**
 * Everything the repository stores, in memory. Two repositories over one [FakeStorage] are the app
 * before and after a restart.
 */
class FakeStorage {
    val settings = InMemoryDataStore()
    val history = FakeHistoryDao()
    val presets = FakePresetDao()
    val planned = FakePlannedDao()
    val siblings = FakeSiblingSessionDao()

    /**
     * A repository over this storage. By default its writes run unconfined and the fakes never suspend, so
     * every write and read has landed by the time a call returns. Pass [deferredStorageScope] instead to have
     * writes and reads wait for the test's scheduler, as they wait for `Dispatchers.IO` in the app: that is
     * how a test catches code that reads a value back before its write has landed.
     */
    fun repository(
        now: WallClock = WallClock { 0L },
        scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
    ): IntervalRepository = IntervalRepository(settings, history, presets, planned, scope, now, siblings)
}

/**
 * A storage scope on the test's scheduler, so writes and reads land only when the test advances it. Not
 * `backgroundScope`: `advanceUntilIdle` leaves background work waiting.
 */
fun TestScope.deferredStorageScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

/** Saves the Timer's current settings under [name], as the save buttons do. */
fun IntervalRepository.saveCurrentAsPreset(name: String) {
    savePreset(Preset(name, config.value))
}

/** Stands in for the real `storageModule`, which opens files on disk. */
fun fakeStorageModule(storage: FakeStorage = FakeStorage()): Module = module {
    single<CoroutineScope>(StorageScope) { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }.withOptions {
        onClose { it?.cancel() }
    }
    single<DataStore<Preferences>> { storage.settings }
    single<HistoryDao> { storage.history }
    single<PresetDao> { storage.presets }
    single<PlannedDao> { storage.planned }
    single<SiblingSessionDao> { storage.siblings }
}

/** A [CuePlayer] that plays nothing and remembers every cue it was asked for. */
class RecordingCuePlayer : CuePlayer {
    val beeps = mutableListOf<Beep>()
    val spoken = mutableListOf<Speech>()
    val haptics = mutableListOf<Haptic>()

    override fun beep(beep: Beep) {
        beeps += beep
    }

    override fun speak(speech: Speech) {
        spoken += speech
    }

    override fun vibrate(haptic: Haptic) {
        haptics += haptic
    }

    override fun release() = Unit
}

const val DAY_MILLIS = 24L * 60 * 60 * 1000

/**
 * Days are epoch days in UTC, as the platforms count them, so tests don't depend on a time zone. Day 0,
 * 1 January 1970, is a Thursday.
 */
object FixedTimestampFormatter : TimestampFormatter {
    override fun date(epochMillis: Long): String = dateOfDay(dayNumber(epochMillis))

    override fun time(epochMillis: Long): String = "at $epochMillis"

    override fun dayNumber(epochMillis: Long): Long = epochMillis.floorDiv(DAY_MILLIS)

    override fun dateOfDay(epochDay: Long): String = "day $epochDay"

    override fun monthTitle(epochDay: Long): String = dateOfEpochDay(epochDay).let { "${it.month}/${it.year}" }
}

/**
 * Stands in for `rememberPlatformModule()`. Tests must never load the real one: on the JVM it writes
 * to the user's app-data directory and plays sound.
 */
fun fakePlatformModule(
    player: CuePlayer = RecordingCuePlayer(),
    formatter: TimestampFormatter = FixedTimestampFormatter,
    feed: SiblingFeed = NoSiblingFeed,
): Module = module {
    single { player }
    single { formatter }
    single<AppInfo> { FakeAppInfo }
    single<SiblingFeed> { feed }
}

/** A build that calls itself [version]. */
object FakeAppInfo : AppInfo {
    override val version: String = "1.2.3"
}

/** The app's real container over fake platform services and storage, for `AppContent`. Time stands at [now]. */
fun testKoin(
    platformModule: Module = fakePlatformModule(),
    storage: FakeStorage = FakeStorage(),
    now: Long = 0L,
): KoinApplication = koinApplication {
    modules(appModules(platformModule, fakeStorageModule(storage)) + module { single { WallClock { now } } })
}
