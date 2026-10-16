package dev.justinsmith.interval.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.timer.ConfigField

/** How each [ConfigField] is named and described wherever it is edited. */
val ConfigField.label: String
    get() = when (this) {
        ConfigField.Prep -> "Get ready"
        ConfigField.Work -> "Work"
        ConfigField.Rest -> "Rest"
        ConfigField.Rounds -> "Rounds"
    }

val ConfigField.hint: String
    get() = when (this) {
        ConfigField.Prep -> "Countdown before the first round"
        ConfigField.Work -> "Effort interval"
        ConfigField.Rest -> "Recovery between rounds"
        ConfigField.Rounds -> "Work intervals in the set"
    }

/** The phase stripe that ties a field to its workout colour; Rounds takes the accent. */
@Composable
@ReadOnlyComposable
fun ConfigField.stripe(): Color = when (this) {
    ConfigField.Prep -> PhaseColors.prep
    ConfigField.Work -> PhaseColors.work
    ConfigField.Rest -> PhaseColors.rest
    ConfigField.Rounds -> IntervalTheme.palette.accent
}

/** The pale wash of the field's colour behind its value. */
@Composable
@ReadOnlyComposable
fun ConfigField.tint(): Color = IntervalTheme.palette.tint(stripe())

/** The field's value on its [tint]: its own colour, except Get ready, whose amber is darkened to read. */
@Composable
@ReadOnlyComposable
fun ConfigField.valueColor(): Color =
    if (this == ConfigField.Prep) IntervalTheme.palette.onPrepTint else stripe()
