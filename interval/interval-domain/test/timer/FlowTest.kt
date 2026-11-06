package dev.justinsmith.interval.timer

import dev.justinsmith.interval.model.Sport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowTest {
    private val hips = builtInFlows.first { it.key == "hips-shoulders" }

    private val short = Flow(
        key = "short",
        name = "Short",
        use = FlowUse.Before,
        moves = listOf(
            Move("Squat", seconds = 30, cue = "Sit low."),
            Move("Lunge", seconds = 20, cue = "Knee down.", sided = true),
        ),
    )

    @Test
    fun `a move is a block and each side a round of it`() {
        val config = short.config
        assertEquals(Activity.Mobility, config.activity)
        assertEquals(listOf(Block(30, 0, 1), Block(20, Flow.SIDE_CHANGE_SECONDS, 2)), config.blocks)
        assertEquals(Flow.MOVE_CHANGE_SECONDS, config.blockRestSeconds)
        // Get ready 10, the squat 30, the change 10, the lunge 20 a side with 5 between.
        assertEquals(10 + 30 + 10 + 20 + 5 + 20, config.totalSeconds)
        assertEquals(5 + 85, short.config(prepSeconds = 5).totalSeconds)
    }

    @Test
    fun `the course names the move worked and the one coming`() {
        val steps = Course.of(short.config).map { short.stepFor(it.phase, it.block, it.round) }
        val squat = FlowStep(short.moves[0], 1, 2, side = null)
        val left = FlowStep(short.moves[1], 2, 2, FlowSide.Left)
        val right = left.copy(side = FlowSide.Right)
        // Get ready, the squat, the change to the lunge, its left, the change of sides, its right.
        assertEquals(listOf(squat, squat, left, left, right, right), steps)
        assertEquals(left, short.stepAfter(squat))
        assertEquals(right, short.stepAfter(left))
        assertNull(short.stepAfter(right))
    }

    @Test
    fun `a state in the flow knows its step and a finished one has none`() {
        val start = WorkoutEngine.start(short.config).state
        assertEquals(1, short.stepAt(start)?.number)
        var state = start
        repeat(3) { state = WorkoutEngine.advance(state).state }
        assertEquals(Phase.Work, state.phase)
        assertEquals(FlowStep(short.moves[1], 2, 2, FlowSide.Left), short.stepAt(state))
        repeat(3) { state = WorkoutEngine.advance(state).state }
        assertEquals(Phase.Done, state.phase)
        assertNull(short.stepAt(state))
    }

    @Test
    fun `a flow is known again by its shape whatever the get-ready`() {
        assertEquals(hips, builtInFlows.matching(hips.config(prepSeconds = 3)))
        assertNull(builtInFlows.matching(hips.config.copy(activity = Activity.General)))
        assertNull(builtInFlows.matching(IntervalConfig()))
        assertEquals("Hips and shoulders", emptyList<Preset>().nameOf(hips.config, builtInFlows))
        // A preset of the user's with the same shape names it first, as it names any workout.
        assertEquals("Mine", listOf(Preset("Mine", hips.config, id = 1)).nameOf(hips.config, builtInFlows))
        assertEquals("Tabata", builtInPresets.nameOf(builtInPresets.first().config, builtInFlows))
    }

    @Test
    fun `no two flows share a shape or a key and none is a built-in preset`() {
        builtInFlows.forEach { flow ->
            assertEquals(listOf(flow), builtInFlows.filter { it.config.sameShape(flow.config) }, flow.name)
            assertEquals(1, builtInFlows.count { it.key == flow.key }, flow.key)
            assertNull(builtInPresets.matching(flow.config), flow.name)
            assertEquals(flow, builtInFlows.keyed(flow.key))
        }
        assertNull(builtInFlows.keyed("gone"))
        assertNull(builtInFlows.keyed(null))
    }

    @Test
    fun `each flow takes a round number of minutes and every move says how`() {
        val minutes = builtInFlows.associate { it.name to it.config.totalSeconds }
        assertEquals(
            mapOf(
                "Hips and shoulders" to 8 * 60,
                "Wrists and fingers" to 5 * 60,
                "Spine and hamstrings" to 10 * 60,
                "Core on the mat" to 18 * 60,
                "Full reset" to 25 * 60,
            ),
            minutes,
        )
        builtInFlows.flatMap { it.moves }.forEach {
            assertTrue(it.name.isNotBlank() && it.cue.isNotBlank(), it.name)
        }
    }

    @Test
    fun `the wrists come before the wall and the hips before the rest`() {
        assertEquals("wrists-fingers", flowBefore(Sport.Climbing).key)
        assertEquals("hips-shoulders", flowBefore(Sport.Bjj).key)
        assertEquals("hips-shoulders", flowBefore(Sport.Running).key)
        assertNotNull(flowBefore(Sport.Custom("Judo")))
    }
}
