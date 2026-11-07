package dev.justinsmith.lift.acceptance

import dev.justinsmith.lift.data.BellEntity
import dev.justinsmith.lift.data.CustomProgramEntity
import dev.justinsmith.lift.data.FollowingEntity
import dev.justinsmith.lift.data.encodeDays
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `journeys/lift-plan-changes.xml`: day 1 of Goblet squat and row changed in the session (a heavier bell, a
 * set changed after logging, the rows skipped), and the change kept in a copy followed from day 2. Then a day
 * done in full that felt all out: its steps up wait, and leaving keeps the program as it was. And the ABC either
 * way: *20 minutes beside BJJ*'s ABC on the minute taken as many as you can for today, the program untouched.
 * And the bell waits: with 16 and 20 kg owned, 5 × 10 two-hand swings at 16 kg go denser, then a set more, then
 * one-hand at 16 kg, and only then back to two-hand at 20 kg, as they started.
 */
class LiftPlanChangesAcceptanceTest {
    @Test
    fun `a day changed in the session is kept in a copy of the program`() = liftScenario {
        followProgram("Goblet squat and row")
        startProgramDay(1)
        see("Up next · 1 of 9 sets")
        see("16 kg")
        seeNextLiftShown("Goblet squat")

        changeUpNext("24")
        see("24 kg")
        logNextSet()
        see("Up next · 2 of 9 sets")
        seeSet("24 kg × 10")
        seeMoved("240 kg")

        addARepTo("24 kg × 10")
        seeSet("24 kg × 11")
        seeMoved("264 kg")

        openTodaysPlan()
        see("Changed")
        see("1 of 3 done")
        skipBlock("Row")
        see("Up next · 2 of 3 sets")

        tap("Finish")
        see("Keep today's changes?")
        see("3 × 10 (10–15) → 3 × 10 (10–15) · 24 kg")
        tap("Every day")
        tap("Save as Goblet squat and row (yours)")
        see("Saved as Goblet squat and row (yours). You follow it from day 2.")
        val saved = app.storage.programs.rows.value.single()
        assertEquals("Goblet squat and row (yours)", saved.name)
        assertTrue(saved.days.contains("\"kg\":24.0"), saved.days)

        closeSummary()
        seeProgramDay("Day 2 of 8 · week 1", "Goblet squat and row (yours)", "Start day 2")
    }

    @Test
    fun `a day done in full that felt all out leaves its steps up off`() = liftScenario {
        followProgram("Goblet squat and row")
        startProgramDay(1)
        repeat(9) { logNextSet() }
        tap("Finish")
        see("Next time")
        see("3 × 10 (10–15) → 3 × 11 (10–15)")
        see("Save as Goblet squat and row (yours)")

        sayHowItWas("All out", "Cooked")
        see("It felt very hard: these wait until you turn them on.")
        seeNo("Save as Goblet squat and row (yours)")
        closeSummary()
        seeNo("Keep today's changes?")
        seeProgramDay("Day 2 of 8 · week 1", "Goblet squat and row", "Start day 2")
        assertTrue(app.storage.programs.rows.value.isEmpty())
    }

    @Test
    fun `the ABC on the minute is taken as many as you can for today and the program keeps it on the minute`() =
        liftScenario {
            followProgram("20 minutes beside BJJ")
            tap("Start day 1")
            see("Start EMOM")
            openTodaysPlan()
            tap("Armor Building Complex")
            tap("As many as you can")
            see("On the minute")
            see("Minutes")
            tap("Done")
            see("Up next · 15 min cap")
            see("Start the clock")
            assertTrue(app.storage.programs.rows.value.isEmpty())
            startTheClock()
            roundsDone(2, total = 2)
        }

    @Test
    fun `swings done in full before a big jump rest shorter first`() = liftScenario(swings(twoHand)) {
        doTheDay(sets = 5)
        seeNextTime(
            "Two-hand swing",
            "5 × 10 · 16 kg → 5 × 10 · 75 s rest · 16 kg",
            "Denser first: the same work in less rest",
        )
    }

    @Test
    fun `swings at the shortest rest take a set more`() =
        liftScenario(swings(twoHand.copy(rest = 30, origin = twoHand))) {
            doTheDay(sets = 5)
            seeNextTime(
                "Two-hand swing",
                "5 × 10 · 30 s rest · 16 kg → 6 × 10 · 30 s rest · 16 kg",
                "More work at 16 kg",
            )
        }

    @Test
    fun `swings with every set they can take move to one hand at the same bell`() =
        liftScenario(swings(twoHand.copy(rounds = 10, rest = 30, origin = twoHand))) {
            doTheDay(sets = 10)
            seeNextTime(
                "Two-hand swing → One-hand swing",
                "10 × 10 · 30 s rest · 16 kg → 5 × 10 a side · 16 kg",
                "Same bell, harder lift",
            )
            tap("Save to Swings")
            see("Saved to Swings.")
            val saved = app.storage.programs.rows.value.single().days
            assertTrue(saved.contains("OneHandSwing") && saved.contains("\"origin\""), saved)
        }

    @Test
    fun `one-hand swings with nothing left at 16 kg go back to two hands at 20 kg`() = liftScenario(
        swings(
            Block(
                BuiltInExercise.OneHandSwing,
                rounds = 10,
                reps = 10,
                load = Load.Bell(16.0),
                rest = 30,
                origin = twoHand,
            ),
        ),
    ) {
        doTheDay(sets = 20)
        seeNextTime(
            "One-hand swing → Two-hand swing",
            "10 × 10 a side · 30 s rest · 16 kg → 5 × 10 · 20 kg",
            "Back to where it started, at 20 kg",
        )
    }
}

/** 5 × 10 two-hand swings at 16 kg, where the swings ladder starts. */
private val twoHand = Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10, load = Load.Bell(16.0))

/** Storage with "Swings", one day of [block], followed, and the 16 and 20 kg bells owned. */
private fun swings(block: Block) = FakeLiftStorage().apply {
    programs.rows.value = listOf(CustomProgramEntity("p1", "Swings", 3, 4, encodeDays(listOf(listOf(block))), 0))
    following.row.value = FollowingEntity(programId = "custom:p1", startedAtEpochMillis = 0)
    bells.rows.value = listOf(BellEntity(16.0), BellEntity(20.0))
}
