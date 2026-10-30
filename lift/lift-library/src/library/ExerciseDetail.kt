package dev.justinsmith.lift.library

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.fadeThroughContent
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.ExerciseHistory
import dev.justinsmith.lift.domain.Muscle
import dev.justinsmith.lift.domain.Muscles
import dev.justinsmith.lift.domain.PastSession
import dev.justinsmith.lift.domain.Trend
import dev.justinsmith.lift.domain.UnknownExercise
import dev.justinsmith.lift.domain.collapsedSets
import dev.justinsmith.lift.domain.muscles
import dev.justinsmith.lift.library.resources.Res
import dev.justinsmith.lift.library.resources.common_joined
import dev.justinsmith.lift.library.resources.detail_also
import dev.justinsmith.lift.library.resources.detail_best
import dev.justinsmith.lift.library.resources.detail_done
import dev.justinsmith.lift.library.resources.detail_edit
import dev.justinsmith.lift.library.resources.detail_history
import dev.justinsmith.lift.library.resources.detail_never
import dev.justinsmith.lift.library.resources.detail_no_cues
import dev.justinsmith.lift.library.resources.detail_open_session
import dev.justinsmith.lift.library.resources.detail_sets_repeated
import dev.justinsmith.lift.library.resources.detail_works
import dev.justinsmith.lift.library.resources.exercise_archived_note
import dev.justinsmith.lift.library.resources.exercise_cues
import dev.justinsmith.lift.library.resources.exercise_editor_edit
import dev.justinsmith.lift.ui.RecordRow
import dev.justinsmith.lift.ui.cues
import dev.justinsmith.lift.ui.exerciseFacts
import dev.justinsmith.lift.ui.exerciseName
import dev.justinsmith.lift.ui.muscleName
import dev.justinsmith.lift.ui.setLine
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringArrayResource
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
    /** Its best each session, once there are two to compare. */
    val trend: Trend? = null,
)

/**
 * An exercise's page, as a sheet over the library, beside it on a tablet: its name and facts, its cues, then
 * the user's history with it, their best, their best each session, and the sessions it was in, each opening
 * on [onOpenSession] when there
 * is one. One of the user's own offers Edit while [editable], which turns the sheet into its form until it is
 * saved or archived: not from a session, whose sets and plan hold the exercise as it is.
 */
@Composable
fun ExerciseDetailSheet(
    state: ExerciseDetailUiState,
    onOpenSession: ((uid: String) -> Unit)?,
    onDismiss: () -> Unit,
    editable: Boolean = true,
) {
    val exercise = state.exercise
    var editing by rememberSaveable { mutableStateOf(false) }
    val title = if (editing) stringResource(Res.string.exercise_editor_edit) else exerciseName(exercise)
    IntervalSheet(title, onDismiss = onDismiss) { hide ->
        val modifier = Modifier.padding(vertical = Spacing.space8)
        // The page and the form take one another's place with a fade through, as a session's faces do.
        AnimatedContent(
            targetState = editing && exercise is CustomExercise,
            transitionSpec = { fadeThroughContent() },
            label = "exerciseDetail",
        ) { form ->
            if (form && exercise is CustomExercise) {
                val editor = rememberExerciseEditor(exercise.uid, "")
                val editorState by editor.state.collectAsStateWithLifecycle()
                ExerciseEditorForm(
                    editorState,
                    onChange = editor::change,
                    onSave = { typed, cues -> if (editor.save(typed, cues)) hide() },
                    onArchive = {
                        editor.toggleArchived()
                        hide()
                    },
                    modifier = modifier,
                )
            } else {
                ExerciseDetailContent(
                    state,
                    onEdit = if (editable && exercise is CustomExercise) ({ editing = true }) else null,
                    onOpenSession = onOpenSession?.let { open ->
                        { uid ->
                            hide()
                            open(uid)
                        }
                    },
                    modifier = modifier,
                )
            }
        }
    }
}

/**
 * The page itself, in the sheet or in place on the Log sheet: the exercise, Edit when [onEdit] is given, then
 * the user's history with it, each past session opening on [onOpenSession] when it is given.
 */
