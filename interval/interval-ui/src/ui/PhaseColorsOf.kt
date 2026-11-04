package dev.justinsmith.interval.ui

import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.timer.Phase

/** The colour the workout shows for [phase]: its ring, band, word and primary control. */
fun WorkoutColors.of(phase: Phase): Color = when (phase) {
    // The warm-up winds up like get ready, the cool-down winds down like rest.
    Phase.Prep, Phase.WarmUp -> prep

    Phase.Work -> work

    Phase.Rest, Phase.CoolDown -> rest

    Phase.SetRest -> setRest

    Phase.Done -> done
}
