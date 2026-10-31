package dev.justinsmith.lift.ui

import androidx.compose.ui.unit.dp

/** Lift's own fixed dimensions, beside core's [dev.justinsmith.interval.ui.Sizes]. */
object LiftSizes {
    /**
     * A day on a program's map: a round mark, smaller than the week strip's so seven and their week's label fit
     * a phone's card.
     */
    val programDayMark = 20.dp

    /** An EMOM's ring at its widest on a tablet or a desktop, where the readout sits inside it at any turn. */
    val ringMaxRoomy = 560.dp
}
