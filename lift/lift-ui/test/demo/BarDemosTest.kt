package dev.justinsmith.lift.ui.demo

import dev.justinsmith.interval.ui.ExerciseRep
import dev.justinsmith.interval.ui.RepBench
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** What each barbell lift's rep must show, beyond what every rep keeps to. */
class BarDemosTest {
    private val fromTheFloor = listOf(
        BarbellDeadliftDemo,
        BarbellCleanPullDemo,
        BarbellSnatchPullDemo,
        BarbellPowerCleanDemo,
        BarbellCleanDemo,
        BarbellPowerSnatchDemo,
        BarbellSnatchDemo,
        BarbellCleanAndJerkDemo,
    )

    @Test
    fun `a lift from the floor starts with the plate near the floor over the foot`() {
        fromTheFloor.forEach { rep ->
            val start = rep.jointsAt(0f)
            assertTrue(start.kettlebellAnchor.y in 0.08f..0.24f, "The plate starts at ${start.kettlebellAnchor}")
            assertTrue(abs(start.kettlebellAnchor.x - start.ankle.x) < 0.14f, "The bar isn't over the foot")
        }
    }

    @Test
    fun `the snatches and jerks lock the bar out overhead and the cleans rack it`() {
        val overhead = listOf(
            BarbellSnatchDemo,
            BarbellPowerSnatchDemo,
            BarbellHangSnatchDemo,
            BarbellOverheadSquatDemo,
            BarbellSnatchBalanceDemo,
            BarbellPushPressDemo,
            BarbellOverheadPressDemo,
            BarbellJerkDemo,
            BarbellCleanAndJerkDemo,
        )
        overhead.forEach { rep ->
            assertTrue(frames(rep).any { it.kettlebellAnchor.y > it.headCenter.y + 0.06f }, "Bar never goes overhead")
        }
        listOf(BarbellCleanDemo, BarbellPowerCleanDemo, BarbellHangCleanDemo, BarbellFrontSquatDemo).forEach { rep ->
            assertTrue(
                frames(rep).any {
                    abs(it.kettlebellAnchor.y - it.shoulder.y) < 0.08f &&
                        it.kettlebellAnchor.x > it.shoulder.x
                },
                "The bar never sits on the front of the shoulders",
            )
        }
    }

    @Test
    fun `the full catches and squats sit the hips below the knees`() {
        listOf(
            BarbellCleanDemo,
            BarbellSnatchDemo,
            BarbellHangSnatchDemo,
            BarbellOverheadSquatDemo,
            BarbellSnatchBalanceDemo,
            BarbellBackSquatDemo,
            BarbellFrontSquatDemo,
        ).forEach { rep -> assertTrue(frames(rep).any { it.hip.y < it.knee.y }, "The squat stops high") }
        listOf(BarbellPowerCleanDemo, BarbellPowerSnatchDemo).forEach { rep ->
            assertTrue(frames(rep).none { it.hip.y < it.knee.y }, "A power catch goes below parallel")
        }
    }

    @Test
    fun `the back squat keeps the bar on the upper back`() {
        frames(BarbellBackSquatDemo).forEach { j ->
            assertTrue(
                (j.kettlebellAnchor - j.shoulder).getDistance() < 0.12f,
                "The bar slips to ${j.kettlebellAnchor}",
            )
        }
    }

    @Test
    fun `the split jerk splits the feet and brings them back together`() {
        val split = frames(BarbellJerkDemo).maxOf { it.ankle.x - it.farAnkle.x }
        assertTrue(split > 0.25f, "The feet only split $split")
        val tall = BarbellJerkDemo.jointsAt(0.7f)
        assertTrue(abs(tall.ankle.x - tall.farAnkle.x) < 0.02f, "The feet stay split")
    }

    @Test
    fun `the bench press lowers the bar to the chest and presses it over the shoulders`() {
        val rep = BarbellBenchPressDemo
        val top = rep.jointsAt(0f)
        assertTrue(top.kettlebellAnchor.y > top.shoulder.y + 0.28f, "The bar starts at ${top.kettlebellAnchor}")
        val chest = rep.jointsAt(0.48f)
        assertTrue(
            chest.kettlebellAnchor.y > chest.shoulder.y && chest.kettlebellAnchor.y < chest.shoulder.y + 0.16f,
            "The bar stops at ${chest.kettlebellAnchor}",
        )
        val benchHeight = (rep.props.single() as RepBench).height
        frames(rep).forEach {
            assertTrue(it.hip.y >= benchHeight - 0.04f, "The hips sink into the bench at ${it.hip.y}")
        }
    }

    @Test
    fun `the row and the Romanian deadlift stay hinged`() {
        frames(BarbellRowDemo).forEach { assertTrue(it.shoulder.y < it.hip.y + 0.18f, "The row stands up") }
        val bottom = BarbellRomanianDeadliftDemo.jointsAt(BarbellRomanianDeadliftDemo.still)
        assertTrue(bottom.kettlebellAnchor.y < bottom.knee.y + 0.04f, "The bar stops above the knees")
        assertTrue(bottom.hip.x < bottom.ankle.x - 0.08f, "The hips never go back")
    }

    private fun frames(rep: ExerciseRep) = (0..200).map { rep.jointsAt(it / 200f) }
}
