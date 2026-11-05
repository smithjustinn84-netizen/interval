package dev.justinsmith.lift.data

import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.BandColour
import dev.justinsmith.lift.domain.BandLevel
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.BlockKind
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Focus
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.RepRange
import dev.justinsmith.lift.domain.band
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StoredProgramsTest {
    private val heavy = Band.of(BandLevel.Heavy)
    private val made = CustomExercise("a1", "Sandbag carry", Equipment.Bodyweight, Measure.Reps, false, Focus.FullBody)

    private fun row(days: String) = CustomProgramEntity("p1", "Mine", 3, 4, days, updatedAtEpochMillis = 0)

    @Test
    fun `blocks keep the load they name`() {
        val days = listOf(
            listOf(
                Block(
                    BuiltInExercise.Press,
                    rounds = 5,
                    reps = 5,
                    double = true,
                    load = Load.Bell(16.0, double = true),
                ),
                Block(BuiltInExercise.BandPullApart, rounds = 3, reps = 15, load = Load.Banded(heavy)),
                Block(BuiltInExercise.PushUp, rounds = 3, reps = 10, load = Load.Bodyweight(0.0)),
                Block(BuiltInExercise.BarbellBackSquat, rounds = 5, reps = 5, load = Load.Bar(62.5)),
                Block(BuiltInExercise.TwoHandSwing, rounds = 10, reps = 10, kind = BlockKind.Emom),
                Block(
                    BuiltInExercise.GobletSquat,
                    rounds = 3,
                    reps = 12,
                    load = Load.Bell(16.0),
                    range = RepRange(10, 15),
                ),
            ),
        )
        assertEquals(days, row(encodeDays(days)).toProgram()?.days)
    }

    @Test
    fun `a block's load is stored as the parts it always was`() {
        val days = listOf(
            listOf(
                Block(
                    BuiltInExercise.Press,
                    rounds = 5,
                    reps = 5,
                    double = true,
                    load = Load.Bell(16.0, double = true),
                ),
                Block(BuiltInExercise.BandPullApart, rounds = 3, reps = 15, load = Load.Banded(heavy)),
            ),
        )
        assertEquals(
            """[[{"exercise":"Press","kind":"Sets","rounds":5,"reps":5,"double":true,"kg":16.0},""" +
                """{"exercise":"BandPullApart","kind":"Sets","rounds":3,"reps":15,"band":"Heavy"}]]""",
            encodeDays(days),
        )
    }

    @Test
    fun `a block's band is the user's of that id and one gone stands in by its id`() {
        val stored = """[[{"exercise":"BandPullApart","kind":"Sets","rounds":3,"reps":15,"band":"mine"}]]"""
        val mine = Band("mine", "Door loop", BandColour.Purple, 5)
        assertEquals(Load.Banded(mine), row(stored).toProgram(bands = listOf(mine))?.days?.single()?.single()?.load)
        assertEquals("mine", row(stored).toProgram()?.days?.single()?.single()?.load?.band?.id)
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
        assertNull(blocks[0].load)
        assertNull(blocks[1].load)
    }

    @Test
    fun `a block can name an exercise the user added`() {
        val days = listOf(listOf(Block(made, rounds = 3, reps = 8, load = Load.Bodyweight(10.0))))
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
