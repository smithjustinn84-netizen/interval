package dev.justinsmith.bells.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgramProgressTest {
    private val swings = Block(BuiltInExercise.TwoHandSwing, rounds = 3, reps = 10)
    private val squats = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 5)
    private val program =
        Program(ProgramKey.Custom("p"), listOf(listOf(swings, squats)), daysPerWeek = 2, weeks = 2, name = "Mine")
    private val followed = Followed(program, startedAtEpochMillis = 100)

    private fun set(exercise: Exercise, kg: Double = 16.0, reps: Int = 10, at: Long = 0) =
        SetLog(0, exercise, kg, reps, null, at)

    /** A finished session of [program] started [at], its [day] as stored, with [sets]. */
    private fun session(
        at: Long,
        day: Int?,
        sets: List<SetLog>,
        program: ProgramKey? = this.program.key,
        plan: SessionPlan? = null,
    ) = LiftSession("s$at", at, at + 1, "Mine", sets, program, plan, day)

    private fun dayDone(at: Long, day: Int?, program: ProgramKey? = this.program.key) =
        session(
            at,
            day,
            List(3) {
                set(BuiltInExercise.TwoHandSwing)
            } + List(3) { set(BuiltInExercise.GobletSquat, reps = 5) },
            program,
        )

    /** Newest first, as the repository holds them. */
    private fun progress(vararg sessions: LiftSession, followed: Followed = this.followed) =
        programProgress(followed, sessions.sortedByDescending { it.startedAtEpochMillis })

    @Test
    fun `stored days number the sessions and a legacy row follows the one before it`() {
        assertEquals(listOf(1, 2, 3, 4), dayNumbers(listOf(null, null, 3, null), daysBefore = 0))
        assertEquals(listOf(3), dayNumbers(listOf(null), daysBefore = 2))
        assertEquals(emptyList(), dayNumbers(emptyList(), daysBefore = 2))
    }

    @Test
    fun `the last day done is the highest so a deleted middle day moves nothing back`() {
        assertEquals(3, lastDayDone(listOf(1, 3), daysBefore = 0))
        assertEquals(2, lastDayDone(emptyList(), daysBefore = 2))
        val progress = progress(dayDone(100, 1), dayDone(300, 3))
        assertEquals(4, progress.next?.number)
        assertEquals(3, progress.lastDay)
        assertEquals(1, progress.daysLeft)
        assertEquals(DayStanding.Done, progress.standing(1))
        assertEquals(DayStanding.Missed, progress.standing(2))
        assertEquals(DayStanding.Done, progress.standing(3))
        assertEquals(DayStanding.Next, progress.standing(4))
    }

    @Test
    fun `a copy's days before count as done with no session to open`() {
        val progress = progress(dayDone(200, 3), followed = followed.copy(daysBefore = 2))
        assertEquals(listOf(1, 2, 3), progress.done.map { it.number })
        assertNull(progress.done[0].session)
        assertNull(progress.done[1].session)
        assertEquals("s200", progress.done[2].session?.uid)
        assertEquals(DayStanding.DoneBefore, progress.standing(1))
        assertEquals(DayStanding.Next, progress.standing(4))
        assertEquals(DayStanding.ToCome, progress.standing(5))
    }

    @Test
    fun `sets done of planned follow the session's plan`() {
        val half = session(100, 1, List(3) { set(BuiltInExercise.TwoHandSwing) })
        assertEquals(3 to 6, progress(half).done.single().let { it.setsDone to it.setsPlanned })
        val skipped = half.copy(plan = SessionPlan.of(program.days[0]).skip(1, done = 0))
        assertEquals(3 to 3, progress(skipped).done.single().let { it.setsDone to it.setsPlanned })
    }

    @Test
    fun `records set during the program are judged against what came before it`() {
        val before = session(10, null, listOf(set(BuiltInExercise.Press, 20.0)), program = null)
        val run = session(200, 1, listOf(set(BuiltInExercise.Press, 24.0)))
        val records = progress(before, run).records
        assertEquals(24.0, (records.single() as PersonalRecord.Weighted).heaviestKg)
        // A free session during the run neither counts for it nor against it.
        val free = session(150, null, listOf(set(BuiltInExercise.Press, 28.0)), program = null)
        val lighter = session(200, 1, listOf(set(BuiltInExercise.Press, 20.0)))
        assertTrue(progress(before, free, lighter).records.isEmpty())
        assertEquals(10, progress(before, free, lighter).reps)
    }

    @Test
    fun `a day past the program's end counts as done and ends it`() {
        val progress = progress(dayDone(100, 5))
        assertEquals(5, progress.lastDay)
        assertEquals(0, progress.daysLeft)
        assertTrue(progress.finished)
        assertEquals(DayStanding.Missed, progress.standing(4))
    }

    @Test
    fun `sessions of another program or from before following are not days of it`() {
        val other = dayDone(200, 1, program = ProgramKey.BuiltIn(ProgramId.EasyStrength))
        val earlier = dayDone(50, 1)
        val progress = progress(other, earlier, dayDone(300, 1))
        assertEquals(listOf("s300"), progress.done.map { it.session?.uid })
        assertEquals(2, progress.next?.number)
        assertEquals(progress.done.single().session?.tonnageKg, progress.tonnageKg)
    }

    @Test
    fun `before the first day nothing is done and day one is next`() {
        val progress = progress()
        assertTrue(progress.done.isEmpty())
        assertEquals(1, progress.next?.number)
        assertEquals(4, progress.daysLeft)
        assertEquals(DayStanding.Next, progress.standing(1))
    }
}
