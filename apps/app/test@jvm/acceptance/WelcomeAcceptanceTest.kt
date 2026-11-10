package dev.justinsmith.interval.acceptance

import dev.justinsmith.interval.fakes.FakeStorage
import kotlin.test.Test

/**
 * A new install's first open (`journeys/welcome.xml`): the welcome and its three pages on the way to Set up, and
 * the First week card Today shows from then until it is hidden.
 */
class WelcomeAcceptanceTest {
    @Test
    fun `a new install is shown round on the way to set up`() = acceptanceScenario(firstRun = true) {
        seeWelcome()
        tap("Get started")
        seeWelcomePage("Today picks for you")
        tap("Next")
        seeWelcomePage("Three ways to train")
        // Back steps a page back, not out of the welcome.
        pressSystemBack()
        seeWelcomePage("Today picks for you")
        tap("Next")
        tap("Next")
        seeWelcomePage("Everything counts toward the week")
        tap("Set up my week")
        seeSetUp()
        // Set up took the welcome's place: Back from it is Today.
        pressSystemBack()
        seeTodayTab()
    }

    @Test
    fun `any page of the welcome skips to set up`() = acceptanceScenario(firstRun = true) {
        seeWelcome()
        tap("Get started")
        seeWelcomePage("Today picks for you")
        tap("Skip")
        seeSetUp()
    }

    @Test
    fun `back from the opening page leaves for today`() = acceptanceScenario(firstRun = true) {
        seeWelcome()
        pressSystemBack()
        seeTodayTab()
        seeFirstWeek(0, 3)
    }

    @Test
    fun `the first week is ticked off on today and hidden for good`() {
        val storage = FakeStorage()
        acceptanceScenario(storage = storage, firstRun = true) {
            skipWelcome()
            answerSport("Climbing")
            trainOn("Thursday")
            ownBells(12)
            sessionOf(20)
            buildMyWeek()

            // The week is built, which is the first of four: a workout, a flow and a day on the wall are to come.
            seeFirstWeek(1, 4)
            scrollTo("Log it")
            logClassFromToday()
            seeFirstWeek(2, 4)

            scrollTo("Hide")
            tap("Hide")
            seeNoFirstWeek()
            firstWeekKept(false)
        }
        acceptanceScenario(storage = storage, firstRun = true) {
            seeTodayTab()
            seeNoFirstWeek()
        }
    }

    @Test
    fun `with set up skipped the first week leads back to it`() = acceptanceScenario(firstRun = true) {
        skipWelcome()
        tap("Skip for now")
        seeTodayTab()

        seeFirstWeek(0, 3)
        scrollTo("Build the week")
        tap("Build the week")
        seeSetUp()
    }

    @Test
    fun `the first week leads to the flows`() = acceptanceScenario(firstRun = true) {
        skipWelcome()
        tap("Skip for now")
        seeFirstWeek(0, 3)
        scrollTo("Follow a flow")
        tap("Follow a flow")
        seeMobility()
    }

    @Test
    fun `someone who had the app before has no first week`() = acceptanceScenario {
        seeTodayTab()
        seeNoFirstWeek()
        firstWeekKept(false)
    }
}
