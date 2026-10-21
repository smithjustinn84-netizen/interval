package dev.justinsmith.bells.fakes

import dev.justinsmith.bells.data.BellDao
import dev.justinsmith.bells.data.BellEntity
import dev.justinsmith.bells.data.FollowingDao
import dev.justinsmith.bells.data.FollowingEntity
import dev.justinsmith.bells.data.SessionDao
import dev.justinsmith.bells.data.SessionEntity
import dev.justinsmith.bells.data.SetEntity
import dev.justinsmith.bells.data.SettingsDao
import dev.justinsmith.bells.data.SettingsEntity
import dev.justinsmith.bells.data.SiblingDao
import dev.justinsmith.bells.data.SiblingEntity
import dev.justinsmith.bells.di.BellsStorageScope
import dev.justinsmith.bells.di.bellsModules
import dev.justinsmith.bells.domain.EmomCue
import dev.justinsmith.bells.platform.Beeper
import dev.justinsmith.core.sync.SiblingFeed
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
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.withOptions
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** [SessionDao] over lists, as the real queries order them. */
class FakeSessionDao : SessionDao {
    val sessions = MutableStateFlow(emptyList<SessionEntity>())
    val sets = MutableStateFlow(emptyList<SetEntity>())
    private var nextId = 1L

    override fun observeSessions(): Flow<List<SessionEntity>> =
        sessions.map { all -> all.filterNot { it.deleted }.sortedByDescending { it.startedAtEpochMillis } }

    override fun observeSets(): Flow<List<SetEntity>> = sets

    override suspend fun allSessions() = sessions.value

    override suspend fun allSets() = sets.value

    override suspend fun active() = sessions.value.firstOrNull { it.finishedAtEpochMillis == null && !it.deleted }

    override suspend fun insert(session: SessionEntity) = sessions.update { it + session }

    override fun observeProgramDays(programId: String, since: Long): Flow<Int> = sessions.map { all ->
        all.count {
            it.programId == programId && it.finishedAtEpochMillis != null && !it.deleted &&
                it.startedAtEpochMillis >= since
        }
    }

    override suspend fun insert(set: SetEntity) = sets.update { it + set.copy(id = nextId++) }

    override suspend fun deleteSet(id: Long) = sets.update { all -> all.filterNot { it.id == id } }

    override suspend fun finish(uid: String, title: String, at: Long) = sessions.update { all ->
        all.map {
            if (it.uid ==
                uid
            ) {
                it.copy(finishedAtEpochMillis = at, title = title, updatedAtEpochMillis = at)
            } else {
                it
            }
        }
    }

    override suspend fun markDeleted(uid: String, now: Long) = sessions.update { all ->
        all.map { if (it.uid == uid) it.copy(deleted = true, updatedAtEpochMillis = now) else it }
    }

    override suspend fun discard(uid: String) = sessions.update { all -> all.filterNot { it.uid == uid } }

    override suspend fun discardSets(uid: String) = sets.update { all -> all.filterNot { it.sessionUid == uid } }
}

class FakeBellDao : BellDao {
    val rows = MutableStateFlow(emptyList<BellEntity>())

    override fun observeAll(): Flow<List<BellEntity>> = rows.map { all -> all.sortedBy { it.kg } }

    override suspend fun upsert(bell: BellEntity) = rows.update { all -> all.filterNot { it.kg == bell.kg } + bell }

    override suspend fun delete(kg: Double) = rows.update { all -> all.filterNot { it.kg == kg } }
}

class FakeSiblingDao : SiblingDao {
    val rows = MutableStateFlow(emptyMap<String, SiblingEntity>())

    override fun observeAll(): Flow<List<SiblingEntity>> = rows.map { all ->
        all.values.sortedByDescending { it.finishedAtEpochMillis }
    }

    override suspend fun all() = rows.value.values.toList()

    override suspend fun upsert(session: SiblingEntity) = rows.update { it + (session.uid to session) }

    override suspend fun delete(uid: String) = rows.update { it - uid }
}

class FakeFollowingDao : FollowingDao {
    val row = MutableStateFlow<FollowingEntity?>(null)

    override fun observe(): Flow<FollowingEntity?> = row

    override suspend fun upsert(following: FollowingEntity) {
        row.value = following
    }

    override suspend fun clear() {
        row.value = null
    }
}

class FakeSettingsDao : SettingsDao {
    val row = MutableStateFlow<SettingsEntity?>(null)

    override fun observe(): Flow<SettingsEntity?> = row

    override suspend fun upsert(settings: SettingsEntity) {
        row.value = settings
    }
}

class MemoryFeed : SiblingFeed {
    var published: String? = null
    var sibling: String? = null

    override fun publish(text: String) {
        published = text
    }

    override fun readSibling(): String? = sibling
}

/** The beeps an EMOM played, in order. */
class RecordingBeeper : Beeper {
    val cues = mutableListOf<EmomCue>()

    override fun beep(cue: EmomCue) {
        cues += cue
    }
}

/** Days of 24 hours from the epoch, in words a test can match. */
object FixedTimestampFormatter : TimestampFormatter {
    override fun date(epochMillis: Long): String = dateOfDay(dayNumber(epochMillis))

    override fun time(epochMillis: Long): String = "at $epochMillis"

    override fun dayNumber(epochMillis: Long): Long = epochMillis.floorDiv(DAY_MILLIS)

    override fun dateOfDay(epochDay: Long): String = "day $epochDay"

    override fun monthTitle(epochDay: Long): String = "month of day $epochDay"
}

private const val DAY_MILLIS = 86_400_000L

/** Bells' storage over the fakes: no database, and writes run at once on the caller's thread. */
class FakeBellsStorage {
    val sessions = FakeSessionDao()
    val bells = FakeBellDao()
    val sibling = FakeSiblingDao()
    val following = FakeFollowingDao()
    val settings = FakeSettingsDao()
}

/** Stands in for `rememberBellsPlatformModule()`. Tests never load the real one: it writes a database file. */
fun fakeBellsPlatformModule(
    feed: SiblingFeed = MemoryFeed(),
    beeper: Beeper = RecordingBeeper(),
    timestamps: TimestampFormatter = FixedTimestampFormatter,
): Module = module {
    single<SiblingFeed> { feed }
    single<Beeper> { beeper }
    single<TimestampFormatter> { timestamps }
}

fun fakeBellsStorageModule(storage: FakeBellsStorage): Module = module {
    single<CoroutineScope>(BellsStorageScope) { CoroutineScope(SupervisorJob() + Dispatchers.Unconfined) }.withOptions {
        onClose { it?.cancel() }
    }
    single<SessionDao> { storage.sessions }
    single<BellDao> { storage.bells }
    single<SiblingDao> { storage.sibling }
    single<FollowingDao> { storage.following }
    single<SettingsDao> { storage.settings }
}

/** Bells' whole container over fakes, its clock reading [now]. */
fun bellsTestKoin(
    storage: FakeBellsStorage = FakeBellsStorage(),
    platformModule: Module = fakeBellsPlatformModule(),
    now: () -> Long = { 0L },
): KoinApplication = koinApplication {
    modules(bellsModules(platformModule, fakeBellsStorageModule(storage)) + module { single { WallClock(now) } })
}
