package dev.justinsmith.bells.data

import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.BlockKind
import dev.justinsmith.bells.domain.BuiltInExercise
import dev.justinsmith.bells.domain.CustomExercise
import dev.justinsmith.bells.domain.Equipment
import dev.justinsmith.bells.domain.Focus
import dev.justinsmith.bells.domain.Measure
import dev.justinsmith.bells.domain.RepRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoredProgramsTest {
    private val made = CustomExercise("a1", "Sandbag carry", Equipment.Bodyweight, Measure.Reps, false, Focus.FullBody)

    private fun row(days: String) = CustomProgramEntity("p1", "Mine", 3, 4, days, updatedAtEpochMillis = 0)

    @Test
    fun `blocks keep the load they name`() {
        val days = listOf(
            listOf(
                Block(BuiltInExercise.Press, rounds = 5, reps = 5, double = true, kg = 16.0),
                Block(BuiltInExercise.BandPullApart, rounds = 3, reps = 15, band = "Heavy"),
                Block(BuiltInExercise.PushUp, rounds = 3, reps = 10, kg = 0.0),
                Block(BuiltInExercise.BarbellBackSquat, rounds = 5, reps = 5, kg = 62.5),
                Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = 10, kind = BlockKind.Emom),
                Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 12, kg = 16.0, range = RepRange(10, 15)),
            ),
        )
        assertEquals(days, row(encodeDays(days)).toProgram()?.days)
    }

    @Test
    fun `a block stored before loads reads with none`() {
        val old = """[[{"exercise":"TwoHandSwing","kind":"Sets","rounds":5,"reps":10}]]"""
        val block = row(old).toProgram()?.days?.single()?.single()
        assertEquals(Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10), block)
    }

    @Test
    fun `a range that does not climb is dropped`() {
        val stored = """[[{"exercise":"GobletSquat","kind":"Sets","rounds":3,"reps":10,""" +
            """"rangeBottom":12,"rangeTop":10}]]"""
        assertNull(row(stored).toProgram()?.days?.single()?.single()?.range)
    }

    @Test
    fun `a load that does not suit the exercise is dropped`() {
        val mixed = """[[{"exercise":"BandPullApart","kind":"Sets","rounds":3,"reps":15,"kg":24.0},""" +
            """{"exercise":"TwoHandSwing","kind":"Sets","rounds":5,"reps":10,"band":"Heavy"}]]"""
        val blocks = row(mixed).toProgram()?.days?.single().orEmpty()
        assertNull(blocks[0].kg)
        assertNull(blocks[1].band)
    }

    @Test
    fun `a block can name an exercise the user added`() {
        val days = listOf(listOf(Block(made, rounds = 3, reps = 8, kg = 10.0)))
        assertEquals(days, row(encodeDays(days)).toProgram(listOf(made))?.days)
        // Without the exercise the block, then its day, then the program goes.
        assertNull(row(encodeDays(days)).toProgram())
    }

    @Test
    fun `draft days keep an empty day across process recreation`() {
        val days = listOf(listOf(Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10)), emptyList())
        assertEquals(days, decodeDraftDays(encodeDays(days)))
    }
}