@Composable
fun ExerciseDetailContent(
    state: ExerciseDetailUiState,
    onEdit: (() -> Unit)?,
    onOpenSession: ((uid: String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        ExerciseHead(state.exercise, onEdit)
        History(state, onOpenSession)
    }
}

/**
 * The exercise's name over its facts, with Edit beside them for one of the user's own ([onEdit]); a note if
 * archived; then its cues, or a hint to add some, and for a built-in what it works.
 */
@Composable
private fun ColumnScope.ExerciseHead(exercise: Exercise, onEdit: (() -> Unit)?) {
    val palette = IntervalTheme.palette
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                exerciseName(exercise),
                style = IntervalTheme.type.section,
                color = palette.text,
                modifier = Modifier.semantics { heading() },
            )
            Text(exerciseFacts(exercise), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        onEdit?.let { QuietTextButton(stringResource(Res.string.detail_edit), it) }
    }
    if ((exercise as? CustomExercise)?.archived == true) {
        Text(stringResource(Res.string.exercise_archived_note), style = IntervalTheme.type.meta, color = palette.text)
    }
    when (exercise) {
        is BuiltInExercise -> {
            Cues(stringArrayResource(exercise.cues()))
            Works(exercise.muscles)
        }

        is CustomExercise -> if (exercise.cues.isBlank()) {
            Text(stringResource(Res.string.detail_no_cues), style = IntervalTheme.type.meta, color = palette.textDim)
        } else {
            Cues(listOf(exercise.cues))
        }

        is UnknownExercise -> Unit
    }
}

/** How to do it, a line each, in a card. */
@Composable
private fun Cues(lines: List<String>) {
    val palette = IntervalTheme.palette
    GroupLabel(stringResource(Res.string.exercise_cues))
    ListCard(fill = IntervalTheme.palette.surfaceVariant) {
        lines.forEachIndexed { i, line ->
            if (i > 0) RowDivider()
            Text(
                line,
                style = IntervalTheme.type.body,
                color = palette.text,
                modifier = Modifier.padding(Spacing.space16),
            )
        }
    }
}

/** What it works most, then what it works [Muscles.also]: "Glutes · Hamstrings", "Also Lower back · Abs". */
@Composable
private fun Works(muscles: Muscles) {
    val palette = IntervalTheme.palette
    GroupLabel(stringResource(Res.string.detail_works))
    ListCard(fill = IntervalTheme.palette.surfaceVariant) {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(joined(muscles.primary), style = IntervalTheme.type.label, color = palette.text)
            if (muscles.also.isNotEmpty()) {
                Text(
                    stringResource(Res.string.detail_also, joined(muscles.also)),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                )
            }
        }
    }
}

/** Muscles in a line: "Glutes · Hamstrings · Lower back". */
@Composable
private fun joined(muscles: List<Muscle>): String =
    muscles.map { muscleName(it) }.reduce { line, name -> stringResource(Res.string.common_joined, line, name) }

/**
 * The user's history with it: how often and when last, their best, and its recent sessions, each a card of
 * its sets under its date that opens the session on [onOpen], if given; or that it is not logged yet.
 */
@Composable
private fun ColumnScope.History(state: ExerciseDetailUiState, onOpen: ((uid: String) -> Unit)?) {
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
        ListCard(fill = IntervalTheme.palette.surfaceVariant) {
            history.records.forEachIndexed { i, record ->
                if (i > 0) RowDivider()
                // Named only when one bell and two each have one, to tell them apart.
                RecordRow(record, named = history.records.size > 1)
            }
        }
    }
    state.trend?.let { TrendCard(it) }
    state.recent.forEach { dated -> PastSessionCard(dated, onOpen) }
}

/**
 * A past session's sets with the exercise under its date, the same set in a row told once with how many, the
 * whole card opening the session when there is [onOpen].
 */
@Composable
private fun PastSessionCard(dated: DatedSession, onOpen: ((uid: String) -> Unit)?) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    GroupLabel(dated.date)
    ListCard(fill = IntervalTheme.palette.surfaceVariant) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = Sizes.listRow)
                .then(
                    if (onOpen == null) {
                        Modifier
                    } else {
                        Modifier
                            .clickable(
                                interaction,
                                LocalIndication.current,
                                role = Role.Button,
                                onClickLabel = stringResource(Res.string.detail_open_session),
                            ) { onOpen(dated.session.uid) }
                            .pressScale(interaction)
                    },
                )
                .padding(start = Spacing.space16, end = Spacing.space12, top = Spacing.space8, bottom = Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
                collapsedSets(dated.session.sets).forEach { run ->
                    Text(
                        if (run.count == 1) {
                            setLine(run.set)
                        } else {
                            pluralStringResource(
                                Res.plurals.detail_sets_repeated,
                                run.count,
                                run.count,
                                setLine(run.set),
                            )
                        },
                        style = IntervalTheme.type.body,
                        color = palette.text,
                    )
                }
            }
            if (onOpen != null) {
                Icon(
                    IntervalIcons.Chevron,
                    contentDescription = null,
                    tint = palette.textDim,
                    modifier = Modifier.size(Sizes.icon),
                )
            }
        }
    }
}
