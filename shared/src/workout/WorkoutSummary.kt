package dev.justinsmith.interval.workout

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.presets.PresetDraft
import dev.justinsmith.interval.presets.PresetEditorHost
import dev.justinsmith.interval.presets.rememberPresetDraft
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_done
import dev.justinsmith.interval.resources.common_joined
import dev.justinsmith.interval.resources.workout_done
import dev.justinsmith.interval.resources.workout_ended
import dev.justinsmith.interval.resources.workout_repeat
import dev.justinsmith.interval.resources.workout_save_preset
import dev.justinsmith.interval.resources.workout_saved
import dev.justinsmith.interval.resources.workout_stat_rounds
import dev.justinsmith.interval.resources.workout_stat_rounds_value
import dev.justinsmith.interval.resources.workout_stat_time
import dev.justinsmith.interval.resources.workout_summary_ended
import dev.justinsmith.interval.resources.workout_summary_finished
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.EnterEasing
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.MAX_ROUND_MARKS
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.configDescription
import dev.justinsmith.interval.ui.enterRising
import dev.justinsmith.interval.ui.presetNameOrCustom
import dev.justinsmith.interval.ui.rememberEntrance
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
) {
    var draft by rememberPresetDraft()
    val onSave = { draft = PresetDraft.saveCurrent(state.config) }
    val entrance = rememberEntrance()
    val padding = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .padding(horizontal = Spacing.space20, vertical = Spacing.space16)
    if (landscape) {
        Row(
            padding,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space32),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryReadout(state, entrance, ring = false, Modifier.weight(1f))
            SummaryActions(
                state,
                entrance,
                onRepeat,
                onSave = onSave,
                onExit = onExit,
                modifier = Modifier.width(Sizes.setupColumnLandscape),
            )
        }
    } else {
        Column(padding) {
            SummaryReadout(state, entrance, ring = true, Modifier.weight(1f).fillMaxWidth())
            SummaryActions(
                state,
                entrance,
                onRepeat,
                onSave = onSave,
                onExit = onExit,
                modifier = Modifier.fillMaxWidth(),
            )
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
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(entrance, modifier, enter = enterRising(Motion.STAGGER_MILLIS * ACTIONS_STEP)) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space12)) {
                BigButton(stringResource(Res.string.workout_repeat), onRepeat, Modifier.weight(1f).height(Sizes.save))
                if (state.canSavePreset) {
                    BigButton(
                        stringResource(Res.string.workout_save_preset),
                        onSave,
                        Modifier.weight(1f).height(Sizes.save),
                    )
                }
            }
            BigButton(
                stringResource(Res.string.common_done),
                onExit,
                Modifier.fillMaxWidth().height(Sizes.control),
                background = PhaseColors.done,
                content = PhaseColors.ink,
                style = IntervalTheme.type.value,
            )
        }
    }
}

@Composable
private fun SummaryReadout(
    state: WorkoutUiState,
    entrance: MutableTransitionState<Boolean>,
    ring: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SummaryTitle(state, entrance, ring)
        AnimatedVisibility(entrance, enter = enterRising(Motion.STAGGER_MILLIS * LINES_STEP)) {
            SummaryLines(state)
        }
        AnimatedVisibility(entrance, enter = enterRising(Motion.STAGGER_MILLIS * STATS_STEP)) {
            Row(
                Modifier.padding(top = Spacing.space32).widthIn(max = Sizes.content),
                horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
            ) {
                Stat(state.elapsed, stringResource(Res.string.workout_stat_time), Modifier.weight(1f))
                Stat(
                    stringResource(Res.string.workout_stat_rounds_value, state.completedRounds, state.rounds),
                    stringResource(Res.string.workout_stat_rounds),
                    Modifier.weight(1f),
                )
            }
        }
        AnimatedVisibility(entrance, enter = enterRising(Motion.STAGGER_MILLIS * SAVED_STEP)) {
            Row(
                Modifier.padding(top = Spacing.space20),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Icon(
                    IntervalIcons.Check,
                    contentDescription = null,
                    tint = PhaseColors.onGroundDim,
                    modifier = Modifier.size(Sizes.icon),
                )
                Text(
                    stringResource(Res.string.workout_saved),
                    style = IntervalTheme.type.meta,
                    color = PhaseColors.onGroundDim,
                )
            }
        }
    }
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
            color = PhaseColors.done,
        )
    }
    if (ring) {
        val sweep = remember { Animatable(0f) }
        LaunchedEffect(state.completedRounds, state.rounds) {
            sweep.animateTo(
                state.completedRounds.toFloat() / state.rounds,
                tween(Motion.SUMMARY_RING_MILLIS, easing = EnterEasing),
            )
        }
        Box(Modifier.size(Sizes.summaryRing), contentAlignment = Alignment.Center) {
            ProgressRing(sweep.value, PhaseColors.done, Modifier.fillMaxSize(), glide = false)
            Column { AnimatedVisibility(entrance, enter = enterRising()) { title() } }
        }
    } else {
        AnimatedVisibility(entrance, enter = enterRising()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.rounds <= MAX_ROUND_MARKS) {
                    RoundDots(state, PhaseColors.done, Modifier.padding(bottom = Spacing.space12))
                }
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
            color = PhaseColors.onGround,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.space8),
        )
        Text(
            shapeText(state),
            style = IntervalTheme.type.meta,
            color = PhaseColors.onGroundDim,
            textAlign = TextAlign.Center,
        )
    }
}

/** The summary's lines arrive in this order, each [Motion.STAGGER_MILLIS] steps after the title. */
private const val LINES_STEP = 2
private const val STATS_STEP = 4
private const val SAVED_STEP = 6
private const val ACTIONS_STEP = 8

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(Corners.radius18)
            .background(PhaseColors.control)
            .padding(top = Spacing.space16, bottom = Spacing.space12),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = IntervalTheme.type.stat, color = PhaseColors.onGround)
        Text(
            label,
            style = IntervalTheme.type.caption,
            color = PhaseColors.onGroundDim,
            modifier = Modifier.padding(top = Spacing.space4),
        )
    }
}

/** The workout's name and shape: "Tabata · 20s on · 10s off · ×8", or "Custom · …" for one no preset has. */
@Composable
private fun shapeText(state: WorkoutUiState): String = stringResource(
    Res.string.common_joined,
    presetNameOrCustom(state.presetName),
    configDescription(state.config),
)
