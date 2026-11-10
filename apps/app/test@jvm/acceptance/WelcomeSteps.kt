package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasText

// The welcome's steps and the First week card's, apart from the driver they extend: it is as large as a class may be.

/** The opening page, which a new install opens on. */
fun IntervalAcceptanceDriver.seeWelcome() = see("Three pages, then four questions.")

/** A tab's page of the welcome, by its title. */
fun IntervalAcceptanceDriver.seeWelcomePage(title: String) = see(title)

/** Past the welcome from its opening page, to Set up. */
fun IntervalAcceptanceDriver.skipWelcome() {
    seeWelcome()
    tap("Skip to Set up")
    seeSetUp()
}

/** Today's First week card, and how far through it. */
fun IntervalAcceptanceDriver.seeFirstWeek(done: Int, of: Int) {
    scrollTo("First week · $done of $of")
    see("First week · $done of $of")
}

fun IntervalAcceptanceDriver.seeNoFirstWeek() = seeNo(hasText("First week", substring = true))

/** Whether the card is on, as it is kept. */
fun IntervalAcceptanceDriver.firstWeekKept(on: Boolean) = waitFor("First week card on: $on") {
    app.repository.settings.appSettings.value.firstWeek == on
}
