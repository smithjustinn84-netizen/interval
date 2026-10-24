package dev.justinsmith.bells.gear

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.BandColour
import dev.justinsmith.bells.domain.MAX_BAND_NAME
import dev.justinsmith.bells.domain.bands
import dev.justinsmith.bells.domain.bars
import dev.justinsmith.bells.domain.formatKg
import dev.justinsmith.bells.domain.offeredBands
import dev.justinsmith.bells.domain.standardBells
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.band_add
import dev.justinsmith.bells.resources.band_archive
import dev.justinsmith.bells.resources.band_colour
import dev.justinsmith.bells.resources.band_delete
import dev.justinsmith.bells.resources.band_editor_edit
import dev.justinsmith.bells.resources.band_editor_new
import dev.justinsmith.bells.resources.band_heavier
import dev.justinsmith.bells.resources.band_lighter
import dev.justinsmith.bells.resources.band_name
import dev.justinsmith.bells.resources.band_name_placeholder
import dev.justinsmith.bells.resources.band_restore
import dev.justinsmith.bells.resources.band_save
import dev.justinsmith.bells.resources.bands_archived
import dev.justinsmith.bells.resources.bands_owned
import dev.justinsmith.bells.resources.bands_owned_hint
import dev.justinsmith.bells.resources.bar_none
import dev.justinsmith.bells.resources.bar_owned
import dev.justinsmith.bells.resources.bar_owned_hint
import dev.justinsmith.bells.resources.bells_owned
import dev.justinsmith.bells.resources.bells_owned_hint
import dev.justinsmith.bells.resources.bells_records
import dev.justinsmith.bells.resources.bells_records_empty
import dev.justinsmith.bells.resources.bells_title
import dev.justinsmith.bells.resources.kg_value
import dev.justinsmith.bells.ui.Choice
import dev.justinsmith.bells.ui.ChoiceRow
import dev.justinsmith.bells.ui.RecordRow
import dev.justinsmith.bells.ui.bandColourName
import dev.justinsmith.bells.ui.bandName
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** What the page does with a band: changes it, moves it, removes or restores it. */
class BandActions(
    val onSave: (band: Band?, name: String, colour: BandColour) -> Unit = { _, _, _ -> },
    val onMove: (Band, by: Int) -> Unit = { _, _ -> },
    val onRemove: (Band) -> Unit = {},
    val onRestore: (Band) -> Unit = {},
)

@Composable
fun GearRoute(onBack: () -> Unit) {
    val scope = currentKoinScope()
    val viewModel = viewModel { GearViewModel(scope.get()) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actions = remember(viewModel) {
        BandActions(viewModel::saveBand, viewModel::move, viewModel::remove, viewModel::restore)
    }
    GearScreen(state, onToggle = viewModel::toggle, onBack = onBack, bandActions = actions, onBar = viewModel::setBar)
}

/**
 * What the user has, opened from Lift: every standard bell size, the owned ones selected; their bar; their bands,
 * lightest first, each opening to rename, recolour, move or archive, and a new one to add; then the records.
 * Each sits in a card under its label, as a column's cell: on a wider window the bar sits beside the bells, and
 * the bands and the records fill whichever column is shorter.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GearScreen(
    state: GearUiState,
    onToggle: (Double) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    bandActions: BandActions = BandActions(),
    onBar: (Double?) -> Unit = {},
) {
    val palette = IntervalTheme.palette
    val card = Modifier.padding(horizontal = Spacing.space16)
    // The band being changed, by id; NEW_BAND for one being added.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    TabGrid(stringResource(Res.string.bells_title), modifier, navigation = { BackAction(onBack) }) {
        item(key = "owned") {
            Column(card) {
                GroupLabel(stringResource(Res.string.bells_owned))
                ListCard {
                    FlowRow(
                        Modifier.padding(Spacing.space16),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
                        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
                    ) {
                        standardBells.forEach { kg ->
                            Choice(stringResource(Res.string.kg_value, formatKg(kg)), kg in state.owned, onClick = {
                                onToggle(kg)
                            })
                        }
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
        item(key = "bar") {
            BarSection(state.bar, onBar, card)
        }
        item(key = "bands") {
            BandsSection(state.bands, onOpen = { editing = it.id }, onAdd = { editing = NEW_BAND }, modifier = card)
        }
        item(key = "records") {
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
                        RecordRow(record)
                    }
                }
            }
        }
    }
    val id = editing
    if (id != null) {
        val band = state.bands.firstOrNull { it.id == id }
        if (band == null && id != NEW_BAND) {
            // Deleted meanwhile.
            editing = null
        } else {
            BandSheet(
                band,
                used = band != null && (band.level != null || band.id in state.usedBands),
                lightest = band == offeredBands(state.bands).firstOrNull(),
                heaviest = band == offeredBands(state.bands).lastOrNull(),
                actions = bandActions,
                onDismiss = { editing = null },
            )
        }
    }
}

/** The user's bar by what it weighs empty, or None: a tap on another picks it ([onBar]). */
@Composable
private fun BarSection(bar: Double?, onBar: (Double?) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        GroupLabel(stringResource(Res.string.bar_owned))
        val none = stringResource(Res.string.bar_none)
        ListCard {
            ChoiceRow(listOf(null) + bars, bar, { kg ->
                kg?.let { stringResource(Res.string.kg_value, formatKg(it)) } ?: none
            }, onBar, Modifier.padding(Spacing.space16))
        }
        Text(
            stringResource(Res.string.bar_owned_hint),
            style = IntervalTheme.type.meta,
            color = IntervalTheme.palette.textDim,
            modifier = Modifier.padding(top = Spacing.space8),
        )
    }
}

