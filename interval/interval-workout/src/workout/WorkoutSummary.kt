package dev.justinsmith.interval.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.platform.prefersReducedMotion
import dev.justinsmith.interval.presets.PresetDraft
import dev.justinsmith.interval.presets.PresetEditorHost
import dev.justinsmith.interval.presets.rememberPresetDraft
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.Burst
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EnterEasing
import dev.justinsmith.interval.ui.HowWasIt
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.ProgressRing
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.presetNameOrCustom
import dev.justinsmith.interval.ui.rememberEntrance
import dev.justinsmith.interval.ui.risingEnter
import dev.justinsmith.interval.ui.workoutDescription
import dev.justinsmith.interval.workout.resources.Res
import dev.justinsmith.interval.workout.resources.common_done
import dev.justinsmith.interval.workout.resources.common_joined
import dev.justinsmith.interval.workout.resources.effort_all_out
import dev.justinsmith.interval.workout.resources.effort_easy
import dev.justinsmith.interval.workout.resources.effort_hard
import dev.justinsmith.interval.workout.resources.effort_steady
import dev.justinsmith.interval.workout.resources.effort_very_hard
import dev.justinsmith.interval.workout.resources.history_goal_met
import dev.justinsmith.interval.workout.resources.history_streak
import dev.justinsmith.interval.workout.resources.summary_how_was_it
import dev.justinsmith.interval.workout.resources.summary_note_placeholder
import dev.justinsmith.interval.workout.resources.summary_week_count
import dev.justinsmith.interval.workout.resources.summary_week_of_goal
import dev.justinsmith.interval.workout.resources.workout_done
import dev.justinsmith.interval.workout.resources.workout_ended
import dev.justinsmith.interval.workout.resources.workout_repeat
import dev.justinsmith.interval.workout.resources.workout_save_preset
import dev.justinsmith.interval.workout.resources.workout_saved
import dev.justinsmith.interval.workout.resources.workout_share
import dev.justinsmith.interval.workout.resources.workout_stat_calories
import dev.justinsmith.interval.workout.resources.workout_stat_rounds
import dev.justinsmith.interval.workout.resources.workout_stat_rounds_value
import dev.justinsmith.interval.workout.resources.workout_stat_time
import dev.justinsmith.interval.workout.resources.workout_summary_ended
import dev.justinsmith.interval.workout.resources.workout_summary_finished
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What the workout came to: Done (or Ended), its shape, time and rounds, that it was saved, and what
 * next. Save as preset shows only for a shape no preset has. It arrives in order, top to bottom,
 * the ring sweeping closed as the lines rise in beneath it: a moment's ceremony for the finish.
 */
