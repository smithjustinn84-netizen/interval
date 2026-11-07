package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MINUTE = 60_000L
private const val DAY = 86_400_000L

class BenchmarksTest {
    private fun set(
        exercise: Exercise,
        reps: Int,
        at: Long,
        kg: Double = 16.0,
    ) = SetLog.of(0, exercise, kg, reps, null, at)

    private fun session(uid: String, vararg sets: SetLog) = LiftSession(
        uid,
        0,
        sets.maxOf {
            it.loggedAtEpochMillis
        },
        "",
        sets.toList(),
    )

    @Test
    fun `both swings count toward a lifetime and the milestone crossed is named`() {
        val sets = listOf(
            set(BuiltInExercise.TwoHandSwing, 600, 0),
            set(BuiltInExercise.OneHandSwing, 500, MINUTE),
            set(BuiltInExercise.Snatch, 50, 2 * MINUTE),
        )
        assertEquals(1_100, lifetimeReps(BuiltInExercise.OneHandSwing, sets))
        assertEquals(50, lifetimeReps(BuiltInExercise.Snatch, sets))
        assertEquals(1_000, crossedMilestone(900, 1_100))
        assertEquals(10_000, crossedMilestone(900, 12_000))
        assertNull(crossedMilestone(1_100, 1_200))
    }

    @Test
    fun `the snatch test counts the most snatches in any five minutes logged as they were done`() {
        // Ten sets of 10 a minute apart: any five minutes hold five of them.
        val minutes = session("a", *Array(10) { set(BuiltInExercise.Snatch, 10, (it + 1) * MINUTE) })
        assertEquals(50, mostInWindow(BuiltInExercise.Snatch, listOf(minutes), SNATCH_TEST_MINUTES))
        // A hundred entered together afterwards: no test.
        val batch = session("b", *Array(5) { set(BuiltInExercise.Snatch, 20, MINUTE + it * 1_000L) })
        assertNull(mostInWindow(BuiltInExercise.Snatch, listOf(batch), SNATCH_TEST_MINUTES))
    }

    @Test
    fun `challenges show once under way and swings count over the program's run while it is followed`() {
        val now = 40 * DAY
        val sessions = listOf(
            session("old", set(BuiltInExercise.TwoHandSwing, 500, 5 * DAY)),
            session(
                "new",
                set(BuiltInExercise.TwoHandSwing, 1_200, 30 * DAY),
                set(
                    BuiltInExercise.GetUp,
                    1,
                    30 * DAY + MINUTE,
                    kg = 40.0,
                ),
            ),
        )
        val loose = challenges(sessions, now, following = null, followedSince = null, bodyWeightKg = 80.0)
        assertEquals(Challenge.TenThousandSwings(1_200, ofProgram = false), loose[0])
        assertEquals(Challenge.BodyweightGetUp(40.0, 0.5), loose.last())

        val program = ProgramKey.BuiltIn(ProgramId.TenThousandSwings)
        val following = challenges(sessions, now, program, followedSince = 0, bodyWeightKg = null)
        assertEquals(Challenge.TenThousandSwings(1_700, ofProgram = true), following.single())
        // A few swings off the program are no challenge yet.
        val few = listOf(session("few", set(BuiltInExercise.TwoHandSwing, 300, 30 * DAY)))
        assertTrue(challenges(few, now, null, null, null).isEmpty())
        assertTrue(challenges(emptyList(), now, null, null, null).isEmpty())
    }
}
