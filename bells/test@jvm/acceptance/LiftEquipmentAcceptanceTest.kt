package dev.justinsmith.bells.acceptance

import kotlin.test.Test

/**
 * `journeys/lift-equipment.xml`: one session logging a bell, a band, a hold on one side and a bodyweight set
 * with weight added, each in its own words, Moved counting only the weighed load.
 */
class LiftEquipmentAcceptanceTest {
    @Test
    fun `each kind of equipment is logged in its own words and only weight is moved`() = liftScenario {
        seeNoSessionsYet()
        startSession()
        seeMoved("0 kg")

        openLogSheet()
        seeChosen("16")
        logFromSheet()
        seeSet("16 kg × 10")
        seeMoved("160 kg")

        openLogSheet()
        openLibraryOnSheet()
        seeRecent("Two-hand swing")
        choose("Band")
        seeMatches("15 exercises", "Band pull-apart")
        tap("Band pull-apart")
        seeChosen("Medium")
        choose("Heavy")
        logFromSheet()
        seeSet("Heavy × 10")
        seeMoved("160 kg")

        openLogSheet()
        openLibraryOnSheet()
        tap("Clear")
        searchFor("side plank")
        seeMatches("1 exercise", "Side plank")
        tap("Side plank")
        seeNo("An EMOM")
        seeChosen("Left")
        holdFiveSecondsLonger(3)
        see("0:45")
        logFromSheet()
        seeSet("0:45 · Left")
        seeMoved("160 kg")

        openLogSheet()
        seeChosen("Right")
        pickExercise("Push-up", search = "pushup")
        choose("8")
        logFromSheet()
        seeSet("+8 kg × 10")
        seeMoved("240 kg")
        setsLogged(4)

        finish()
        seeRecord("Two-hand swing", "Heaviest 16 kg × 10")
        seeRecord("Band pull-apart", "Heaviest Heavy × 10")
        seeRecord("Side plank", "Longest 0:45")
        seeRecord("Push-up", "Most added +8 kg × 10")
        closeSummary()
        seeLastSession("Two-hand swing and 3 more", "4 sets", "240 kg")
    }
}
