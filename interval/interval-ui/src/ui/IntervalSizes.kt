package dev.justinsmith.interval.ui

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The interval side's own fixed dimensions, beside core's [Sizes]: the Timer's pills and start, the workout's
 * controls and marks, History's charts and calendar, the sheets' steppers.
 */
object IntervalSizes {
    /** Width of each side rail of workout controls in landscape. */
    val rail = 120.dp

    /** Start workout's height, and the Timer's square start button. */
    val start = 72.dp

    /** Elevation of Start workout, the only raised element (`shadow-start`). */
    val startElevation = 8.dp

    /** The play glyph inside Start workout. */
    val playIcon = 26.dp

    /** The largest a Timer stepper value grows to when the row has room; it never shrinks below a stat. */
    val stepperValueMax = 72.sp

    /** A value in its phase-tinted pill on the Timer. */
    val valuePill = 48.dp

    /** The smaller value pill on a Settings row. */
    val valuePillSmall = 40.dp

    /** The hero card's workout timeline, and the gap between its segments. */
    val timeline = 12.dp
    val timelineGap = 2.dp

    /** The tallest bar of the History tab's week chart. */
    val weekBar = 56.dp

    /** The ring of this week's workouts against the weekly goal, and its stroke. */
    val goalRing = 76.dp
    val goalRingStroke = 8.dp

    /** A day on History's month calendar: the cell that takes the tap, and the round mark of its state. */
    val calendarCell = 48.dp
    val calendarDay = 34.dp

    /** The short bar under today on the calendar. */
    val todayMarkWidth = 14.dp
    val todayMarkHeight = 3.dp

    /** The pill of phase colour at the start of a History row. */
    val historyMarkHeight = 40.dp

    /** The stripe beside a value sheet's title, as tall as the title and its hint. */
    val sheetHeaderStripe = 48.dp

    /** Room the workout leaves under its ring for the "Tap anywhere to pause" line. */
    val tapHint = 48.dp

    /** A phase's colour stripe at the start of a field row, and a history mark's width. */
    val stripe = 6.dp

    /** A round's mark on the workout screen: a short bar, narrowing when many share a row. */
    val roundMarkWidth = 24.dp
    val roundMarkHeight = 6.dp

    /** The ring around Done on the summary. */
    val summaryRing = 260.dp

    /** The least the summary's ring shrinks to, on a phone too short for it and "How was it?" both. */
    val summaryRingLeast = 160.dp

    /** Minimum height of a Timer interval row. */
    val intervalRow = 72.dp

    /** The edit sheet's − / + buttons, a size up from [stepper]. */
    val sheetStepper = 68.dp
}
