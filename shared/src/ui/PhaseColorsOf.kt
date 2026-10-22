package dev.justinsmith.interval.ui

import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.timer.Phase

/** The colour the workout shows for [phase]: its ring, band, word and primary control. */
fun WorkoutColors.of(phase: Phase): Color = when (phase) {
    Phase.Prep -> prep
    Phase.Work -> work
    Phase.Rest -> rest
    Phase.Done -> done
}
