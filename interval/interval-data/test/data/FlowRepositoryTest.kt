package dev.justinsmith.interval.data

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.FlowUse
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.timer.builtInFlows
import dev.justinsmith.interval.timer.ownFlowKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The flows through [FlowRepository]: the shipped ones first, the user's after, saved, renamed, deleted and back. */
class FlowRepositoryTest {
    private val storage = FakeStorage()
    private fun repository() = storage.repository()

    private val plank = Move("Plank", seconds = 30, cue = "Long line from head to heel.")
    private val sidePlank = Move("Side plank", seconds = 20, cue = "Hips high.", sided = true)

    private fun flow(name: String, vararg moves: Move) = Flow("new", name, FlowUse.After, moves.toList())

    @Test
    fun `the shipped flows are there before any is saved`() {
        val flows = repository().flows
        assertEquals(builtInFlows, flows.flows.value)
        assertTrue(flows.customFlows.value.isEmpty())
    }

    @Test
    fun `a saved flow follows the shipped ones under a key of its own and outlives a restart`() {
        repository().flows.saveFlow(flow("Core", plank, sidePlank).copy(note = "on the floor"))

        val flows = repository().flows
        val saved = flows.customFlows.value.single()
        assertEquals(builtInFlows + saved, flows.flows.value)
        assertEquals("Core", saved.name)
        assertEquals(listOf(plank, sidePlank), saved.moves)
        assertEquals("on the floor", saved.note)
        assertEquals(ownFlowKey(checkNotNull(saved.id)), saved.key)
        assertTrue(saved.custom)
    }

    @Test
    fun `a name another flow has is numbered`() {
        val flows = repository().flows
        flows.saveFlow(flow("Hips and shoulders", plank))
        flows.saveFlow(flow("Mine", sidePlank))
        flows.saveFlow(flow(" Mine ", plank.copy(seconds = 40)))
        assertEquals(
            listOf("Hips and shoulders 2", "Mine", "Mine 2"),
            flows.customFlows.value.map { it.name },
        )
    }

    @Test
    fun `an edited flow keeps its id and a deleted one comes back with undo`() {
        val flows = repository().flows
        flows.saveFlow(flow("Core", plank))
        val saved = flows.customFlows.value.single()

        flows.updateFlow(saved.copy(name = "Core 2", moves = listOf(plank, sidePlank)))
        val edited = flows.customFlows.value.single()
        assertEquals(saved.id, edited.id)
        assertEquals(2, edited.moves.size)

        flows.deleteFlow(edited)
        assertTrue(flows.customFlows.value.isEmpty())
        flows.restoreFlow(edited)
        assertEquals(listOf(edited), flows.customFlows.value)
    }

    @Test
    fun `a shipped flow is never written`() {
        val flows = repository().flows
        flows.deleteFlow(builtInFlows.first())
        flows.updateFlow(builtInFlows.first())
        assertEquals(builtInFlows, flows.flows.value)
        assertTrue(storage.flows.rows.value.isEmpty())
    }

    @Test
    fun `a row whose moves can't be read is left out and the rest read`() {
        repository().flows.saveFlow(flow("Core", plank))
        storage.flows.rows.value += FlowEntity(id = 9, name = "Broken", use = "Before", moves = "not json")

        assertEquals(listOf("Core"), repository().flows.customFlows.value.map { it.name })
    }

    @Test
    fun `a move read back is kept within its ranges`() {
        storage.flows.rows.value = listOf(
            FlowEntity(
                id = 1,
                name = "Odd",
                use = "Sideways",
                moves = """[{"name":"Plank","seconds":5000,"cue":"x"}]""",
            ),
        )
        val read = repository().flows.customFlows.value.single()
        assertEquals(Move.SECONDS_RANGE.last, read.moves.single().seconds)
        assertEquals(FlowUse.After, read.use)
    }
}
