package dev.justinsmith.lift.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SupersetsTest {
    private val push = Block(BuiltInExercise.PushUp, rounds = 3, reps = 5)
    private val pullUp = Block(BuiltInExercise.PullUp, rounds = 3, reps = 5)
    private val squat = Block(BuiltInExercise.GobletSquat, rounds = 2, reps = 10)
    private val swings = Block(BuiltInExercise.TwoHandSwing, rounds = 2, reps = 10)

    private fun Block.paired(rest: SupersetRest = SupersetRest.Shared) = copy(superset = rest)

    private fun logged(exercise: Exercise, times: Int, side: Side? = null) =
        (1..times).map { SetLog.of(it.toLong(), exercise, 24.0, 5, side, it.toLong()) }

    private fun day(vararg blocks: Block, plan: SessionPlan? = null) = ProgramDay(
        Program(ProgramKey.Custom("p"), listOf(blocks.toList()), daysPerWeek = 3, weeks = 4),
        number = 1,
        week = 1,
        blocks = blocks.toList(),
        plan = plan,
    )

    private fun ProgramDay.order() = today.inOrder().map { it.set.exercise }

    @Test
    fun `two blocks marked alike side by side pair and are done in turn`() {
        val day = day(swings, push.paired(), pullUp.paired())
        assertEquals(listOf(Superset(1, SupersetRest.Shared, 0)), day.today.supersets())
        assertEquals(
            listOf(
                BuiltInExercise.TwoHandSwing,
                BuiltInExercise.TwoHandSwing,
                BuiltInExercise.PushUp,
                BuiltInExercise.PullUp,
                BuiltInExercise.PushUp,
                BuiltInExercise.PullUp,
                BuiltInExercise.PushUp,
                BuiltInExercise.PullUp,
            ),
            day.order(),
        )
    }

    @Test
    fun `a pair's places say A1 and A2 and each other's lift`() {
        val blocks = listOf(push.paired(), pullUp.paired(), squat.paired(), swings.paired())
        assertEquals("A1", blocks.supersetPlace(0)?.mark)
        assertEquals(BuiltInExercise.PullUp, blocks.supersetPlace(0)?.partner)
        assertEquals("A2", blocks.supersetPlace(1)?.mark)
        assertEquals("B1", blocks.supersetPlace(2)?.mark)
        assertEquals("B2", blocks.supersetPlace(3)?.mark)
    }

    @Test
    fun `a mark alone or marked otherwise pairs nothing`() {
        assertTrue(listOf(push.paired(), pullUp).supersets().isEmpty())
        assertTrue(listOf(push.paired(), squat, pullUp.paired()).supersets().isEmpty())
        assertTrue(listOf(push.paired(), pullUp.paired(SupersetRest.Alternating)).supersets().isEmpty())
    }

    @Test
    fun `the same lift in both halves never pairs`() {
        assertTrue(listOf(push.paired(), push.paired()).supersets().isEmpty())
    }

    @Test
    fun `only sets of a counted lift pair`() {
        val emom = swings.copy(kind = BlockKind.Emom).paired()
        val hold = Block(BuiltInExercise.Plank, rounds = 3, reps = 30).paired()
        assertTrue(listOf(push.paired(), emom).supersets().isEmpty())
        assertTrue(listOf(push.paired(), hold).supersets().isEmpty())
    }

    @Test
    fun `a skipped half leaves the other on its own`() {
        val plan = SessionPlan.of(listOf(push.paired(), pullUp.paired())).skip(1, 0)
        assertTrue(plan.supersets().isEmpty())
    }

    @Test
    fun `the longer block's rounds are left over after the pair's`() {
        val day = day(push.copy(rounds = 2).paired(), pullUp.paired())
        assertEquals(
            listOf(
                BuiltInExercise.PushUp,
                BuiltInExercise.PullUp,
                BuiltInExercise.PushUp,
                BuiltInExercise.PullUp,
                BuiltInExercise.PullUp,
            ),
            day.order(),
        )
    }

    @Test
    fun `a one-sided lift does both sides before its partner`() {
        val row = Block(BuiltInExercise.Row, rounds = 2, reps = 8).paired()
        val sets = day(row, squat.paired()).today.inOrder().map { it.set.exercise to it.set.side }
        assertEquals(
            listOf(
                BuiltInExercise.Row to Side.Left,
                BuiltInExercise.Row to Side.Right,
                BuiltInExercise.GobletSquat to null,
                BuiltInExercise.Row to Side.Left,
                BuiltInExercise.Row to Side.Right,
                BuiltInExercise.GobletSquat to null,
            ),
            sets,
        )
    }

    @Test
    fun `sharing the rest A1 goes straight on to A2`() {
        val straight = day(push.paired(), pullUp.paired()).today.inOrder().map { it.straightOn }
        assertEquals(listOf(true, false, true, false, true, false), straight)
    }

    @Test
    fun `resting after each nothing goes straight on`() {
        val day = day(push.paired(SupersetRest.Alternating), pullUp.paired(SupersetRest.Alternating))
        assertTrue(day.today.inOrder().none { it.straightOn })
    }

    @Test
    fun `the next set takes the pair in turn and says where it stands`() {
        val day = day(push.paired(), pullUp.paired())
        val first = day.nextSet(emptyList())
        assertEquals(BuiltInExercise.PushUp, first?.set?.exercise)
        assertEquals("A1", first?.superset?.mark)
        assertTrue(first?.straightOn == true)

        val second = day.nextSet(logged(BuiltInExercise.PushUp, 1))
        assertEquals(BuiltInExercise.PullUp, second?.set?.exercise)
        assertEquals("A2", second?.superset?.mark)
        assertEquals(2, second?.number)
        assertEquals(3, second?.index)
        assertFalse(second?.straightOn == true)
        assertEquals(1, second?.block)

        val third = day.nextSet(logged(BuiltInExercise.PushUp, 1) + logged(BuiltInExercise.PullUp, 1))
        assertEquals(BuiltInExercise.PushUp, third?.set?.exercise)
        assertEquals(3, third?.number)
    }

    @Test
    fun `a block on its own is in no superset`() {
        assertNull(day(push, pullUp).nextSet(emptyList())?.superset)
    }

    @Test
    fun `plan rows count each half's sets as done whatever the order`() {
        val day = day(push.paired(), pullUp.paired())
        val rows = day.planRows(logged(BuiltInExercise.PushUp, 2) + logged(BuiltInExercise.PullUp, 1))
        assertEquals(listOf(2, 1), rows.map { it.done })
    }

    @Test
    fun `pairing marks both blocks and undoing it both again`() {
        val paired = listOf(push, pullUp, squat).pairedWithNext(0, SupersetRest.Alternating)
        assertEquals(listOf(SupersetRest.Alternating, SupersetRest.Alternating, null), paired.map { it.superset })
        assertEquals(listOf(null, null, null), paired.pairedWithNext(0, null).map { it.superset })
    }

    @Test
    fun `pairing a block already paired takes it from its old pair`() {
        val blocks = listOf(
            push,
            pullUp,
            squat,
        ).pairedWithNext(0, SupersetRest.Shared).pairedWithNext(1, SupersetRest.Shared)
        assertEquals(listOf(null, SupersetRest.Shared, SupersetRest.Shared), blocks.map { it.superset })
    }

    @Test
    fun `removing or moving a block of a pair undoes it and never pairs another`() {
        val draft = ProgramDraft("p", listOf(listOf(push, pullUp, squat, swings)))
            .pairWithNext(0, 0, SupersetRest.Shared)
            .pairWithNext(0, 2, SupersetRest.Shared)
        assertEquals(2, draft.days[0].supersets().size)
        val removed = draft.removeBlock(0, 1)
        assertEquals(listOf(null, SupersetRest.Shared, SupersetRest.Shared), removed.days[0].map { it.superset })
        val moved = draft.moveBlock(0, 1, 1)
        // The pull-up lands between B1 and B2, which splits them too.
        assertEquals(listOf(null, null, null, null), moved.days[0].map { it.superset })
    }

    @Test
    fun `a block changed to its partner's lift leaves the pair`() {
        val draft = ProgramDraft("p", listOf(listOf(push, pullUp))).pairWithNext(0, 0, SupersetRest.Shared)
        val changed = draft.changeBlock(0, 1) { it.withExercise(BuiltInExercise.PushUp) }
        assertEquals(listOf(null, null), changed.days[0].map { it.superset })
    }

    @Test
    fun `a swap keeps a block in its superset`() {
        val plan = SessionPlan.of(listOf(push.paired(), pullUp.paired())).swap(1, BuiltInExercise.Row, 0)
        assertEquals(1, plan.supersets().size)
    }

    @Test
    fun `sharing the rest the estimate leaves out the rests after A1`() {
        val alone = day(push, pullUp).estimatedSeconds(restSeconds = 60)
        val shared = day(push.paired(), pullUp.paired()).estimatedSeconds(restSeconds = 60)
        val alternating = day(push.paired(SupersetRest.Alternating), pullUp.paired(SupersetRest.Alternating))
            .estimatedSeconds(restSeconds = 60)
        assertEquals(alone - 3 * 60, shared)
        assertEquals(alone, alternating)
    }

    @Test
    fun `done as planned a superset's sets fall in turn with no rest before A2 when shared`() {
        val day = day(push.paired(), pullUp.paired())
        val done = day.doneAsPlanned(finishedAt = 100_000_000L, before = emptyList(), restSeconds = 60) {
            Load.Bell(16.0)
        }
        val sets = done!!.sets
        assertEquals(day.order(), sets.map { it.exercise })
        // A pull-up's 5 reps at its pace straight after the push: no rest between.
        val gap = sets[1].loggedAtEpochMillis - sets[0].loggedAtEpochMillis
        assertTrue(gap < 60_000L)
        val restGap = sets[2].loggedAtEpochMillis - sets[1].loggedAtEpochMillis
        assertTrue(restGap >= 60_000L)
    }
}
