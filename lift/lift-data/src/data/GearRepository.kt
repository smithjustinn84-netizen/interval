package dev.justinsmith.lift.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.platform.IdGenerator
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.storage.SerialWriter
import dev.justinsmith.interval.storage.orEmptyOnError
import dev.justinsmith.interval.storage.orOnError
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.BandColour
import dev.justinsmith.lift.domain.bands
import dev.justinsmith.lift.domain.nextBandPosition
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * The bells the user owns, their bar and their bands: Lift's [GearStore]. Read with the database, on [scope],
 * and written through [writer], the queue every Lift repository shares: a band removed lands behind the set
 * just logged with it, which counts.
 */
class GearRepository(
    private val bellDao: BellDao,
    private val bandDao: BandDao,
    private val writer: SerialWriter,
    private val clock: WallClock,
    private val ids: IdGenerator,
    scope: CoroutineScope,
) : GearStore {
    private val log = Logger.withTag("GearRepository")

    /**
     * The user's bands as stored, from the first read on: what a set names its band by, shared so the sessions
     * read the one list. Unlike [bands], it shows nothing until read.
     */
    internal val owned: SharedFlow<List<Band>> =
        bandDao.observeAll().orEmptyOnError("bands", log)
            .map { rows -> bands(rows.map { it.toDomain() }) }
            .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    private val _bells = MutableStateFlow(emptyList<Double>())

    /** The bells the user owns, lightest first. */
    override val bells: StateFlow<List<Double>> = _bells.asStateFlow()

    private val _bar = MutableStateFlow<Double?>(null)

    /** What the user's barbell weighs empty, or null until they say they have one. */
    override val bar: StateFlow<Double?> = _bar.asStateFlow()

    private val _bands = MutableStateFlow(bands(emptyList()))

    /** The user's bands, archived ones too, lightest first: the five every user starts with, then theirs. */
    override val bands: StateFlow<List<Band>> = _bands.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** True once the bells, the bar and the bands have been read. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch {
            val bellsLoaded = CompletableDeferred<Unit>()
            val barLoaded = CompletableDeferred<Unit>()
            val bandsLoaded = CompletableDeferred<Unit>()
            launch {
                bellDao.observeAll().orEmptyOnError("bells", log).collect { rows ->
                    _bells.value = rows.map { it.kg }
                    bellsLoaded.complete(Unit)
                }
            }
            launch {
                bellDao.observeBar().orOnError(null, "the bar", log).collect { row ->
                    _bar.value = row?.kg
                    barLoaded.complete(Unit)
                }
            }
            launch {
                owned.collect {
                    _bands.value = it
                    bandsLoaded.complete(Unit)
                }
            }
            bellsLoaded.await()
            barLoaded.await()
            bandsLoaded.await()
            _ready.value = true
        }
    }

    override fun setOwned(kg: Double, owned: Boolean) {
        writer.persist { if (owned) bellDao.upsert(BellEntity(kg)) else bellDao.delete(kg) }
    }

    /** Sets the user's barbell to one weighing [kg] empty, or to none. */
    override fun setBar(kg: Double?) {
        writer.persist { if (kg != null) bellDao.upsertBar(BarEntity(kg = kg)) else bellDao.clearBar() }
    }

    /** Saves [band] as changed: renamed (blank for its starting words), recoloured or archived. */
    override fun saveBand(band: Band) {
        saveBands(listOf(band.copy(name = band.name.trim())))
    }

    /** Saves the order [bands] are in, each band's position its place. */
    override fun saveBands(bands: List<Band>) {
        val now = clock.nowMillis()
        writer.persist { bandDao.upsert(bands.map { it.toEntity(now) }) }
    }

    /** Adds a band named [name] in [colour], heaviest of all; returns its id. */
    override fun addBand(name: String, colour: BandColour): String {
        val id = ids.newId()
        saveBand(Band(id, name, colour, nextBandPosition(_bands.value)))
        return id
    }

    /**
     * Removes [band]: one the user added with no sets of it goes, any other (one they started with, or one
     * with sets) is archived, so every set keeps its band.
     */
    override fun removeBand(band: Band) {
        if (band.level != null) {
            saveBand(band.copy(archived = true))
            return
        }
        val now = clock.nowMillis()
        // Decided in the write, behind any set still queued: one logged with the band a moment ago counts.
        writer.persist { bandDao.removeOrArchive(band.copy(archived = true).toEntity(now)) }
    }
}
