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

    /** The front/back anatomical body map on exercise detail and session summaries, and compactly in balance cards. */
    val bodyMap = 168.dp
    val bodyMapCompact = 148.dp

    /** The symmetric barbell plate diagram and kettlebell rack silhouette. */
    val barbellDiagram = 56.dp
    val kettlebellRack = 72.dp

    /** The bar beside a cue a demo lights. */
    val cueBar = 6.dp

    /** A set-up's stretch at its narrowest on a demo's timeline, and the gap that keeps it apart from the rep. */
    val demoSetUpLeast = 36.dp
    val demoSetUpGap = 12.dp

    /** A trend's line on an exercise's page: compact, so months of sessions read in one glance. */
    val sparkline = 56.dp
    val sparklineStroke = 2.dp
    val sparklineDot = 6.dp
    val sparklineMark = 1.dp
    val sparklineLabels = 18.dp

    /**
     * The narrowest a flagship lift's card lays the diagram of its bell's path beside its cues and faults rather
     * than over them: room for both a phone's sheet wide at their narrowest. A sheet across a phone on its side is
     * that wide; the side sheet a tablet opens the page in is not.
     */
    val pathBesideNotes = 560.dp

    /** The grip's four weeks on Lift's Grip card: short bars under its lines. */
    val gripChart = 48.dp

    /** Strength's Following card movement hero plate, its top accent ribbon, and its corner registration ticks. */
    val programHeroHeight = 104.dp
    val programHeroAccentStrip = 2.dp
    val programHeroCornerTick = 8.dp
    val programHeroFigureWidth = 148.dp

    /** Leading movement icon tile on program blocks and recap lift rows. */
    val liftTile = 34.dp

    /** Circular benchmark progress dial beside each challenge on Strength. */
    val challengeGauge = 44.dp
    val challengeGaugeStroke = 3.5.dp

    /** Program completion recap hero plate, its completion ring dial, and lift progression track. */
    val recapHeroHeight = 120.dp
    val recapDial = 76.dp
    val recapDialStroke = 6.dp
    val recapTrackHeight = 14.dp
    val recapTrackStroke = 3.dp
    val recapTrackNode = 4.dp
}
