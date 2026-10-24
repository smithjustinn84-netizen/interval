package dev.justinsmith.bells.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import dev.justinsmith.bells.domain.CustomExercise
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.ExerciseFilters
import dev.justinsmith.bells.domain.Focus
import dev.justinsmith.bells.domain.Measure
import dev.justinsmith.bells.domain.nameMatches
import dev.justinsmith.bells.domain.toggled
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.library_add_named
import dev.justinsmith.bells.resources.library_all
import dev.justinsmith.bells.resources.library_archived
import dev.justinsmith.bells.resources.library_clear
import dev.justinsmith.bells.resources.library_count
import dev.justinsmith.bells.resources.library_empty
import dev.justinsmith.bells.resources.library_empty_hint
import dev.justinsmith.bells.resources.library_empty_query
import dev.justinsmith.bells.resources.library_filters
import dev.justinsmith.bells.resources.library_filters_count
import dev.justinsmith.bells.resources.library_focus
import dev.justinsmith.bells.resources.library_measured
import dev.justinsmith.bells.resources.library_owned_only
import dev.justinsmith.bells.resources.library_recent
import dev.justinsmith.bells.resources.library_search
import dev.justinsmith.bells.resources.library_show_archived
import dev.justinsmith.bells.resources.library_yours
import dev.justinsmith.bells.resources.library_yours_only
import dev.justinsmith.bells.ui.Choice
import dev.justinsmith.bells.ui.equipmentName
import dev.justinsmith.bells.ui.exerciseFacts
import dev.justinsmith.bells.ui.exerciseName
import dev.justinsmith.bells.ui.focusName
import dev.justinsmith.bells.ui.measureName
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.NameField
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.enterExpanding
import dev.justinsmith.interval.ui.exitShrinking
import dev.justinsmith.interval.ui.pressScale
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What the library lists: [exercises] in the catalogue's order, the [recents] first, narrowed by [filters],
 * [owned] being the equipment the user has.
 */
data class LibraryList(
    val exercises: List<Exercise>,
    val recents: List<Exercise> = emptyList(),
    val filters: ExerciseFilters = ExerciseFilters(),
    val owned: Set<Equipment> = Equipment.entries.toSet(),
)

/** What a picker holds while it is open: the search typed, and whether the facets besides equipment show. */
@Stable
class PickerState(val query: TextFieldState, more: MutableState<Boolean>) {
    var more by more
}

/** A [PickerState] kept across a configuration change and process death. */
@Composable
fun rememberPickerState(): PickerState {
    val query = rememberTextFieldState()
    val more = rememberSaveable { mutableStateOf(false) }
    return remember(query, more) { PickerState(query, more) }
}

/**
 * What a picker shows of [LibraryList]: each exercise's [names], the exercises in [shown] that pass the
 * filters and the search, and whether either one [narrowed] them.
 */
@Immutable
data class PickerMatches(val names: Map<String, String>, val shown: List<Exercise>, val narrowed: Boolean)

/** The exercises of [list] that pass its filters and the search in [picker]. */
@Composable
fun pickerMatches(list: LibraryList, picker: PickerState): PickerMatches {
    val names = list.exercises.associate { it.key to exerciseName(it) }
    val text = picker.query.text.toString()
    val shown = list.exercises.filter {
        list.filters.matches(it, list.owned) &&
            nameMatches(names.getValue(it.key), text)
    }
    return PickerMatches(names, shown, narrowed = text.isNotBlank() || !list.filters.isEmpty)
}

/**
 * The exercise library: a search, the equipment to narrow to, and behind Filters the focus, what it is
 * measured by and only the user's own; then the recent ones, while nothing narrows, and all that match. With
 * [onPick] a row picks its exercise ([canPick] says which can be), the [selected] one ticked; without, the
 * rows just inform. With [onAdd], a search that finds nothing offers to add it as the user's own. Only an
 * [archivable] picker, the library's, offers the user's archived exercises among its filters. One column, for
 * a sheet; the library lays the same parts out as a grid.
 */
