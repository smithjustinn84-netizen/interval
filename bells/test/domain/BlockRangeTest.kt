package dev.justinsmith.bells.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private const val MAX_REPS = 999

class BlockRangeTest {
    private val goblets = Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 8)

    @Test
    fun `a range turned on climbs from the reps the block had`() {
        val ranged = goblets.withRange(on = true, maxReps = MAX_REPS)
        assertEquals(RepRange(8, 8 + ProgramDraft.RANGE_SPAN), ranged.range)
        assertEquals(8, ranged.reps)
        assertNull(ranged.withRange(on = false, maxReps = MAX_REPS).range)
    }

    @Test
    fun `a range turned on at the most reps there can be climbs to them`() {
        assertEquals(
            RepRange(MAX_REPS - 1, MAX_REPS),
            goblets.copy(reps = MAX_REPS).withRange(on = true, maxReps = MAX_REPS).range,
        )
    }

    @Test
    fun `the reps stay within a range narrowed past them`() {
        val ranged = goblets.copy(reps = 12, range = RepRange(8, 13))
        assertEquals(10, ranged.withRange(RepRange(8, 10)).reps)
        assertEquals(11, ranged.copy(reps = 9).withRange(RepRange(11, 13)).reps)
    }

    @Test
    fun `a ladder or a hold takes no range`() {
        val ranged = goblets.withRange(on = true, maxReps = MAX_REPS)
        assertNull(ranged.asKind(BlockKind.Ladder).range)
        assertNull(ranged.withExercise(BuiltInExercise.Plank).range)
        assertEquals(ranged.range, ranged.asKind(BlockKind.Emom).range)
    }
}
