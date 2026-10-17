package dev.justinsmith.interval.fakes

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import dev.justinsmith.interval.data.HistoryDao
import dev.justinsmith.interval.data.HistoryEntity
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.data.PresetDao
import dev.justinsmith.interval.data.PresetEntity
import dev.justinsmith.interval.di.StorageScope
import dev.justinsmith.interval.di.appModules
import dev.justinsmith.interval.platform.AppInfo
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.platform.Speech
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
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
            all.sortedWith(compareByDescending<HistoryEntity> { it.finishedAtEpochMillis }.thenByDescending { it.id })
        }

    override suspend fun insert(entry: HistoryEntity) {
        rows.update { it + entry.copy(id = nextId++) }
    }

    override suspend fun clear() {
        rows.value = emptyList()
    }
}

/** A [PresetDao] over a list, in save order like the real query. */
class FakePresetDao : PresetDao {
    private val rows = MutableStateFlow(emptyList<PresetEntity>())
    private var nextId = 1L

    override fun observeAll(): Flow<List<PresetEntity>> = rows

    override suspend fun all(): List<PresetEntity> = rows.value

    override suspend fun insert(preset: PresetEntity): Long {
        val id = nextId++
        rows.update { it + preset.copy(id = id) }
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

    /**
     * A repository over this storage. By default its writes run unconfined and the fakes never suspend, so
     * every write and read has landed by the time a call returns. Pass [deferredStorageScope] instead to have
     * writes and reads wait for the test's scheduler, as they wait for `Dispatchers.IO` in the app: that is
     * how a test catches code that reads a value back before its write has landed.
     */
    fun repository(
        now: () -> Long = { 0L },
        scope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
    ): IntervalRepository = IntervalRepository(settings, history, presets, scope, now)
}

/**
 * A storage scope on the test's scheduler, so writes and reads land only when the test advances it. Not
 * `backgroundScope`: `advanceUntilIdle` leaves background work waiting.
 */
fun TestScope.deferredStorageScope(): CoroutineScope =
    CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))

/** Stands in for the real `storageModule`, which opens files on disk. */
fun fakeStorageModule(storage: FakeStorage = FakeStorage()): Module = module {
    single<CoroutineScope>(StorageScope) { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }.withOptions {
        onClose { it?.cancel() }
    }
    single<DataStore<Preferences>> { storage.settings }
    single<HistoryDao> { storage.history }
    single<PresetDao> { storage.presets }
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

/** Days are numbered from the epoch in UTC, so tests don't depend on a time zone. Day 0 is a Monday. */
object FixedTimestampFormatter : TimestampFormatter {
    override fun date(epochMillis: Long): String = "day ${dayNumber(epochMillis)}"

    override fun time(epochMillis: Long): String = "at $epochMillis"

    override fun dayNumber(epochMillis: Long): Long = epochMillis / DAY_MILLIS

    override fun weekday(epochMillis: Long): Int = (dayNumber(epochMillis) % 7).toInt() + 1
}

/**
 * Stands in for `rememberPlatformModule()`. Tests must never load the real one: on the JVM it writes
 * to the user's app-data directory and plays sound.
 */
fun fakePlatformModule(
    player: CuePlayer = RecordingCuePlayer(),
    formatter: TimestampFormatter = FixedTimestampFormatter,
): Module = module {
    single { player }
    single { formatter }
    single<AppInfo> { FakeAppInfo }
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
