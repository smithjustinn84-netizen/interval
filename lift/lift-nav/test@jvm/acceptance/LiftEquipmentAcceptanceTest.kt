package dev.justinsmith.lift.acceptance

import kotlin.test.Test

/**
 * `journeys/lift-equipment.xml`: one session logging a bell, a band, a hold on one side and a bodyweight set
 * with weight added, each in its own words, Moved counting only the weighed load; then a second, and the
 * balance of the two on the Lift tab.
 */
class LiftEquipmentAcceptanceTest {
    @Test
    fun `each kind of equipment is logged in its words and two sessions show a balance`() = liftScenario {
        seeNoSessionsYet()
        startSession()
        seeMoved("0 kg")

        openLogSheet()
        seeChosen("16")
        logFromSheet()
        seeSet("16 kg × 10")
        seeMoved("160 kg")
        seeResting("1:30")
        skipRest()

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
        seeNoBalanceYet()

        startSession()
        openLogSheet()
        pickExercise("Goblet squat", search = "goblet")
        logFromSheet()
        finish()
        closeSummary()
        seeBalance(
            "Glutes and hamstrings: 2 sets worked it most",
            "Quads and calves: 1 set worked it most",
            "Chest: 1 set worked it most",
            "Shoulders: 0 sets worked it most",
        )
    }
}