@Composable
internal fun SummaryLayout(
    state: WorkoutUiState,
    landscape: Boolean,
    onRepeat: () -> Unit,
    onSavePreset: (Preset) -> Unit,
    onExit: () -> Unit,
    onShare: (String) -> Unit,
    reflection: SummaryReflection,
) {
    var draft by rememberPresetDraft()
    val healthNotes = healthNotes(state)
    LaunchedEffect(healthNotes) { reflection.onDescribeForHealth(healthNotes) }
    val onSave = { draft = PresetDraft.saveCurrent(state.config) }
    val shareText = workoutShareText(state)
    val share = { onShare(shareText) }
    val entrance = rememberEntrance()
    val padding = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .then(
            if (landscape) {
                Modifier.padding(
                    start = Sizes.phaseBand + Spacing.space20,
                    end = Spacing.space20,
                    top = Spacing.space16,
                    bottom = Spacing.space16,
                )
            } else {
                Modifier.padding(
                    top = Sizes.phaseBand + Spacing.space16,
                    bottom = Spacing.space16,
                    start = Spacing.space20,
                    end = Spacing.space20,
                )
            },
        )
    Box(Modifier.fillMaxSize()) {
        // The workout that brings the week to its goal gets the intro's ripple, as the ring closes.
        if (state.week?.justMet == true) {
            val colors = IntervalTheme.workout
            Burst(
                listOf(colors.prep, colors.work, colors.rest),
                Modifier.matchParentSize(),
                delayMillis = Motion.SUMMARY_RING_MILLIS,
            )
        }
        if (landscape) {
            Row(
                padding,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space32),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SummaryReadout(state, entrance, ring = false, reflection, Modifier.weight(1f))
                SummaryActions(
                    state,
                    entrance,
                    onRepeat,
                    onSave = onSave,
                    onExit = onExit,
                    onShare = share,
                    modifier = Modifier.width(Sizes.setupColumnLandscape),
                )
            }
        } else {
            Column(padding) {
                SummaryReadout(state, entrance, ring = true, reflection, Modifier.weight(1f).fillMaxWidth())
                SummaryActions(
                    state,
                    entrance,
                    onRepeat,
                    onSave = onSave,
                    onExit = onExit,
                    onShare = share,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    PresetEditorHost(
        draft = draft,
        onDraftChange = { draft = it },
        prepSeconds = state.config.prepSeconds,
        presets = state.presets,
        onSave = { _, preset -> onSavePreset(preset) },
    )
}

@Composable
private fun SummaryActions(
    state: WorkoutUiState,
    entrance: MutableTransitionState<Boolean>,
    onRepeat: () -> Unit,
    onSave: () -> Unit,
    onExit: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(entrance, modifier, enter = risingEnter(Motion.STAGGER_MILLIS * ACTIONS_STEP)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                BigButton(
                    stringResource(Res.string.workout_repeat),
                    onRepeat,
                    Modifier.weight(1f).heightIn(min = Sizes.save),
                )
                if (state.canSavePreset) {
                    BigButton(
                        stringResource(Res.string.workout_save_preset),
                        onSave,
                        Modifier.weight(1f).heightIn(min = Sizes.save),
                    )
                }
            }
            QuietTextButton(
                stringResource(Res.string.workout_share),
                onShare,
                Modifier.align(Alignment.CenterHorizontally),
            )
            BigButton(
                stringResource(Res.string.common_done),
                onExit,
                Modifier.fillMaxWidth().heightIn(min = Sizes.control),
                background = IntervalTheme.workout.done,
                content = IntervalTheme.workout.onFill(IntervalTheme.workout.done),
                style = IntervalTheme.type.value,
            )
        }
    }
}

/**
 * The title, then the lines, stats and "How was it?" under it, in a column that scrolls when they don't fit. In
 * the [ring], the ring gives way first: it takes the height the rest leaves, down to its least.
 */
@Composable
private fun SummaryReadout(
    state: WorkoutUiState,
    entrance: MutableTransitionState<Boolean>,
    ring: Boolean,
    reflection: SummaryReflection,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val viewport = constraints.maxHeight
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (ring) {
                RingOverDetails(
                    viewport,
                    ring = { Column { SummaryTitle(state, entrance, ring = true) } },
                    details = { SummaryDetails(state, entrance, reflection) },
                )
            } else {
                SummaryTitle(state, entrance, ring = false)
                SummaryDetails(state, entrance, reflection)
            }
        }
    }
}

/**
 * The [ring] over the [details], as large as the [viewport]'s height leaves it: at most its full size, at least
 * [IntervalSizes.summaryRingLeast]. It is drawn whole and scaled, so Done shrinks with it; it is measured after
 * the details and in layout alone, so it never shows at one size and then another. Short of its least, the
 * readout scrolls.
 */
@Composable
private fun RingOverDetails(viewport: Int, ring: @Composable () -> Unit, details: @Composable () -> Unit) {
    Layout(contents = listOf(ring, details)) { (ringMeasurables, detailMeasurables), constraints ->
        val below = detailMeasurables.single().measure(constraints.copy(minHeight = 0))
        val full = IntervalSizes.summaryRing.roundToPx()
        // The actions' own gap stays clear under the details, so the note field never meets Repeat.
        val room = viewport - below.height - Spacing.space12.roundToPx()
        val size = room.coerceIn(IntervalSizes.summaryRingLeast.roundToPx(), full)
        val drawn = ringMeasurables.single().measure(Constraints.fixed(full, full))
        val width = maxOf(below.width, size)
        layout(width, size + below.height) {
            drawn.placeWithLayer((width - full) / 2, 0) {
                scaleX = size.toFloat() / full
                scaleY = size.toFloat() / full
                transformOrigin = TransformOrigin(pivotFractionX = 0.5f, pivotFractionY = 0f)
            }
            below.place((width - below.width) / 2, size)
        }
    }
}

