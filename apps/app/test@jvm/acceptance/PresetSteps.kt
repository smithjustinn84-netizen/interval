package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText

// Conditioning's Timer card, beside the driver, which is at its size limit.

/** The Timer row on Conditioning reads [reading]: the preset on the Timer by name, or the shape when it is nobody's. */
fun IntervalAcceptanceDriver.seeTimerRow(reading: String) =
    see(hasText("Timer") and hasText(reading) and hasClickAction())
