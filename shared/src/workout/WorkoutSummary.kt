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
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.MAX_ROUND_MARKS
import dev.justinsmith.interval.ui.PhaseColors
import dev.justinsmith.interval.ui.SavePresetDialog
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing

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
            summary = state.shape,
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
            BigButton("Repeat", onRepeat, Modifier.weight(1f).height(Sizes.save))
            if (state.canSavePreset) {
                BigButton("Save as preset", onSave, Modifier.weight(1f).height(Sizes.save))
            }
        }
        BigButton(
            "Done",
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
        Text(if (state.ended) "Ended" else "Done", style = IntervalTheme.type.done, color = PhaseColors.onPhase)
        Text(
            state.summary,
            style = IntervalTheme.type.summary,
            color = PhaseColors.onPhase90,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.space8),
        )
        Text(state.shape, style = IntervalTheme.type.meta, color = PhaseColors.onPhase85, textAlign = TextAlign.Center)
        Row(
            Modifier.padding(top = Spacing.space28).widthIn(max = Sizes.content),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space10),
        ) {
            Stat(state.elapsed, "Time", Modifier.weight(1f))
            Stat("${state.completedRounds}/${state.rounds}", "Rounds", Modifier.weight(1f))
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
            Text("Saved to history", style = IntervalTheme.type.meta, color = PhaseColors.onPhase85)
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
