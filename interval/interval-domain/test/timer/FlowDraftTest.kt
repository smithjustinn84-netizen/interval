package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowDraftTest {
    private val plank = Move("Plank", seconds = 30, cue = "Long line.")
    private val bridge = Move("Shoulder bridge", seconds = 20, cue = "Hips up.", sided = true, held = false)
    private val hips = builtInFlows.first { it.key == "hips-shoulders" }

    @Test
    fun `a draft saves as a flow once it has a name and a move`() {
        assertNull(FlowDraft().toFlow())
        assertNull(FlowDraft(name = "Core").toFlow())
        assertNull(FlowDraft(name = "  ", moves = listOf(plank)).toFlow())

        val flow = assertNotNull(FlowDraft("  Core ", FlowUse.Before, "on the floor", listOf(plank, bridge)).toFlow())
        assertEquals("Core", flow.name)
        assertEquals(FlowUse.Before, flow.use)
        assertEquals("on the floor", flow.note)
        assertEquals(listOf(plank, bridge), flow.moves)
        assertNull(FlowDraft("Core", note = " ", moves = listOf(plank)).toFlow()?.note)
    }

    @Test
    fun `one it edits keeps its id and key`() {
        val flow = assertNotNull(FlowDraft("Core", moves = listOf(plank), id = 4).toFlow())
        assertEquals(4L, flow.id)
        assertEquals(ownFlowKey(4), flow.key)
        assertEquals(FlowDraft("Core", FlowUse.After, "", listOf(plank), 4), FlowDraft.of(flow))
    }

    @Test
    fun `moves are added up to ten moved and taken out`() {
        var draft = FlowDraft(name = "Core")
        repeat(Flow.MAX_MOVES + 2) { draft = draft.adding(plank.copy(seconds = 10 + it)) }
        assertEquals(Flow.MAX_MOVES, draft.moves.size)
        assertFalse(draft.roomForMore)

        draft = FlowDraft(moves = listOf(plank, bridge))
        assertEquals(listOf(bridge, plank), draft.moving(1, -1).moves)
        assertEquals(draft, draft.moving(0, -1))
        assertEquals(listOf(bridge), draft.removing(0).moves)
        assertEquals(listOf(plank, plank), draft.replacing(1, plank).moves)
        assertEquals(draft, draft.removing(5))
    }

    @Test
    fun `a move's time steps by five seconds within its range`() {
        assertEquals(35, plank.steppedSeconds(1).seconds)
        assertEquals(Move.SECONDS_RANGE.first, plank.copy(seconds = 10).steppedSeconds(-1).seconds)
        assertEquals(Move.SECONDS_RANGE.last, plank.copy(seconds = 300).steppedSeconds(1).seconds)
    }

    @Test
    fun `a copy of a shipped flow has its moves under the name given`() {
        val copy = FlowDraft.copyOf(hips, "Hips and shoulders (yours)")
        assertEquals("Hips and shoulders (yours)".take(Flow.NAME_MAX_LENGTH), copy.name)
        assertEquals(hips.moves, copy.moves)
        assertEquals(hips.use, copy.use)
        assertNull(copy.id)
    }

    @Test
    fun `a draft with the shape of another flow or a preset clashes with it`() {
        val copy = FlowDraft.copyOf(hips, "Mine")
        assertEquals(Clash("Hips and shoulders"), copy.clash(builtInFlows, builtInPresets))

        val mine = Flow(ownFlowKey(3), "Core", FlowUse.After, listOf(plank), id = 3)
        val flows = builtInFlows + mine
        // Its own shape is no clash for the flow being edited.
        assertNull(FlowDraft.of(mine).clash(flows, emptyList()))
        assertEquals(Clash("Core"), FlowDraft("Other", moves = listOf(plank)).clash(flows, emptyList()))
        assertNull(FlowDraft("Other", moves = listOf(plank.copy(seconds = 35))).clash(flows, emptyList()))

        val preset = Preset("Plank 30", mine.config, id = 1)
        assertEquals(Clash("Plank 30"), FlowDraft("Other", moves = listOf(plank)).clash(builtInFlows, listOf(preset)))
        assertNull(FlowDraft().clash(flows, listOf(preset)))
    }

    @Test
    fun `a workout laid out as a flow is told from one that isn't`() {
        builtInFlows.forEach { assertTrue(it.config.isFlowShaped, it.name) }
        assertTrue(Flow("x", "One", FlowUse.After, listOf(plank)).config.isFlowShaped)
        assertTrue(Flow("x", "Sides", FlowUse.After, listOf(bridge)).config(prepSeconds = 5).isFlowShaped)
        assertFalse(IntervalConfig().isFlowShaped)
        assertFalse(hips.config.copy(activity = Activity.General).isFlowShaped)
        assertFalse(IntervalConfig(activity = Activity.Mobility).isFlowShaped)
    }
}
