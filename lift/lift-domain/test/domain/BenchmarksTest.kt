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

    @Test
    fun `a density block's rounds count toward the ABC challenge`() {
        // Fourteen rounds in twenty minutes, one tapped every eighty-five seconds.
        val rounds = (0 until 14).map {
            SetLog.of(0, BuiltInExercise.ArmorBuildingComplex, 24.0, 1, null, 30_000L + it * 85_000L, double = true)
        }
        val abc = challenges(listOf(session("abc", *rounds.toTypedArray())), 40 * DAY, null, null, null)
        assertEquals(Challenge.ArmorBuilding(14), abc.single())
    }

    @Test
    fun `the ABC's best says the protocol of the session it came from`() {
        fun rounds(n: Int, gap: Long) = (0 until n).map {
            SetLog.of(0, BuiltInExercise.ArmorBuildingComplex, 24.0, 1, null, 30_000L + it * gap, double = true)
        }
        val abf = ProgramKey.BuiltIn(ProgramId.ArmorBuildingFormula)
        val onTheMinute = session("emom", *rounds(12, 60_000L).toTypedArray()).copy(program = abf, day = 1)
        val day = program(ProgramId.ArmorBuildingFormula).day(1)
        val capped = SessionPlan.of(day.blocks).change(0) { it.otherAbcProtocol() ?: it }
        val asMany = session("cap", *rounds(14, 85_000L).toTypedArray()).copy(program = abf, day = 1, plan = capped)
        assertEquals(
            Challenge.ArmorBuilding(14, AbcProtocol.AsManyAsYouCan),
            challenges(listOf(onTheMinute, asMany), 40 * DAY, null, null, null).single(),
        )
        assertEquals(
            Challenge.ArmorBuilding(12, AbcProtocol.OnTheMinute),
            challenges(listOf(onTheMinute), 40 * DAY, null, null, null).single(),
        )
    }

    /** 15:15 MVO2 run out from the epoch: forty work intervals, the first ending 25 s in, thirty seconds apart. */
    private val fifteenFifteen = List(40) { 25_000L + it * 30_000L }

    @Test
    fun `15-15 counted at 8 a set is forty sets of 8 a hand at a time`() {
        val group = intervalSets(listOf(IntervalSnatches(8, 16.0, fifteenFifteen, "15:15 MVO2"))).single()

        assertEquals("15:15 MVO2", group.source)
        assertEquals(40, group.sets.size)
        assertTrue(group.sets.all { it.exercise == BuiltInExercise.Snatch && it.reps == 8 && it.load.kg == 16.0 })
        assertEquals(listOf(Side.Left, Side.Right, Side.Left), group.sets.take(3).map { it.side })
        assertEquals(40, group.sets.map { it.id }.distinct().size)
        assertTrue(group.sets.all { it.id < 0 })
        assertTrue(loggedAsDone(group.sets))
        assertEquals(320, lifetimeReps(BuiltInExercise.Snatch, group.sets))
    }

    @Test
    fun `the snatch test takes an interval workout's snatches and names where its best came from`() {
        val intervals = intervalSets(listOf(IntervalSnatches(8, 16.0, fifteenFifteen, "15:15 MVO2")))
        // Ten sets of 10 a minute apart in a session: 50 in five minutes, under 15:15's 80.
        val lifted = session("a", *Array(10) { set(BuiltInExercise.Snatch, 10, (it + 1) * MINUTE) })

        assertEquals(
            Challenge.SnatchTest(80, fromIntervals = true, preset = "15:15 MVO2"),
            snatchTest(listOf(lifted), intervals),
        )
        assertEquals(Challenge.SnatchTest(50), snatchTest(listOf(lifted), emptyList()))
        val unnamed = intervalSets(listOf(IntervalSnatches(8, 16.0, fifteenFifteen, source = null)))
        assertEquals(Challenge.SnatchTest(80, fromIntervals = true), snatchTest(emptyList(), unnamed))
        val challenges = challenges(emptyList(), 40 * DAY, null, null, null, intervals = intervals)
        assertEquals(listOf(Challenge.SnatchTest(80, fromIntervals = true, preset = "15:15 MVO2")), challenges)
    }

    @Test
    fun `a session's best stands when an interval workout only ties it`() {
        val intervals = intervalSets(listOf(IntervalSnatches(5, 16.0, fifteenFifteen, "15:15 MVO2")))
        val lifted = session("a", *Array(10) { set(BuiltInExercise.Snatch, 10, (it + 1) * MINUTE) })

        assertEquals(Challenge.SnatchTest(50), snatchTest(listOf(lifted), intervals))
    }
}
