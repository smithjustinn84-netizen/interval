package dev.justinsmith.bells.library

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.ExerciseHistory
import dev.justinsmith.bells.domain.PastSession
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.detail_best
import dev.justinsmith.bells.resources.detail_done
import dev.justinsmith.bells.resources.detail_edit
import dev.justinsmith.bells.resources.detail_history
import dev.justinsmith.bells.resources.detail_never
import dev.justinsmith.bells.resources.detail_open_session
import dev.justinsmith.bells.resources.exercise_archived_note
import dev.justinsmith.bells.resources.exercise_cues
import dev.justinsmith.bells.resources.exercise_editor_edit
import dev.justinsmith.bells.ui.RecordRow
import dev.justinsmith.bells.ui.exerciseFacts
import dev.justinsmith.bells.ui.exerciseName
import dev.justinsmith.bells.ui.setLine
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.pressScale
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A past session with an exercise, and the day it was, as the platform words it: "Sunday, Oct 11". */
data class DatedSession(val session: PastSession, val date: String)

/**
 * One exercise as its page shows it: the [exercise], the user's [history] with it, the day it was [lastDone]
 * and its [recent] sessions, each with its date.
 */
data class ExerciseDetailUiState(
    val exercise: Exercise,
    val history: ExerciseHistory = ExerciseHistory(),
    val lastDone: String = "",
    val recent: List<DatedSession> = emptyList(),
)

/**
 * An exercise's page, as a sheet over the library, beside it on a tablet: its name and facts, its cues, then
 * the user's history with it, their best and the sessions it was in, each opening on [onOpenSession]. One of
 * the user's own offers Edit, which turns the sheet into its form until it is saved or archived.
 */
@Composable
fun ExerciseDetailSheet(state: ExerciseDetailUiState, onOpenSession: (uid: String) -> Unit, onDismiss: () -> Unit) {
    val exercise = state.exercise
    var editing by rememberSaveable { mutableStateOf(false) }
    val title = if (editing) stringResource(Res.string.exercise_editor_edit) else exerciseName(exercise)
    IntervalSheet(title, onDismiss = onDismiss) { hide ->
        val modifier = Modifier.padding(horizontal = Spacing.space20, vertical = Spacing.space8)
        if (editing && exercise is CustomExercise) {
            val editor = rememberExerciseEditor(exercise.uid, "")
            ExerciseEditorForm(
                editor.state,
                onChange = editor::change,
                onSave = { typed, cues -> if (editor.save(typed, cues)) hide() },
                onArchive = {
                    editor.toggleArchived()
                    hide()
                },
                modifier = modifier,
            )
        } else {
            Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                ExerciseHead(exercise, onEdit = if (exercise is CustomExercise) ({ editing = true }) else null)
                History(state, onOpen = { uid ->
                    hide()
                    onOpenSession(uid)
                })
            }
        }
    }
}

/** The exercise's name over its facts, a note if archived, its cues, and Edit with [onEdit]. */
@Composable
private fun ColumnScope.ExerciseHead(exercise: Exercise, onEdit: (() -> Unit)?) {
    val palette = IntervalTheme.palette
    Column {
        Text(exerciseName(exercise), style = IntervalTheme.type.section, color = palette.text)
        Text(exerciseFacts(exercise), style = IntervalTheme.type.meta, color = palette.textDim)
    }
    if ((exercise as? CustomExercise)?.archived == true) {
        Text(stringResource(Res.string.exercise_archived_note), style = IntervalTheme.type.meta, color = palette.text)
    }
    (exercise as? CustomExercise)?.cues?.takeIf { it.isNotBlank() }?.let { cues ->
        GroupLabel(stringResource(Res.string.exercise_cues))
        ListCard {
            Text(
                cues,
                style = IntervalTheme.type.body,
                color = palette.text,
                modifier = Modifier.padding(Spacing.space16),
            )
        }
    }
    onEdit?.let { SecondaryButton(stringResource(Res.string.detail_edit), it, Modifier.fillMaxWidth()) }
}

/**
 * The user's history with it: how often and when last, their best, and its recent sessions, each a card of
 * its sets under its date that opens the session on [onOpen]; or that it is not logged yet.
 */
@Composable
private fun ColumnScope.History(state: ExerciseDetailUiState, onOpen: (uid: String) -> Unit) {
    val palette = IntervalTheme.palette
    val history = state.history
    GroupLabel(stringResource(Res.string.detail_history))
    if (history.timesDone == 0) {
        Text(stringResource(Res.string.detail_never), style = IntervalTheme.type.meta, color = palette.textDim)
        return
    }
    Text(
        pluralStringResource(Res.plurals.detail_done, history.timesDone, history.timesDone, state.lastDone),
        style = IntervalTheme.type.meta,
        color = palette.textDim,
    )
    if (history.records.isNotEmpty()) {
        GroupLabel(stringResource(Res.string.detail_best))
        ListCard {
            history.records.forEachIndexed { i, record ->
                if (i > 0) RowDivider()
                RecordRow(record)
            }
        }
    }
    state.recent.forEach { dated -> PastSessionCard(dated, onOpen) }
}

/** A past session's sets with the exercise under its date, the whole card opening the session. */
@Composable
private fun PastSessionCard(dated: DatedSession, onOpen: (uid: String) -> Unit) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    GroupLabel(dated.date)
    ListCard {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.listRow)
                .clickable(
                    interaction,
                    LocalIndication.current,
                    role = Role.Button,
                    onClickLabel = stringResource(Res.string.detail_open_session),
                ) { onOpen(dated.session.uid) }
                .pressScale(interaction)
                .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space8, bottom = Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
                dated.session.sets.forEach { set ->
                    Text(setLine(set), style = IntervalTheme.type.body, color = palette.text)
                }
            }
            Icon(
                IntervalIcons.Chevron,
                contentDescription = null,
                tint = palette.textDim,
                modifier = Modifier.size(Sizes.icon),
            )
        }
    }
}