@Composable
fun ExercisePicker(
    list: LibraryList,
    onFilters: ((ExerciseFilters) -> ExerciseFilters) -> Unit,
    modifier: Modifier = Modifier,
    selected: Exercise? = null,
    onPick: ((Exercise) -> Unit)? = null,
    canPick: (Exercise) -> Boolean = { true },
    onAdd: ((name: String) -> Unit)? = null,
    archivable: Boolean = false,
) {
    val picker = rememberPickerState()
    val matches = pickerMatches(list, picker)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        PickerHeader(list, matches, picker, onFilters, archivable = archivable)
        if (!matches.narrowed && list.recents.isNotEmpty()) {
            GroupLabel(stringResource(Res.string.library_recent))
            ExerciseList(list.recents, matches.names, selected, onPick, canPick)
            GroupLabel(stringResource(Res.string.library_all))
        }
        if (matches.shown.isEmpty()) {
            NoMatch(picker.query.text.toString(), onAdd)
        } else {
            ExerciseList(matches.shown, matches.names, selected, onPick, canPick)
        }
    }
}

/**
 * A picker's head: the search, a chip for each kind of equipment, how many match with Clear while anything
 * narrows them, and Filters, which opens the other facets under it.
 */
@Composable
fun PickerHeader(
    list: LibraryList,
    matches: PickerMatches,
    picker: PickerState,
    onFilters: ((ExerciseFilters) -> ExerciseFilters) -> Unit,
    modifier: Modifier = Modifier,
    archivable: Boolean = false,
) {
    val filters = list.filters
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        val search = stringResource(Res.string.library_search)
        NameField(picker.query, placeholder = search, onDone = {}, label = search, maxLength = SEARCH_LENGTH)
        FilterChips(Equipment.entries, filters.equipment, { equipmentName(it) }) { e ->
            onFilters { it.copy(equipment = it.equipment.toggled(e)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(Res.plurals.library_count, matches.shown.size, matches.shown.size),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.palette.textDim,
                modifier = Modifier.weight(1f),
            )
            if (matches.narrowed) {
                QuietTextButton(stringResource(Res.string.library_clear), {
                    picker.query.clearText()
                    onFilters { ExerciseFilters() }
                })
            }
            QuietTextButton(
                if (filters.moreCount > 0) {
                    stringResource(Res.string.library_filters_count, filters.moreCount)
                } else {
                    stringResource(Res.string.library_filters)
                },
                { picker.more = !picker.more },
                expanded = picker.more,
            )
        }
        AnimatedVisibility(picker.more, enter = enterExpanding(), exit = exitShrinking()) {
            MoreFilters(filters, onFilters, archivable)
        }
    }
}

/** The facets besides equipment: focus, measure, only yours, only what you own, and archived if [archivable]. */
@Composable
private fun MoreFilters(
    filters: ExerciseFilters,
    onFilters: ((ExerciseFilters) -> ExerciseFilters) -> Unit,
    archivable: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        GroupLabel(stringResource(Res.string.library_focus))
        FilterChips(Focus.entries, filters.focus, {
            focusName(it)
        }) { f -> onFilters { it.copy(focus = it.focus.toggled(f)) } }
        GroupLabel(stringResource(Res.string.library_measured))
        FilterChips(Measure.entries, filters.measure, { measureName(it) }) { m ->
            onFilters { it.copy(measure = it.measure.toggled(m)) }
        }
        Choice(
            stringResource(Res.string.library_yours_only),
            filters.yoursOnly,
            onClick = { onFilters { it.copy(yoursOnly = !it.yoursOnly) } },
            role = Role.Checkbox,
        )
        Choice(
            stringResource(Res.string.library_owned_only),
            filters.ownedOnly,
            onClick = { onFilters { it.copy(ownedOnly = !it.ownedOnly) } },
            role = Role.Checkbox,
        )
        if (archivable) {
            Choice(
                stringResource(Res.string.library_show_archived),
                filters.showArchived,
                onClick = { onFilters { it.copy(showArchived = !it.showArchived) } },
                role = Role.Checkbox,
            )
        }
    }
}

