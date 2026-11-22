package dev.justinsmith.lift.domain

import dev.justinsmith.interval.timer.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BarbellTest {
    @Test
    fun `the empty bar takes no plates`() {
        assertEquals(emptyList(), platesPerSide(20.0, 20.0))
    }

    @Test
    fun `a side is loaded heaviest plate first`() {
        assertEquals(listOf(20.0), platesPerSide(60.0, 20.0))
        assertEquals(listOf(20.0, 1.25), platesPerSide(62.5, 20.0))
        assertEquals(listOf(25.0, 25.0, 25.0, 20.0, 2.5), platesPerSide(215.0, 20.0))
    }

    @Test
    fun `a lighter bar leaves more for the plates`() {
        assertEquals(listOf(20.0, 2.5), platesPerSide(60.0, 15.0))
        assertEquals(listOf(5.0), platesPerSide(20.0, 10.0))
    }

    @Test
    fun `a total under the bar or between the change plates cannot be loaded`() {
        assertNull(platesPerSide(17.5, 20.0))
        assertNull(platesPerSide(21.0, 20.0))
        assertNull(platesPerSide(21.25, 20.0))
    }

    @Test
    fun `a barbell lift starts from its last weight then the empty bar`() {
        val snatch = SetLog.of(1, BuiltInExercise.BarbellSnatch, 45.0, 2, null, 0)
        val squat = SetLog.of(2, BuiltInExercise.BarbellBackSquat, 100.0, 5, null, 0)
        val history = listOf(snatch, squat)
        assertEquals(45.0, suggestedBar(BuiltInExercise.BarbellSnatch, history, bar = 20.0))
        assertEquals(15.0, suggestedBar(BuiltInExercise.BarbellClean, history, bar = 15.0))
        assertEquals(DEFAULT_BAR_KG, suggestedBar(BuiltInExercise.BarbellClean, history, bar = null))
    }

    @Test
    fun `pound plates load a 45 lb and 35 lb bar heaviest first`() {
        assertEquals(emptyList(), platesPerSide(20.0, 20.0, WeightUnit.Lb))
        assertEquals(listOf(45.0), platesPerSide(lbToKg(135.0), 20.0, WeightUnit.Lb))
        assertEquals(listOf(45.0, 2.5), platesPerSide(lbToKg(140.0), 20.0, WeightUnit.Lb))
        assertEquals(listOf(45.0, 45.0), platesPerSide(lbToKg(225.0), 20.0, WeightUnit.Lb))
        assertEquals(listOf(25.0, 5.0), platesPerSide(lbToKg(95.0), 15.0, WeightUnit.Lb))
        assertNull(platesPerSide(lbToKg(40.0), 20.0, WeightUnit.Lb))
        assertNull(platesPerSide(lbToKg(137.5), 20.0, WeightUnit.Lb))
    }

    @Test
    fun `barbell stepping in pounds moves by 5 lb and lands back on the empty bar`() {
        val up = stepBarKg(20.0, 1, bar = 20.0, unit = WeightUnit.Lb)!!
        assertEquals(50.0, barWeight(up, WeightUnit.Lb))
        val back = stepBarKg(up, -1, bar = 20.0, unit = WeightUnit.Lb)
        assertEquals(20.0, back)
        assertNull(stepBarKg(20.0, -1, bar = 20.0, unit = WeightUnit.Lb))
        assertNull(stepBarKg(lbToKg(MAX_BAR_LB), 1, bar = 20.0, unit = WeightUnit.Lb))
        // Stepping from an off-grid kg weight snaps to the next 5 lb multiple.
        val snappedUp = stepBarKg(60.0, 1, bar = 20.0, unit = WeightUnit.Lb)!!
        assertEquals(135.0, barWeight(snappedUp, WeightUnit.Lb))
    }
}
