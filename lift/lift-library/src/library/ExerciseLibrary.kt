package dev.justinsmith.lift.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.ExerciseFilters
import dev.justinsmith.lift.library.resources.Res
import dev.justinsmith.lift.library.resources.library_group
import dev.justinsmith.lift.library.resources.library_new
import dev.justinsmith.lift.library.resources.library_recent
import dev.justinsmith.lift.library.resources.library_title
import dev.justinsmith.lift.library.resources.library_yours
import dev.justinsmith.lift.ui.equipmentName
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** The library, an exercise's page over it, and a new exercise's form; a session on its page by [onOpenSession]. */
@Composable
fun ExerciseLibraryRoute(onBack: () -> Unit, onOpenSession: (uid: String) -> Unit = {}) {
    val viewModel = koinViewModel<ExerciseLibraryViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf<String?>(null) }
    ExerciseLibraryScreen(
        state.list,
        onFilters = viewModel::updateFilters,
        onBack = onBack,
        onNew = { adding = it },
        onOpen = viewModel::open,
    )
    state.detail?.let { shown -> ExerciseDetailSheet(shown, onOpenSession, onDismiss = { viewModel.open(null) }) }
    adding?.let { name -> ExerciseEditorSheet(name, onDismiss = { adding = null }) }
}

/**
 * The exercise library, opened from Lift: every exercise there is to log, to look through and narrow. Under
 * the search and the filters, a card for the recent ones while nothing narrows, one for the user's own, then
 * one for each kind of equipment with any to show: one column on a phone, side by side as the window widens.
 * New adds one of the user's own, as does a search that finds nothing ([onNew], with the search). Any exercise
 * opens its page ([onOpen], by its key).
 */
@Composable
fun ExerciseLibraryScreen(
    list: LibraryList,
    onFilters: ((ExerciseFilters) -> ExerciseFilters) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onNew: (name: String) -> Unit = {},
    onOpen: (key: String) -> Unit = {},
) {
    val picker = rememberPickerState()
    val matches = pickerMatches(list, picker)
    val onPick = { exercise: Exercise -> onOpen(exercise.key) }
    val canPick = { _: Exercise -> true }
    val card = Modifier.padding(horizontal = Spacing.space16)
    // Grouped once a change to what is shown, not each time the grid's content runs, so each group keeps its list.
    val (yours, byEquipment) = remember(matches.shown) {
        val (custom, builtIn) = matches.shown.partition { it is CustomExercise }
        custom to builtIn.groupBy { it.equipment }
    }
    TabGrid(
        stringResource(Res.string.library_title),
        modifier,
        navigation = { BackAction(onBack) },
        actions = { IconAction(IntervalIcons.Add, stringResource(Res.string.library_new), { onNew("") }) },
    ) {
        item(key = "header", span = StaggeredGridItemSpan.FullLine) {
            PickerHeader(list, matches, picker, onFilters, card, archivable = true)
        }
        if (!matches.narrowed && list.recents.isNotEmpty()) {
            item(key = "recent") {
                Column(card) {
                    GroupLabel(stringResource(Res.string.library_recent))
                    ExerciseList(list.recents, matches.names, null, onPick, canPick, opens = true)
                }
            }
        }
        if (yours.isNotEmpty()) {
            item(key = "yours") {
                Column(card) {
                    GroupLabel(
                        stringResource(Res.string.library_group, stringResource(Res.string.library_yours), yours.size),
                    )
                    ExerciseList(yours, matches.names, null, onPick, canPick, opens = true, tagYours = false)
                }
            }
        }
        Equipment.entries.forEach { equipment ->
            val exercises = byEquipment[equipment] ?: return@forEach
            item(key = "group-${equipment.name}") {
                Column(card) {
                    GroupLabel(stringResource(Res.string.library_group, equipmentName(equipment), exercises.size))
                    ExerciseList(exercises, matches.names, null, onPick, canPick, opens = true, equipment = false)
                }
            }
        }
        if (matches.shown.isEmpty()) {
            item(key = "none", span = StaggeredGridItemSpan.FullLine) {
                NoMatch(picker.query.text.toString(), onNew, card)
            }
        }
    }
}
