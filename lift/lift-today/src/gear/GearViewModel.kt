package dev.justinsmith.lift.gear

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.BandColour
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.bands
import dev.justinsmith.lift.domain.moved
import dev.justinsmith.lift.domain.offeredBands
import dev.justinsmith.lift.domain.personalRecords
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * The bells the user owns, their [bar] (what it weighs empty; null for none), their bands (archived ones too)
 * lightest first with the ids of those with sets, and their records across every session.
 */
data class GearUiState(
    val owned: Set<Double> = emptySet(),
    val bar: Double? = null,
    val bands: List<Band> = bands(emptyList()),
    val usedBands: Set<String> = emptySet(),
    val records: List<PersonalRecord> = emptyList(),
)

/** The bells, bar and bands the user has, and their records. */
class GearViewModel(private val repository: LiftRepository) : ViewModel() {
    val state: StateFlow<GearUiState> =
        combine(repository.bells, repository.bar, repository.bands, repository.sessions) {
                bells,
                bar,
                bands,
                sessions,
            ->
            val sets = sessions.flatMap { it.sets }
            GearUiState(bells.toSet(), bar, bands, sets.mapNotNull { it.band?.id }.toSet(), personalRecords(sets))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, GearUiState())

    fun toggle(kg: Double) {
        repository.setOwned(kg, owned = kg !in state.value.owned)
    }

    /** Sets the user's bar to one weighing [kg] empty, or to none. */
    fun setBar(kg: Double?) = repository.setBar(kg)

    /** Saves [band] as [name] (blank for its starting words) in [colour], or adds a band with a null one. */
    fun saveBand(band: Band?, name: String, colour: BandColour) {
        if (band == null) {
            repository.addBand(name.trim(), colour)
        } else {
            repository.saveBand(band.copy(name = name, colour = colour))
        }
    }

    /** Moves [band] [by] places among those not archived: lighter when negative. */
    fun move(band: Band, by: Int) {
        val offered = offeredBands(state.value.bands)
        repository.saveBands(offered.moved(band.id, by) + state.value.bands.filter { it.archived })
    }

    fun remove(band: Band) = repository.removeBand(band)

    fun restore(band: Band) = repository.saveBand(band.copy(archived = false))
}
