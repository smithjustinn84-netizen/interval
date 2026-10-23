package dev.justinsmith.bells.library

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
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * The exercise library: a search, the equipment to narrow to, and behind Filters the focus, what it is
 * measured by and only the user's own; then the recent ones, while nothing narrows, and all that match. With
 * [onPick] a row picks its exercise ([canPick] says which can be), the [selected] one ticked; without, the
 * rows just inform. With [onAdd], a search that finds nothing offers to add it as the user's own. Only an
 * [archivable] picker, the library's, offers the user's archived exercises among its filters.
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
    val query = rememberTextFieldState()
    var more by rememberSaveable { mutableStateOf(false) }
    val filters = list.filters
    val names = list.exercises.associate { it.key to exerciseName(it) }
    val text = query.text.toString()
    val shown = list.exercises.filter { filters.matches(it, list.owned) && nameMatches(names.getValue(it.key), text) }
    val narrowed = text.isNotBlank() || !filters.isEmpty
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        val search = stringResource(Res.string.library_search)
        NameField(query, placeholder = search, onDone = {}, label = search, maxLength = SEARCH_LENGTH)
        FilterChips(Equipment.entries, filters.equipment, { equipmentName(it) }) { e ->
            onFilters { it.copy(equipment = it.equipment.toggled(e)) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                pluralStringResource(Res.plurals.library_count, shown.size, shown.size),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.palette.textDim,
                modifier = Modifier.weight(1f),
            )
            if (narrowed) {
                QuietTextButton(stringResource(Res.string.library_clear), {
                    query.clearText()
                    onFilters { ExerciseFilters() }
                })
            }
            QuietTextButton(
                if (filters.moreCount > 0) {
                    stringResource(Res.string.library_filters_count, filters.moreCount)
                } else {
                    stringResource(Res.string.library_filters)
                },
                { more = !more },
            )
        }
        if (more) MoreFilters(filters, onFilters, archivable)
        if (!narrowed && list.recents.isNotEmpty()) {
            GroupLabel(stringResource(Res.string.library_recent))
            ExerciseList(list.recents, names, selected, onPick, canPick)
            GroupLabel(stringResource(Res.string.library_all))
        }
        if (shown.isEmpty()) {
            NoMatch(text, onAdd)
        } else {
            ExerciseList(shown, names, selected, onPick, canPick)
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

@Composable
private fun ExerciseList(
    exercises: List<Exercise>,
    names: Map<String, String>,
    selected: Exercise?,
    onPick: ((Exercise) -> Unit)?,
    canPick: (Exercise) -> Boolean,
) {
    ListCard {
        exercises.forEachIndexed { i, exercise ->
            if (i > 0) RowDivider()
            ExerciseRow(
                exercise,
                names.getValue(exercise.key),
                selected = exercise.key == selected?.key,
                onPick = onPick?.takeIf { canPick(exercise) }?.let { pick -> { pick(exercise) } },
            )
        }
    }
}

/** An exercise's name over its facts, tagged when the user added it (or archived it), ticked when [selected]. */
@Composable
private fun ExerciseRow(exercise: Exercise, name: String, selected: Boolean, onPick: (() -> Unit)?) {
    val palette = IntervalTheme.palette
    val tap = if (onPick == null) {
        Modifier
    } else {
        Modifier.clickable(remember { MutableInteractionSource() }, LocalIndication.current, role = Role.Button) {
            onPick()
        }
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
            Text(exerciseFacts(exercise), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        if (exercise is CustomExercise) {
            Text(
                stringResource(if (exercise.archived) Res.string.library_archived else Res.string.library_yours),
                style = IntervalTheme.type.caption,
                color = if (exercise.archived) palette.textDim else palette.accent,
            )
        }
        if (selected) {
            Icon(
                IntervalIcons.Check,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(Sizes.icon),
            )
        }
    }
}

/** Nothing matches: the search, if there is one, how to find more, and with [onAdd] Add it as an exercise. */
@Composable
private fun NoMatch(query: String, onAdd: ((name: String) -> Unit)?) {
    val palette = IntervalTheme.palette
    Column(Modifier.padding(vertical = Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
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
