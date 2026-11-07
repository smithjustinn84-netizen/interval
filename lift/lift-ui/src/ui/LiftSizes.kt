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

    /** A density block's Round done: the one control tapped between rounds, so taller than a workout's. */
    val roundDone = 112.dp

    /** A muscle group's bar on the Lift tab's balance: thin, so seven of them and their names fit a phone's card. */
    val balanceBar = 8.dp

    /** A figure showing how an exercise goes, on its page: tall enough to read a swing's arc on a phone. */
    val exerciseDemo = 220.dp

    /** The same figure on the session's card about the next set, a band across its top: a swing's arc still reads. */
    val nextFigure = 140.dp

    /** The marker on a demo's timeline, the gap between its stretches, and the bar beside a cue it lights. */
    val demoMarkerWidth = 4.dp
    val demoMarkerHeight = 16.dp
    val demoStretchGap = 2.dp
    val cueBar = 6.dp

    /** A set-up's stretch at its narrowest on a demo's timeline, and the gap that keeps it apart from the rep. */
    val demoSetUpLeast = 36.dp
    val demoSetUpGap = 12.dp
}
