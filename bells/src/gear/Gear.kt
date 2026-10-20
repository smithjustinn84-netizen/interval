package dev.justinsmith.bells.gear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.domain.PersonalRecord
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.domain.personalRecords
import dev.justinsmith.bells.domain.standardBells
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.bells_owned
import dev.justinsmith.bells.resources.bells_owned_hint
import dev.justinsmith.bells.resources.bells_records
import dev.justinsmith.bells.resources.bells_records_empty
import dev.justinsmith.bells.resources.bells_title
import dev.justinsmith.bells.resources.kg_value
import dev.justinsmith.bells.resources.record_line
import dev.justinsmith.bells.ui.Choice
import dev.justinsmith.bells.ui.exerciseName
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

data class GearUiState(val owned: Set<Double> = emptySet(), val records: List<PersonalRecord> = emptyList())

/** The bells the user owns, and their records across every session. */
class GearViewModel(private val repository: BellsRepository) : ViewModel() {
    val state: StateFlow<GearUiState> =
        combine(repository.bells, repository.sessions) { bells, sessions ->
            GearUiState(bells.toSet(), personalRecords(sessions.flatMap { it.sets }))
        }.stateIn(viewModelScope, SharingStarted.Eagerly, GearUiState())

    fun toggle(kg: Double) {
        repository.setOwned(kg, owned = kg !in state.value.owned)
    }
}

@Composable
fun GearRoute() {
    val scope = currentKoinScope()
    val viewModel = viewModel { GearViewModel(scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    GearScreen(state, onToggle = viewModel::toggle)
}

/** The Bells tab: every standard size, the owned ones selected, then the records. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GearScreen(state: GearUiState, onToggle: (Double) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val card = Modifier.padding(horizontal = Spacing.space16)
    TabGrid(stringResource(Res.string.bells_title), modifier) {
        item(key = "owned", span = StaggeredGridItemSpan.FullLine) {
            Column(card) {
                GroupLabel(stringResource(Res.string.bells_owned))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
                    verticalArrangement = Arrangement.spacedBy(Spacing.space8),
                ) {
                    standardBells.forEach { kg ->
                        Choice(stringResource(Res.string.kg_value, formatKg(kg)), kg in state.owned, onClick = {
                            onToggle(kg)
                        })
                    }
                }
                Text(
                    stringResource(Res.string.bells_owned_hint),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                    modifier = Modifier.padding(top = Spacing.space8),
                )
            }
        }
        item(key = "records", span = StaggeredGridItemSpan.FullLine) {
            Column(card) {
                GroupLabel(stringResource(Res.string.bells_records))
                ListCard {
                    if (state.records.isEmpty()) {
                        Text(
                            stringResource(Res.string.bells_records_empty),
                            style = IntervalTheme.type.body,
                            color = palette.textDim,
                            modifier = Modifier.padding(Spacing.space16),
                        )
                    }
                    state.records.forEachIndexed { i, record ->
                        if (i > 0) RowDivider()
                        Column(Modifier.padding(Spacing.space16)) {
                            Text(exerciseName(record.exercise), style = IntervalTheme.type.label, color = palette.text)
                            Text(
                                stringResource(
                                    Res.string.record_line,
                                    formatKg(record.heaviestKg),
                                    record.repsAtHeaviest,
                                    record.mostReps,
                                    formatKg(record.mostRepsKg),
                                ),
                                style = IntervalTheme.type.meta,
                                color = palette.textDim,
                            )
                        }
                    }
                }
            }
        }
    }
}
