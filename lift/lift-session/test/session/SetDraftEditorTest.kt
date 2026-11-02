package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.BAR_KG_STEP
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Focus
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.PlannedSet
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.bands
import dev.justinsmith.lift.domain.offeredBands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The Log sheet's draft on its own: what a lift picked starts from, how its load steps, and a set changed. */
class SetDraftEditorTest {
    private var context = DraftContext(
        bells = listOf(12.0, 16.0, 24.0),
        bar = null,
        bands = offeredBands(bands(emptyList())),
        history = emptyList(),
        logged = emptyList(),
        made = emptyList(),
    )
    private val editor = SetDraftEditor { context }
    private val draft get() = editor.draft.value

    private fun set(id: Long, kg: Double, reps: Int = 5, side: Side? = Side.Left) =
        SetLog(id, BuiltInExercise.Press, kg, reps, side, loggedAtEpochMillis = id)

    @Test
    fun `a lift picked starts from the load it was last done with`() {
        context = context.copy(history = listOf(set(1, 12.0), set(2, 24.0)))
        editor.update { it.copy(exercise = BuiltInExercise.Press) }
        assertEquals(24.0, draft.bellKg)

        // The same lift kept: the load in hand stays.
        editor.update { it.copy(bellKg = 16.0) }
        assertEquals(16.0, draft.bellKg)
    }

    @Test
    fun `counts stay in range and the bar is never loaded below what it weighs`() {
        context = context.copy(bar = 20.0)
        editor.update { it.copy(exercise = BuiltInExercise.BarbellDeadlift) }
        editor.update { it.copy(reps = 0, barKg = 10.0, seconds = 0, minutes = 0) }
        assertEquals(1, draft.reps)
        assertEquals(20.0, draft.barKg)
        assertEquals(SetDraft.SECONDS_STEP, draft.seconds)
        assertEquals(1, draft.minutes)

        editor.stepLoad(1)
        assertEquals(20.0 + BAR_KG_STEP, draft.barKg)
    }

    @Test
    fun `the load steps through the bells offered and stops at either end`() {
        editor.start(owned = emptyList(), restored = null, last = null)
        editor.stepLoad(1)
        assertEquals(24.0, draft.bellKg)
        editor.stepLoad(1)
        assertEquals(24.0, draft.bellKg)
        editor.stepLoad(-1)
        editor.stepLoad(-1)
        assertEquals(12.0, draft.bellKg)
    }

    @Test
    fun `a session opens on the sheet a killed process left or else the other side of its last set`() {
        val restored = SetDraft(exercise = BuiltInExercise.Snatch, bellKg = 20.0)
        editor.start(owned = listOf(12.0, 20.0, 28.0), restored = restored, last = set(1, 12.0))
        assertEquals(restored, draft)

        editor.start(owned = listOf(12.0, 20.0, 28.0), restored = null, last = set(1, 12.0, side = Side.Left))
        assertEquals(BuiltInExercise.Press, draft.exercise)
        assertEquals(12.0, draft.bellKg)
        assertEquals(Side.Right, draft.side)

        // With nothing to go on, the lightest of the user's bells at or above the default.
        editor.start(owned = listOf(12.0, 20.0, 28.0), restored = null, last = null)
        assertEquals(20.0, draft.bellKg)
    }

    @Test
    fun `a set changed puts the new set's draft aside until it is saved or left`() {
        context = context.copy(logged = listOf(set(7, 16.0, reps = 5)))
        editor.update { it.copy(exercise = BuiltInExercise.Snatch, reps = 12) }
        val aside = draft

        editor.edit(7)
        assertEquals(BuiltInExercise.Press, draft.exercise)
        editor.update { it.copy(reps = 8) }
        val saved = editor.takeEdit()

        assertEquals(8, saved?.reps)
        assertEquals(7L, saved?.id)
        assertNull(editor.editing.value)
        assertEquals(aside, draft)

        editor.edit(7)
        editor.endEdit()
        assertEquals(aside, draft)
        assertNull(editor.takeEdit())
    }

    @Test
    fun `the sheet opens on the set up next once and again only when asked`() {
        val next = UpNext(PlannedSet(BuiltInExercise.Press, 6, Side.Left), 20.0, 1, 10, emomMinutes = null)
        editor.openOn(next)
        assertEquals(BuiltInExercise.Press to 20.0, draft.exercise to draft.bellKg)

        editor.update { it.copy(bellKg = 16.0) }
        editor.openOn(next)
        assertEquals(16.0, draft.bellKg)

        editor.openOn(next, again = true)
        assertEquals(20.0, draft.bellKg)
    }

    @Test
    fun `an exercise added from the sheet is the one it goes on with`() {
        val old = CustomExercise("old", "Old", Equipment.Kettlebell, Measure.Reps, perSide = false, Focus.FullBody)
        val added = old.copy(uid = "new", name = "New")
        context = context.copy(made = listOf(old))

        editor.exercisesChanged(listOf(old, added))
        assertEquals(BuiltInExercise.TwoHandSwing, draft.exercise)

        editor.expectNewExercise()
        editor.exercisesChanged(listOf(old, added))
        assertEquals(added, draft.exercise)
    }
}
