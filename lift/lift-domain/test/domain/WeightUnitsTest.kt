package dev.justinsmith.lift.domain

import dev.justinsmith.interval.timer.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WeightUnitsTest {
    @Test
    fun `standard kettlebells map to canonical US pound sizes strictly increasing`() {
        val setupKg = listOf(8.0, 12.0, 16.0, 20.0, 24.0, 32.0)
        val expectedSetupLb = listOf(18.0, 26.0, 35.0, 44.0, 53.0, 70.0)
        assertEquals(expectedSetupLb, setupKg.map { bellWeight(it, WeightUnit.Lb) })

        val allLb = standardBells.map { bellWeight(it, WeightUnit.Lb) }
        assertTrue(allLb.zipWithNext().all { (a, b) -> b > a })
        assertEquals(standardBells, standardBells.map { bellWeight(it, WeightUnit.Kg) })
    }

    @Test
    fun `canonical bars map to 25 35 and 45 lb and lb steps round trip cleanly`() {
        assertEquals(25.0, barWeight(10.0, WeightUnit.Lb))
        assertEquals(35.0, barWeight(15.0, WeightUnit.Lb))
        assertEquals(45.0, barWeight(20.0, WeightUnit.Lb))
        assertEquals(135.0, barWeight(lbToKg(135.0), WeightUnit.Lb))
        assertEquals(140.0, barWeight(lbToKg(140.0), WeightUnit.Lb))
        assertEquals(20.0, barWeight(20.0, WeightUnit.Kg))
    }

    @Test
    fun `session tonnage in pounds sums each set by its displayed weight`() {
        val sets = listOf(
            SetLog.of(1, BuiltInExercise.TwoHandSwing, kg = 16.0, reps = 10, side = null, loggedAtEpochMillis = 0),
            SetLog.of(
                2,
                BuiltInExercise.FrontSquat,
                kg = 16.0,
                reps = 5,
                side = null,
                loggedAtEpochMillis = 1,
                double = true,
            ),
            SetLog.of(3, BuiltInExercise.BarbellBackSquat, kg = 20.0, reps = 5, side = null, loggedAtEpochMillis = 2),
            SetLog.of(
                4,
                BuiltInExercise.BarbellBackSquat,
                kg = lbToKg(135.0),
                reps = 5,
                side = null,
                loggedAtEpochMillis = 3,
            ),
        )
        val session = LiftSession(
            uid = "s1",
            startedAtEpochMillis = 0,
            finishedAtEpochMillis = 1000,
            title = "Test",
            sets = sets,
        )
        // 35*10 + (2*35)*5 + 45*5 + 135*5 = 350 + 350 + 225 + 675 = 1600 lb
        assertEquals(1600.0, session.tonnage(WeightUnit.Lb))
        assertEquals(session.tonnageKg, session.tonnage(WeightUnit.Kg))
    }

    @Test
    fun `session tonnage in pounds leaves warm-ups out as in kilograms`() {
        val warmUp = SetLog(1, BuiltInExercise.TwoHandSwing, Load.Bell(8.0), 10, null, 0, warmUp = true)
        val work = SetLog(2, BuiltInExercise.TwoHandSwing, Load.Bell(16.0), 10, null, 1)
        val session = LiftSession("s1", 0, 1000, "Test", listOf(warmUp, work))
        assertEquals(350.0, session.tonnage(WeightUnit.Lb))
        assertEquals(160.0, session.tonnage(WeightUnit.Kg))
    }
}