/** Under the title: the shape, the stats, that it was saved, the week, and "How was it?", rising in turn. */
@Composable
private fun SummaryDetails(
    state: WorkoutUiState,
    entrance: MutableTransitionState<Boolean>,
    reflection: SummaryReflection,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(entrance, enter = risingEnter(Motion.STAGGER_MILLIS * LINES_STEP)) {
            SummaryLines(state)
        }
        AnimatedVisibility(entrance, enter = risingEnter(Motion.STAGGER_MILLIS * STATS_STEP)) {
            Row(
                Modifier.padding(top = Spacing.space32).widthIn(max = Sizes.content),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
            ) {
                Stat(
                    formatClock(countUp(state.elapsedSeconds)),
                    stringResource(Res.string.workout_stat_time),
                    Modifier.weight(1f),
                )
                Stat(
                    stringResource(Res.string.workout_stat_rounds_value, countUp(state.completedRounds), state.rounds),
                    stringResource(Res.string.workout_stat_rounds),
                    Modifier.weight(1f),
                )
                state.calories?.let { calories ->
                    Stat(
                        countUp(calories).toString(),
                        stringResource(Res.string.workout_stat_calories),
                        Modifier.weight(1f),
                    )
                }
            }
        }
        AnimatedVisibility(entrance, enter = risingEnter(Motion.STAGGER_MILLIS * SAVED_STEP)) {
            Row(
                Modifier.padding(top = Spacing.space20),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Icon(
                    IntervalIcons.Check,
                    contentDescription = null,
                    tint = IntervalTheme.workout.onGroundDim,
                    modifier = Modifier.size(Sizes.icon),
                )
                Text(
                    stringResource(Res.string.workout_saved),
                    style = IntervalTheme.type.meta,
                    color = IntervalTheme.workout.onGroundDim,
                )
            }
        }
        state.week?.let { week ->
            AnimatedVisibility(entrance, enter = risingEnter(Motion.STAGGER_MILLIS * WEEK_STEP)) {
                Text(
                    weekText(week),
                    style = IntervalTheme.type.meta,
                    color = IntervalTheme.workout.onGround,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = Spacing.space8),
                )
            }
        }
        AnimatedVisibility(entrance, enter = risingEnter(Motion.STAGGER_MILLIS * HOW_STEP)) {
            HowWasIt(
                title = stringResource(Res.string.summary_how_was_it),
                efforts = PerceivedEffort.entries,
                effort = state.effort,
                effortLabel = { effortWord(it) },
                onEffortChange = reflection.onEffortChange,
                note = state.note,
                onNoteChange = reflection.onNoteChange,
                notePlaceholder = stringResource(Res.string.summary_note_placeholder),
                noteMaxLength = PerceivedEffort.NOTE_MAX_LENGTH,
                modifier = Modifier.padding(top = Spacing.space24).widthIn(max = Sizes.content),
            )
        }
    }
}

/** What the summary's "How was it?" does: keeps the effort and the note, and hands on their words. */
class SummaryReflection(
    val onEffortChange: (PerceivedEffort) -> Unit = {},
    val onNoteChange: (String) -> Unit = {},
    /** The effort and note in words for the health store's note, null with neither said. */
    val onDescribeForHealth: (String?) -> Unit = {},
)

/** The word for [effort]: "Easy" through "All out". */
@Composable
internal fun effortWord(effort: PerceivedEffort): String = stringResource(
    when (effort) {
        PerceivedEffort.Easy -> Res.string.effort_easy
        PerceivedEffort.Steady -> Res.string.effort_steady
        PerceivedEffort.Hard -> Res.string.effort_hard
        PerceivedEffort.VeryHard -> Res.string.effort_very_hard
        PerceivedEffort.AllOut -> Res.string.effort_all_out
    },
)

/** The effort and the note as one line, "Hard · Felt strong", either alone, or null with neither. */
@Composable
internal fun healthNotes(state: WorkoutUiState): String? {
    val word = state.effort?.let { effortWord(it) }
    val note = state.note.trim().ifEmpty { null }
    return when {
        word != null && note != null -> stringResource(Res.string.common_joined, word, note)
        else -> word ?: note
    }
}

