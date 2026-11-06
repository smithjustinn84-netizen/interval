package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasContentDescription

// Log's week, beside the driver, which is at its size limit.

/** Log's week bar of [kind], as it is read out: "Sport days: 1 of 1". */
fun IntervalAcceptanceDriver.seeKindBar(kind: String, done: Int, of: Int) =
    see(hasContentDescription("$kind: $done of $of"))

/** Log's week lists [name] under what is coming up. */
fun IntervalAcceptanceDriver.seeComingUp(name: String) {
    see("Coming up")
    see(name)
}
