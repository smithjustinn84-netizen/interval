package dev.justinsmith.interval.ui

import androidx.compose.ui.unit.dp

/** The app shell's own fixed dimensions, beside core's [Sizes]: its panes, and About's mark. */
internal object AppSizes {
    /**
     * Width of a list beside its detail on a wide window (History beside a workout): a phone's, so the list
     * lays out as it does on a phone.
     */
    val listPane = 412.dp

    /** Width of Settings when it opens beside the Timer. */
    val settingsPane = 400.dp

    /**
     * Narrowest window that opens Settings beside the Timer rather than over it: room for the rail,
     * [settingsPane] and a Timer still as wide as a phone's.
     */
    val twoPaneMinWidth = 1000.dp

    /** The app's mark on the About screen: the launcher icon's coin, at a size that reads as a logo. */
    val aboutMark = 112.dp
}