/** A chip for each of [options], any number of them on. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> FilterChips(options: List<T>, on: Set<T>, label: @Composable (T) -> String, onToggle: (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        options.forEach { option -> Choice(label(option), option in on, { onToggle(option) }, role = Role.Checkbox) }
    }
}

/**
 * [exercises] in a card, one row each, by their [names]. [onPick] and [canPick] say which rows can be tapped;
 * with [opens] those lead somewhere, so they end in a chevron. Without [equipment] the facts leave out what
 * each is done with, for a card that is already one kind of equipment's; without [tagYours] the user's own go
 * untagged, unless archived, for a card that is already theirs.
 */
@Composable
internal fun ExerciseList(
    exercises: List<Exercise>,
    names: Map<String, String>,
    selected: Exercise?,
    onPick: ((Exercise) -> Unit)?,
    canPick: (Exercise) -> Boolean,
    modifier: Modifier = Modifier,
    opens: Boolean = false,
    equipment: Boolean = true,
    tagYours: Boolean = true,
) {
    ListCard(modifier) {
        exercises.forEachIndexed { i, exercise ->
            if (i > 0) RowDivider()
            ExerciseRow(
                exercise,
                names.getValue(exercise.key),
                exerciseFacts(exercise, equipment),
                selected = exercise.key == selected?.key,
                onPick = onPick?.takeIf { canPick(exercise) }?.let { pick -> { pick(exercise) } },
                opens = opens,
                tagYours = tagYours,
            )
        }
    }
}

/**
 * An exercise's name over its [facts], tagged when the user added it (or archived it), ticked when [selected].
 * One that [opens] on [onPick] ends in a chevron; one with no [onPick] takes no tap and shows none.
 */
@Composable
private fun ExerciseRow(
    exercise: Exercise,
    name: String,
    facts: String,
    selected: Boolean,
    onPick: (() -> Unit)?,
    opens: Boolean,
    tagYours: Boolean,
) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val tap = if (onPick == null) {
        Modifier
    } else {
        Modifier
            .clickable(interaction, LocalIndication.current, role = Role.Button, onClick = onPick)
            // After the ripple, so the content shrinks while the ripple keeps the card's full width.
            .pressScale(interaction)
    }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .then(tap)
            .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space8, bottom = Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = IntervalTheme.type.label, color = palette.text)
            Text(facts, style = IntervalTheme.type.meta, color = palette.textDim)
        }
        if (exercise is CustomExercise && (tagYours || exercise.archived)) {
            Text(
                stringResource(if (exercise.archived) Res.string.library_archived else Res.string.library_yours),
                style = IntervalTheme.type.caption,
                color = if (exercise.archived) palette.textDim else palette.accent,
            )
        }
        when {
            selected -> Icon(
                IntervalIcons.Check,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(Sizes.icon),
            )

            opens && onPick != null -> Icon(
                IntervalIcons.Chevron,
                contentDescription = null,
                tint = palette.textDim,
                modifier = Modifier.size(Sizes.icon),
            )
        }
    }
}

/** Nothing matches: the search, if there is one, how to find more, and with [onAdd] Add it as an exercise. */
@Composable
internal fun NoMatch(query: String, onAdd: ((name: String) -> Unit)?, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier.padding(vertical = Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        Text(
            if (query.isBlank()) {
                stringResource(Res.string.library_empty)
            } else {
                stringResource(Res.string.library_empty_query, query.trim())
            },
            style = IntervalTheme.type.label,
            color = palette.text,
        )
        Text(stringResource(Res.string.library_empty_hint), style = IntervalTheme.type.meta, color = palette.textDim)
        if (onAdd != null && query.isNotBlank()) {
            SecondaryButton(
                stringResource(Res.string.library_add_named, query.trim()),
                { onAdd(query.trim()) },
                Modifier.fillMaxWidth().padding(top = Spacing.space8),
            )
        }
    }
}

/** The longest search the field takes. */
private const val SEARCH_LENGTH = 40
