package dev.justinsmith.interval.platform

import dev.justinsmith.interval.platform.resources.Res
import dev.justinsmith.interval.platform.resources.common_joined
import dev.justinsmith.interval.platform.resources.cue_left_side
import dev.justinsmith.interval.platform.resources.cue_next_move
import dev.justinsmith.interval.platform.resources.cue_right_side
import dev.justinsmith.interval.platform.resources.cue_switch_sides
import dev.justinsmith.interval.platform.resources.flow_move_count
import dev.justinsmith.interval.timer.FlowSide
import dev.justinsmith.interval.timer.FlowStep
import dev.justinsmith.interval.timer.Phase
import org.jetbrains.compose.resources.getString

/**
 * What a notification says of a flow's interval of [phase] at [step], in place of the phase's own word: the move
 * and its side while it is held ("Pigeon · Left side"), "Switch sides" between the two, and the move coming in a
 * change-over ("Next: Pigeon").
 */
internal suspend fun flowTitle(phase: Phase, step: FlowStep): String = when (phase) {
    Phase.Work -> when (step.side) {
        FlowSide.Left -> getString(Res.string.common_joined, step.move.name, getString(Res.string.cue_left_side))
        FlowSide.Right -> getString(Res.string.common_joined, step.move.name, getString(Res.string.cue_right_side))
        null -> step.move.name
    }

    Phase.Rest -> getString(Res.string.cue_switch_sides)

    else -> getString(Res.string.cue_next_move, step.move.name)
}

/** Where [step] is among the flow's moves: "Move 3 of 6". */
internal suspend fun flowCount(step: FlowStep): String = getString(Res.string.flow_move_count, step.number, step.of)