/** The bands offered, lightest first, Add band, then any archived: a tap opens one ([onOpen]). */
@Composable
private fun BandsSection(bands: List<Band>, onOpen: (Band) -> Unit, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        GroupLabel(stringResource(Res.string.bands_owned))
        BandList(offeredBands(bands), onOpen)
        Text(
            stringResource(Res.string.bands_owned_hint),
            style = IntervalTheme.type.meta,
            color = IntervalTheme.palette.textDim,
            modifier = Modifier.padding(top = Spacing.space8),
        )
        SecondaryButton(
            stringResource(Res.string.band_add),
            onAdd,
            Modifier.fillMaxWidth().padding(top = Spacing.space8),
        )
        val archived = bands.filter { it.archived }
        if (archived.isNotEmpty()) {
            GroupLabel(stringResource(Res.string.bands_archived))
            BandList(archived, onOpen)
        }
    }
}

@Composable
private fun BandList(bands: List<Band>, onOpen: (Band) -> Unit) {
    ListCard {
        bands.forEachIndexed { i, band ->
            if (i > 0) RowDivider()
            LinkRow(bandName(band), bandColourName(band.colour), { onOpen(band) })
        }
    }
}

/**
 * A band to change, or a new one with a null [band]: its name (blank keeps a starting band's words) and
 * colour, Lighter and Heavier unless it is already the [lightest] or [heaviest], and Archive (Delete for one
 * the user added and never [used]), or Restore for one archived.
 */
@Composable
private fun BandSheet(
    band: Band?,
    used: Boolean,
    lightest: Boolean,
    heaviest: Boolean,
    actions: BandActions,
    onDismiss: () -> Unit,
) {
    val title = stringResource(if (band == null) Res.string.band_editor_new else Res.string.band_editor_edit)
    IntervalSheet(title, onDismiss = onDismiss) { hide ->
        val name = rememberTextFieldState(band?.name.orEmpty())
        var colour by rememberSaveable { mutableStateOf(band?.colour ?: BandColour.Purple) }
        Column(
            Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            GroupLabel(stringResource(Res.string.band_name))
            NameField(
                name,
                placeholder =
                band?.let { bandName(it.copy(name = "")) } ?: stringResource(Res.string.band_name_placeholder),
                onDone = {},
                label = stringResource(Res.string.band_name),
                maxLength = MAX_BAND_NAME,
            )
            GroupLabel(stringResource(Res.string.band_colour))
            ChoiceRow(BandColour.entries, colour, { bandColourName(it) }, { colour = it })
            if (band != null && !band.archived) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                    if (!lightest) {
                        SecondaryButton(
                            stringResource(Res.string.band_lighter),
                            { actions.onMove(band, -1) },
                            Modifier.weight(1f),
                        )
                    }
                    if (!heaviest) {
                        SecondaryButton(
                            stringResource(Res.string.band_heavier),
                            { actions.onMove(band, 1) },
                            Modifier.weight(1f),
                        )
                    }
                }
            }
            val typed = name.text.toString()
            // A band the user adds needs a name; one they started with keeps its words while blank.
            if (band != null || typed.isNotBlank()) {
                AccentButton(
                    stringResource(Res.string.band_save),
                    {
                        actions.onSave(band, typed, colour)
                        hide()
                    },
                    Modifier.fillMaxWidth().padding(top = Spacing.space8),
                )
            }
            when {
                band == null -> Unit

                band.archived -> SecondaryButton(
                    stringResource(Res.string.band_restore),
                    {
                        actions.onRestore(band)
                        hide()
                    },
                    Modifier.fillMaxWidth(),
                )

                else -> SecondaryButton(
                    stringResource(if (used) Res.string.band_archive else Res.string.band_delete),
                    {
                        actions.onRemove(band)
                        hide()
                    },
                    Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** What [editing] holds while a band is being added. */
private const val NEW_BAND = ""
