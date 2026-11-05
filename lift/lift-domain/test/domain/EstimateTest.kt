package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EstimateTest {
    private val rest = RestClock.DEFAULT_SECONDS

    private val squats = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 5)
    private val presses = Block(BuiltInExercise.Press, rounds = 3, reps = 5)
    private val swingsOnTheMinute = Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = 10, kind = BlockKind.Emom)
    private val planks = Block(BuiltInExercise.Plank, rounds = 3, reps = 30)

    private fun day(vararg blocks: Block, plan: SessionPlan? = null) = ProgramDay(
        Program(ProgramKey.Custom("p"), listOf(blocks.toList()), daysPerWeek = 3, weeks = 4),
        number = 1,
        week = 1,
        blocks = blocks.toList(),
        plan = plan,
    )

    private fun minutes(id: ProgramId): List<Int> =
        program(id).let { program -> program.days.indices.map { program.day(it + 1).estimatedMinutes(rest) } }

    @Test
    fun `sets take their reps at the lift's pace and a rest between them`() {
        // 15 squats at 3 seconds each, and a rest after the first set and the second.
        assertEquals(15 * 3 + 2 * 90, day(squats).estimatedSeconds(rest))
    }

    @Test
    fun `a one-sided lift's two sides share a rest`() {
        // 5 presses a side three times over: 30 reps, and a rest after two of the three pairs.
        assertEquals(30 * 3 + 2 * 90, day(presses).estimatedSeconds(rest))
    }

    @Test
    fun `an EMOM lasts its minutes and its count-in with no rest inside`() {
        assertEquals(10 * 60 + 10, day(swingsOnTheMinute).estimatedSeconds(rest))
    }

    @Test
    fun `a hold lasts its seconds and its count-in`() {
        assertEquals(3 * (30 + 3) + 2 * 90, day(planks).estimatedSeconds(rest))
    }

    @Test
    fun `a rest follows the sets before an EMOM but none follows the day's last set`() {
        // Before the EMOM every set of squats is rested from; after it they are the day's last.
        assertEquals(15 * 3 + 3 * 90 + 610, day(squats, swingsOnTheMinute).estimatedSeconds(rest))
        assertEquals(610 + 15 * 3 + 2 * 90, day(swingsOnTheMinute, squats).estimatedSeconds(rest))
    }

    @Test
    fun `a longer rest makes a longer day`() {
        assertEquals(15 * 3 + 2 * 180, day(squats).estimatedSeconds(restSeconds = 180))
    }

    @Test
    fun `a block skipped today counts only the sets it kept`() {
        val skipped = SessionPlan.of(listOf(squats, presses)).skip(index = 1, done = 0)
        assertEquals(day(squats).estimatedSeconds(rest), day(squats, presses, plan = skipped).estimatedSeconds(rest))
    }

    @Test
    fun `a day is said to the nearest five minutes and never less than five`() {
        // Under four minutes of squats; about 16 of the 15-minute plan's lifting day.
        assertEquals(5, day(squats).estimatedMinutes(rest))
        assertEquals(15, program(ProgramId.FifteenMinuteKettlebell).day(1).estimatedMinutes(rest))
    }

    @Test
    fun `a day that is all on the minute is its minutes exactly`() {
        assertEquals(10, day(swingsOnTheMinute).estimatedMinutes(rest))
        assertEquals(12, minutes(ProgramId.ProgramMinimum).first())
    }

    @Test
    fun `a program's days run from its shortest to its longest`() {
        // 12 minutes of swings one day; 5 get-ups a side the other, about 13 minutes with the rests.
        assertEquals(12..15, program(ProgramId.ProgramMinimum).dayMinutes(rest))
        assertEquals(15..15, program(ProgramId.FifteenMinuteKettlebell).dayMinutes(rest))
    }

    /** The built-ins whose summaries name a length: a change to a program or to the estimate says so here. */
    @Test
    fun `the built-ins that name their length come out at it`() {
        assertEquals(listOf(15, 15), minutes(ProgramId.FifteenMinuteKettlebell))
        assertEquals(20, minutes(ProgramId.ArmorBuildingFormula).first())
        assertEquals(listOf(20), minutes(ProgramId.CleanPressEmom))
        assertEquals(12, minutes(ProgramId.ProgramMinimum).first())
        assertEquals(listOf(30, 30), minutes(ProgramId.IronCardio))
        assertEquals(listOf(20, 30), minutes(ProgramId.BodyweightEmom))
        assertEquals(listOf(24, 24), minutes(ProgramId.RoundConditioning))
        assertEquals(listOf(10, 12, 5), minutes(ProgramId.SnatchTestPrep))
        assertEquals(listOf(15, 15, 15), minutes(ProgramId.FirstHundredSwings))
    }

    @Test
    fun `every built-in day comes out between five minutes and an hour and a quarter`() {
        val outside = builtInPrograms.flatMap { program ->
            program.days.indices.map { program.key to program.day(it + 1).estimatedMinutes(rest) }
        }.filter { (_, minutes) -> minutes !in 5..75 }
        assertTrue(outside.isEmpty(), "Outside 5 to 75 minutes: $outside")
    }
}
