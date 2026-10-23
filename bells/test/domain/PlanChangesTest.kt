package dev.justinsmith.bells.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanChangesTest {
    private val swings = Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10)
    private val squats = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 5)
    private val swingEmom = Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = 10, kind = BlockKind.Emom)
    private val program = Program(
        ProgramKey.Custom("p"),
        days = listOf(listOf(swings, squats), listOf(swingEmom, swings.copy(reps = 15))),
        daysPerWeek = 2,
        weeks = 2,
        name = "Mine",
    )

    private fun changed(dayIndex: Int, transform: (SessionPlan) -> SessionPlan): PlanChanges? {
        val day = program.dayAfter(dayIndex)!!
        return planChanges(day.copy(plan = transform(day.today)))
    }

    @Test
    fun `a day as programmed or only skipped has no changes`() {
        assertNull(planChanges(program.dayAfter(0)!!))
        assertNull(changed(0) { it.skip(0, done = 2) })
    }

    @Test
    fun `this day keeps a change on the day done only`() {
        val changes = changed(0) { it.change(0) { b -> b.copy(kg = 24.0, reps = 12) } }!!
        assertEquals(listOf(BlockChange(0, swings, swings.copy(kg = 24.0, reps = 12))), changes.changes)
        val days = changes.applied(SaveScope.ThisDay)
        assertEquals(listOf(swings.copy(kg = 24.0, reps = 12), squats), days[0])
        assertEquals(program.days[1], days[1])
    }

    @Test
    fun `every day carries a load everywhere and a count only to blocks that had it`() {
        val days = changed(0) { it.change(0) { b -> b.copy(kg = 24.0, reps = 12) } }!!.applied(SaveScope.EveryDay)
        // The EMOM takes the bell but keeps its reps: it is another kind. Sets of 15 keep theirs.
        assertEquals(swingEmom.copy(kg = 24.0), days[1][0])
        assertEquals(swings.copy(reps = 15, kg = 24.0), days[1][1])
    }

    @Test
    fun `a swap stays on this day whatever the scope`() {
        val changes = changed(0) { it.swap(0, BuiltInExercise.Deadlift, done = 0) }!!
        assertTrue(changes.changes.single().swapped)
        val days = changes.applied(SaveScope.EveryDay)
        assertEquals(BuiltInExercise.Deadlift, days[0][0].exercise)
        assertEquals(program.days[1], days[1])
    }

    @Test
    fun `the second day is the one changed when it is done`() {
        val changes = changed(1) { it.change(1) { b -> b.copy(rounds = 4) } }!!
        assertEquals(1, changes.dayIndex)
        assertEquals(4, changes.applied(SaveScope.ThisDay)[1][1].rounds)
        assertEquals(program.days[0], changes.applied(SaveScope.ThisDay)[0])
    }

    @Test
    fun `a copy is named within the limit and apart from programs already made`() {
        assertEquals("Easy Strength (yours)", copyName("Easy Strength (yours)", setOf("Mine")))
        assertEquals("Easy Strength (yours) 2", copyName("Easy Strength (yours)", setOf("easy strength (yours)")))
        val long = "A".repeat(ProgramDraft.MAX_NAME_LENGTH + 5)
        assertEquals(ProgramDraft.MAX_NAME_LENGTH, copyName(long, emptySet()).length)
        assertTrue(copyName(long, setOf("A".repeat(ProgramDraft.MAX_NAME_LENGTH))).endsWith(" 2"))
    }
}
