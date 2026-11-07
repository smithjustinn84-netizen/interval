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
    ) = readiness(sessions, grappling, grip, now, dayOf)

    @Test
    fun `a very hard session within a day and a half taxes today`() {
        assertEquals(
            ReadinessCheck(Readiness.Taxed, ReadinessReason.HardSession),
            check(listOf(session(now - 30 * HOUR_MILLIS, PerceivedEffort.VeryHard))),
        )
        assertEquals(Readiness.Normal, check(listOf(session(now - 40 * HOUR_MILLIS, PerceivedEffort.AllOut))).readiness)
        assertEquals(Readiness.Normal, check(listOf(session(now - 10 * HOUR_MILLIS, PerceivedEffort.Hard))).readiness)
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
}
