@file:OptIn(ExperimentalUuidApi::class)

package dev.justinsmith.lift.fakes

import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.BandColour
import dev.justinsmith.lift.domain.bands
import dev.justinsmith.lift.domain.nextBandPosition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The gear in memory alone, for a ViewModel test that needs no storage: the five starting bands, then those
 * added. A band is removed as the repository removes one: a starting band, or one of [usedBands], is archived,
 * any other goes.
 */
class InMemoryGearStore(private val usedBands: Set<String> = emptySet()) : GearStore {
    override val bells = MutableStateFlow(emptyList<Double>())
    override val bar = MutableStateFlow<Double?>(null)
    private val changed = MutableStateFlow(emptyList<Band>())
    private val all = MutableStateFlow(bands(emptyList()))
    override val bands: StateFlow<List<Band>> get() = all

    override fun setOwned(kg: Double, owned: Boolean) =
        bells.update { (if (owned) it + kg else it - kg).distinct().sorted() }

    override fun setBar(kg: Double?) {
        bar.value = kg
    }

    override fun saveBand(band: Band) = saveBands(listOf(band.copy(name = band.name.trim())))

    override fun saveBands(bands: List<Band>) {
        val ids = bands.map { it.id }.toSet()
        keep(changed.value.filterNot { it.id in ids } + bands)
    }

    override fun addBand(name: String, colour: BandColour): String {
        val id = Uuid.random().toHexString()
        saveBand(Band(id, name, colour, nextBandPosition(bands.value)))
        return id
    }

    override fun removeBand(band: Band) {
        if (band.level != null || band.id in usedBands) {
            saveBand(band.copy(archived = true))
        } else {
            keep(changed.value.filterNot { it.id == band.id })
        }
    }

    private fun keep(bands: List<Band>) {
        changed.value = bands
        all.value = bands(bands)
    }
}
