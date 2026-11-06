package dev.justinsmith.interval.mobility

import androidx.lifecycle.SavedStateHandle
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.timer.Clash
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.FlowUse
import dev.justinsmith.interval.timer.Move
import dev.justinsmith.interval.timer.builtInFlows
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowEditorViewModelTest : MainDispatcherTest() {
    private val repository = FakeStorage().repository()
    private val savedState = SavedStateHandle()
    private val plank = Move("Plank", 30, "Long line.")
    private val bug = Move("Dead bug", 40, "Low back down.")

    private fun editor(id: Long? = null, copy: FlowCopy? = null, saved: SavedStateHandle = savedState) =
        FlowEditorViewModel(repository.flows, repository.presets, repository.settings, id, copy, saved)

    @Test
    fun `a new flow is saved once it has a name and a move`() {
        val editor = editor()
        assertFalse(editor.state.value.canSave)
        assertFalse(editor.save())

        editor.change { it.copy(name = "Floor core").adding(plank).adding(bug) }
        assertTrue(editor.state.value.canSave)
        // Ten seconds to get ready, the plank, ten to change, the dead bug.
        assertEquals("1:30", editor.state.value.total)
        assertTrue(editor.save())

        val saved = repository.flows.customFlows.value.single()
        assertEquals("Floor core" to listOf(plank, bug), saved.name to saved.moves)
        assertEquals(FlowUse.After, saved.use)
    }

    @Test
    fun `a move left unnamed keeps it from saving`() {
        val editor = editor()
        editor.change { it.copy(name = "Floor core").adding(Move("", 30, "")) }
        assertFalse(editor.state.value.canSave)
        editor.change { it.replacing(0, plank) }
        assertTrue(editor.state.value.canSave)
    }

    @Test
    fun `a copy of a shipped flow clashes with it until a time changes`() {
        val hips = builtInFlows.first { it.key == "hips-shoulders" }
        val editor = editor(copy = FlowCopy(hips.key, "Hips and shoulders (yours)"))

        assertEquals("Hips and shoulders (yours)", editor.state.value.draft.name)
        assertEquals(Clash("Hips and shoulders"), editor.state.value.clash)
        assertFalse(editor.save())

        editor.change { it.replacing(0, it.moves[0].copy(seconds = it.moves[0].seconds + 5)) }
        assertNull(editor.state.value.clash)
        assertTrue(editor.save())
        assertEquals(listOf("Hips and shoulders (yours)"), repository.flows.customFlows.value.map { it.name })
    }

    @Test
    fun `a flow of the user's is edited in place and deleted with undo`() {
        repository.flows.saveFlow(Flow("new", "Floor core", FlowUse.Before, listOf(plank)))
        val id = repository.flows.customFlows.value.single().id
        val editor = editor(id = id)
        assertTrue(editor.state.value.edits)
        assertEquals(listOf(plank), editor.state.value.draft.moves)
        // Its own shape is no clash with itself.
        assertNull(editor.state.value.clash)

        editor.change { it.adding(bug) }
        assertTrue(editor.save())
        val edited = repository.flows.customFlows.value.single()
        assertEquals(id to 2, edited.id to edited.moves.size)

        val deleted = editor.delete()
        assertTrue(repository.flows.customFlows.value.isEmpty())
        editor.restore(checkNotNull(deleted))
        assertEquals(listOf(edited), repository.flows.customFlows.value)
    }

    @Test
    fun `the draft outlives the view model`() {
        editor().change { it.copy(name = "Floor core", note = "on the mat", use = FlowUse.Before).adding(plank) }

        val back = editor().state.value.draft
        assertEquals("Floor core", back.name)
        assertEquals("on the mat", back.note)
        assertEquals(FlowUse.Before, back.use)
        assertEquals(listOf(plank), back.moves)
    }
}
