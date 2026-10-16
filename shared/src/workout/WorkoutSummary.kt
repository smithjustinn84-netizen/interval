package dev.justinsmith.interval.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.common_custom
import dev.justinsmith.interval.resources.common_done
import dev.justinsmith.interval.resources.common_joined
import dev.justinsmith.interval.resources.preset_default_name
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
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.MAX_ROUND_MARKS
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.SavePresetDialog
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.configDescription
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * What the workout came to: Done (or Ended), its shape, time and rounds, that it was saved, and what
 * next. Save as preset shows only for a shape no preset has.
 */
@Composable
internal fun SummaryLayout(
    state: WorkoutUiState,
    landscape: Boolean,
    onRepeat: () -> Unit,
    onSavePreset: (String) -> Unit,
    onDone: () -> Unit,
) {
    var saving by rememberSaveable { mutableStateOf(false) }
    val padding = Modifier
        .fillMaxSize()
        .safeDrawingPadding()
        .padding(horizontal = Spacing.space20, vertical = Spacing.space16)
    if (landscape) {
        Row(
            padding,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space28),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SummaryReadout(state, Modifier.weight(1f))
            SummaryActions(state, onRepeat, { saving = true }, onDone, Modifier.width(Sizes.setupColumnLandscape))
        }
    } else {
        Column(padding) {
            SummaryReadout(state, Modifier.weight(1f).fillMaxWidth())
            SummaryActions(state, onRepeat, { saving = true }, onDone, Modifier.fillMaxWidth())
        }
    }
    if (saving) {
        SavePresetDialog(
            summary = shapeText(state),
            defaultName = stringResource(Res.string.preset_default_name, state.nextCustomNumber),
            onSave = {
                onSavePreset(it)
                saving = false
            },
            onDismiss = { saving = false },
        )
    }
}

@Composable
private fun SummaryActions(
    state: WorkoutUiState,
    onRepeat: () -> Unit,
    onSave: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space10)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space10)) {
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
            onDone,
            Modifier.fillMaxWidth().height(Sizes.control),
            background = PhaseColors.onPhase,
            content = PhaseColors.ink,
            style = IntervalTheme.type.primaryControl,
        )
    }
}

@Composable
private fun SummaryReadout(state: WorkoutUiState, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (state.rounds <= MAX_ROUND_MARKS) RoundDots(state, Modifier.padding(bottom = Spacing.space12))
        Text(
            stringResource(if (state.ended) Res.string.workout_ended else Res.string.workout_done),
            style = IntervalTheme.type.done,
            color = PhaseColors.onPhase,
        )
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
            style = IntervalTheme.type.summary,
            color = PhaseColors.onPhase90,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.space8),
        )
        Text(
            shapeText(state),
            style = IntervalTheme.type.meta,
            color = PhaseColors.onPhase85,
            textAlign = TextAlign.Center,
        )
        Row(
            Modifier.padding(top = Spacing.space28).widthIn(max = Sizes.content),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space10),
        ) {
            Stat(state.elapsed, stringResource(Res.string.workout_stat_time), Modifier.weight(1f))
            Stat(
                stringResource(Res.string.workout_stat_rounds_value, state.completedRounds, state.rounds),
                stringResource(Res.string.workout_stat_rounds),
                Modifier.weight(1f),
            )
        }
        Row(
            Modifier.padding(top = Spacing.space20),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.space6),
        ) {
            Icon(
                IntervalIcons.Check,
                contentDescription = null,
                tint = PhaseColors.onPhase85,
                modifier = Modifier.size(Sizes.icon),
            )
            Text(
                stringResource(Res.string.workout_saved),
                style = IntervalTheme.type.meta,
                color = PhaseColors.onPhase85,
            )
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(Corners.radius16)
            .background(PhaseColors.glass)
            .padding(top = Spacing.space16, bottom = Spacing.space12),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = IntervalTheme.type.stat, color = PhaseColors.onPhase)
        Text(
            label,
            style = IntervalTheme.type.metaStrong,
            color = PhaseColors.onPhase,
            modifier = Modifier.padding(top = Spacing.space4),
        )
    }
}

/** The workout's name and shape: "Tabata · 20s on · 10s off · ×8", or "Custom · …" for one no preset has. */
@Composable
private fun shapeText(state: WorkoutUiState): String = stringResource(
    Res.string.common_joined,
    state.presetName ?: stringResource(Res.string.common_custom),
    configDescription(state.config),
)