/** "3 of 4 this week", "Goal met this week · 3-week streak", or with no goal set, the count. */
@Composable
private fun weekText(week: WeekStanding): String {
    val goal = week.goal
    val standing = when {
        goal == null -> pluralStringResource(Res.plurals.summary_week_count, week.count, week.count)
        week.goalMet -> stringResource(Res.string.history_goal_met)
        else -> stringResource(Res.string.summary_week_of_goal, week.count, goal)
    }
    return if (week.streak > 1) {
        stringResource(
            Res.string.common_joined,
            standing,
            pluralStringResource(Res.plurals.history_streak, week.streak, week.streak),
        )
    } else {
        standing
    }
}

/**
 * [value] counted up from nothing as the summary arrives, over the time its ring takes to close; at
 * once under reduced motion, where a running number would be the one thing that moved.
 */
@Composable
private fun countUp(value: Int): Int {
    val reduced = prefersReducedMotion()
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val shown by animateIntAsState(
        if (started || reduced) value else 0,
        tween(Motion.SUMMARY_RING_MILLIS, easing = EnterEasing),
        label = "countUp",
    )
    return shown
}

/**
 * Done or Ended, in a [ring] that sweeps closed on a finished workout and stops at the rounds an
 * ended one got through; sideways, over the round marks instead.
 */
@Composable
private fun ColumnScope.SummaryTitle(state: WorkoutUiState, entrance: MutableTransitionState<Boolean>, ring: Boolean) {
    val title = @Composable {
        Text(
            stringResource(if (state.ended) Res.string.workout_ended else Res.string.workout_done),
            style = IntervalTheme.type.display,
            color = IntervalTheme.workout.done,
            modifier = Modifier.semantics { heading() },
        )
    }
    if (ring) {
        val reduced = prefersReducedMotion()
        val target = state.completedRounds.toFloat() / state.rounds
        val sweep = remember { Animatable(if (reduced) target else 0f) }
        LaunchedEffect(target, reduced) {
            if (reduced) {
                sweep.snapTo(target)
            } else {
                sweep.animateTo(target, tween(Motion.SUMMARY_RING_MILLIS, easing = EnterEasing))
            }
        }
        Box(Modifier.size(IntervalSizes.summaryRing), contentAlignment = Alignment.Center) {
            ProgressRing(sweep.value, IntervalTheme.workout.done, Modifier.fillMaxSize(), glide = false)
            Column { AnimatedVisibility(entrance, enter = risingEnter()) { title() } }
        }
    } else {
        AnimatedVisibility(entrance, enter = risingEnter()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                RoundDots(state, IntervalTheme.workout.done, Modifier.padding(bottom = Spacing.space12))
                title()
            }
        }
    }
}

/** "8 rounds · 5:55 total" (or how far an ended workout got) over the workout's name and shape. */
@Composable
private fun SummaryLines(state: WorkoutUiState, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (state.ended) {
                pluralStringResource(
                    Res.plurals.workout_summary_ended,
                    state.rounds,
                    state.completedRounds,
                    state.rounds,
                    state.elapsed,
                )
            } else {
                pluralStringResource(Res.plurals.workout_summary_finished, state.rounds, state.rounds, state.total)
            },
            style = IntervalTheme.type.section,
            color = IntervalTheme.workout.onGround,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.space8),
        )
        Text(
            shapeText(state),
            style = IntervalTheme.type.meta,
            color = IntervalTheme.workout.onGroundDim,
            textAlign = TextAlign.Center,
        )
    }
}

/** The summary's lines arrive in this order, each [Motion.STAGGER_MILLIS] steps after the title. */
private const val LINES_STEP = 2
private const val STATS_STEP = 4
private const val SAVED_STEP = 6
private const val WEEK_STEP = 7
private const val HOW_STEP = 8
private const val ACTIONS_STEP = 9

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .semantics(mergeDescendants = true) {}
            .clip(Corners.radius18)
            .background(IntervalTheme.workout.control)
            .padding(start = Spacing.space8, end = Spacing.space8, top = Spacing.space16, bottom = Spacing.space12),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = IntervalTheme.type.stat, color = IntervalTheme.workout.onGround)
        Text(
            label,
            style = IntervalTheme.type.caption,
            color = IntervalTheme.workout.onGroundDim,
            modifier = Modifier.padding(top = Spacing.space4),
        )
    }
}

/** The workout's name and shape: "Tabata · 20s on · 10s off · ×8", or "Custom · …" for one no preset has. */
@Composable
private fun shapeText(state: WorkoutUiState): String = stringResource(
    Res.string.common_joined,
    presetNameOrCustom(state.presetName),
    workoutDescription(state.config),
)
