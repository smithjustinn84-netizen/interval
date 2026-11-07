package dev.justinsmith.lift.ui.demo

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/** What each barbell lift's figure must show, beyond what every demo keeps to. */
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
    fun `a lift from the floor starts with the plate on it over the middle of the foot`() {
        fromTheFloor.forEach { demo ->
            val start = demo.figureAt(0f)
            assertTrue(abs(start.hand.y + Rig.PLATE - Rig.FLOOR) < 4f, "The plate starts at ${start.hand}")
            assertTrue(abs(start.hand.x - (start.ankle.x + Rig.FOOT / 2)) < 15f, "The bar isn't over the foot")
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
        overhead.forEach { demo ->
            assertTrue(frames(demo).any { it.hand.y < it.head.y - Rig.HEAD }, "The bar never goes overhead")
        }
        listOf(BarbellCleanDemo, BarbellPowerCleanDemo, BarbellHangCleanDemo, BarbellFrontSquatDemo).forEach { demo ->
            assertTrue(
                frames(demo).any { abs(it.hand.y - it.shoulder.y) < 20f && it.hand.x > it.shoulder.x },
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
        ).forEach { demo -> assertTrue(frames(demo).any { it.hip.y > it.knee.y }, "The squat stops high") }
        listOf(BarbellPowerCleanDemo, BarbellPowerSnatchDemo).forEach { demo ->
            assertTrue(frames(demo).none { it.hip.y > it.knee.y }, "A power catch goes below parallel")
        }
    }

    @Test
    fun `the back squat keeps the bar on the upper back`() {
        frames(BarbellBackSquatDemo).forEach { figure ->
            assertTrue((figure.hand - figure.shoulder).getDistance() < 25f, "The bar slips to ${figure.hand}")
        }
    }

    @Test
    fun `the split jerk splits the feet and brings them back together`() {
        val split = frames(BarbellJerkDemo).maxOf { it.ankle.x - it.farAnkle.x }
        assertTrue(split > 80f, "The feet only split $split")
        val tall = BarbellJerkDemo.figureAt(0.7f)
        assertTrue(abs(tall.ankle.x - tall.farAnkle.x) < 2f, "The feet stay split")
    }

    @Test
    fun `the bench press lowers the bar to the chest and presses it over the shoulders`() {
        val demo = BarbellBenchPressDemo
        val top = demo.figureAt(0f)
        assertTrue(top.hand.y < top.shoulder.y - 90f, "The bar starts at ${top.hand}")
        val chest = demo.figureAt(0.48f)
        assertTrue(
            chest.hand.y < chest.shoulder.y && chest.hand.y > chest.shoulder.y - 35f,
            "The bar stops at ${chest.hand}",
        )
        frames(demo).forEach {
            assertTrue(it.hip.y < Rig.FLOOR - (demo.props.single() as Bench).height, "The hips sink into the bench")
        }
    }

    @Test
    fun `the row and the Romanian deadlift stay hinged`() {
        frames(BarbellRowDemo).forEach { assertTrue(it.shoulder.y > it.hip.y - 50f, "The row stands up") }
        val bottom = BarbellRomanianDeadliftDemo.figureAt(BarbellRomanianDeadliftDemo.still)
        assertTrue(bottom.hand.y > bottom.knee.y, "The bar stops above the knees")
        assertTrue(bottom.hip.x < bottom.ankle.x - 30f, "The hips never go back")
    }

    private fun frames(demo: ExerciseDemo) = (0..200).map { demo.figureAt(it / 200f) }
}
