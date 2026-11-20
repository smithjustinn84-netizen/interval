package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals

private const val DAY_MILLIS = 86_400_000L
private const val HOUR_MILLIS = 3_600_000L

class ReadinessTest {
    private val dayOf: (Long) -> Long = { it / DAY_MILLIS }
    private val now = 10 * DAY_MILLIS + 12 * HOUR_MILLIS

    private fun session(finishedAt: Long, effort: PerceivedEffort? = null) =
        LiftSession("s$finishedAt", finishedAt - HOUR_MILLIS, finishedAt, "", emptyList(), effort = effort)

    private fun check(
        sessions: List<LiftSession> = emptyList(),
        grappling: List<Long> = emptyList(),
        grip: GripLoad = GripLoad(),
        conditioning: List<WorkoutEffort> = emptyList(),
    ) = readiness(sessions, grappling, conditioning, grip, now, dayOf)

    private fun workout(hoursAgo: Long, effort: PerceivedEffort) = WorkoutEffort(now - hoursAgo * HOUR_MILLIS, effort)

    @Test
    fun `a very hard session within two days taxes today`() {
        assertEquals(
            ReadinessCheck(Readiness.Taxed, ReadinessReason.HardSession),
            check(listOf(session(now - 30 * HOUR_MILLIS, PerceivedEffort.VeryHard))),
        )
        // The morning after next still has it: recovery is slower than a day and a half.
        assertEquals(
            Readiness.Taxed,
            check(listOf(session(now - 44 * HOUR_MILLIS, PerceivedEffort.VeryHard))).readiness,
        )
        assertEquals(Readiness.Normal, check(listOf(session(now - 50 * HOUR_MILLIS, PerceivedEffort.AllOut))).readiness)
        assertEquals(Readiness.Normal, check(listOf(session(now - 10 * HOUR_MILLIS, PerceivedEffort.Hard))).readiness)
    }

    @Test
    fun `an interval workout felt very hard or all out within two days taxes today`() {
        val hard = ReadinessCheck(Readiness.Taxed, ReadinessReason.HardConditioning)
        assertEquals(hard, check(conditioning = listOf(workout(10, PerceivedEffort.AllOut))))
        assertEquals(hard, check(conditioning = listOf(workout(44, PerceivedEffort.VeryHard))))
        assertEquals(Readiness.Normal, check(conditioning = listOf(workout(50, PerceivedEffort.AllOut))).readiness)
        assertEquals(Readiness.Normal, check(conditioning = listOf(workout(10, PerceivedEffort.Hard))).readiness)
    }

    @Test
    fun `an easy workout after an all-out one still leaves today taxed`() {
        val workouts = listOf(workout(2, PerceivedEffort.Easy), workout(20, PerceivedEffort.AllOut))
        assertEquals(Readiness.Taxed, check(conditioning = workouts).readiness)
    }

    @Test
    fun `a very hard session is the reason before a very hard workout`() {
        assertEquals(
            ReadinessCheck(Readiness.Taxed, ReadinessReason.HardSession),
            check(
                listOf(session(now - 30 * HOUR_MILLIS, PerceivedEffort.VeryHard)),
                conditioning = listOf(workout(10, PerceivedEffort.AllOut)),
            ),
        )
    }

    @Test
    fun `BJJ yesterday taxes today only with the grip above usual`() {
        // Above the weeks before (600 against 500 a week), short of overreached.
        val above = GripLoad(acute = 600.0, chronic = 2_100.0)
        val below = GripLoad(acute = 300.0, chronic = 1_800.0)
        assertEquals(ReadinessCheck(Readiness.Taxed, ReadinessReason.Mat), check(grappling = listOf(9), grip = above))
        assertEquals(Readiness.Normal, check(grappling = listOf(9), grip = below).readiness)
        assertEquals(Readiness.Normal, check(grappling = listOf(7), grip = above).readiness)
    }

    @Test
    fun `an overreached grip taxes today`() {
        val grip = GripLoad(acute = 2_000.0, chronic = 3_500.0)
        assertEquals(ReadinessCheck(Readiness.Taxed, ReadinessReason.Grip), check(grip = grip))
    }

    @Test
    fun `three days off leaves one fresh and a first day is normal`() {
        assertEquals(Readiness.Fresh, check(listOf(session(7 * DAY_MILLIS))).readiness)
        assertEquals(Readiness.Normal, check(listOf(session(8 * DAY_MILLIS))).readiness)
        assertEquals(Readiness.Normal, check().readiness)
    }

    /** A usual training day's heart load on each of four days before the last two, then [recent] on [recentDay]. */
    private fun loads(recent: Int, recentDay: Long = 9, usual: Int = 60) =
        listOf(2L, 4L, 6L, 7L).map { HeartLoad(it, usual) } + HeartLoad(recentDay, recent)

    @Test
    fun `a day the heart worked half again a usual day taxes the next`() {
        assertEquals(
            ReadinessCheck(Readiness.Taxed, ReadinessReason.HeartLoad),
            readiness(emptyList(), emptyList(), emptyList(), GripLoad(), now, dayOf, loads(recent = 100)),
        )
        // Today's own counts as yesterday's does.
        assertEquals(true, heavyHeartDay(loads(recent = 100, recentDay = 10), today = 10))
    }

    @Test
    fun `a usual day on the heart taxes nothing and neither does one two days gone`() {
        assertEquals(false, heavyHeartDay(loads(recent = 70), today = 10))
        assertEquals(false, heavyHeartDay(loads(recent = 200, recentDay = 8), today = 10))
    }

    @Test
    fun `a heavy day is not called before a sensor has read three days to go by`() {
        val few = listOf(HeartLoad(6, 60), HeartLoad(7, 60), HeartLoad(9, 200))

        assertEquals(false, heavyHeartDay(few, today = 10))
    }

    @Test
    fun `a light day is never heavy however little the days before held`() {
        assertEquals(false, heavyHeartDay(loads(recent = 50, usual = 10), today = 10))
    }

    @Test
    fun `a day's loads add up and a very hard session said so comes first`() {
        val twice = loads(recent = 50) + HeartLoad(9, 50)
        assertEquals(true, heavyHeartDay(twice, today = 10))

        val said = listOf(session(now - 30 * HOUR_MILLIS, PerceivedEffort.VeryHard))
        assertEquals(
            ReadinessReason.HardSession,
            readiness(said, emptyList(), emptyList(), GripLoad(), now, dayOf, loads(recent = 100)).reason,
        )
    }
}
