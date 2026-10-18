package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals

class CaloriesTest {
    @Test
    fun `work burns at the effort's MET by the ACSM equation`() {
        // 8 MET × 3.5 × 70 kg / 200 = 9.8 kcal a minute.
        assertEquals(98, estimateCalories(workSeconds = 600, otherSeconds = 0, weightKg = 70.0, Effort.Hard))
    }

    @Test
    fun `rest and get ready burn at the recovery MET whatever the effort`() {
        // 2.5 MET × 3.5 × 80 kg / 200 = 3.5 kcal a minute.
        assertEquals(7, estimateCalories(workSeconds = 0, otherSeconds = 120, weightKg = 80.0, Effort.Max))
        assertEquals(7, estimateCalories(workSeconds = 0, otherSeconds = 120, weightKg = 80.0, Effort.Easy))
    }

    @Test
    fun `work and recovery add up and round to the nearest calorie`() {
        // 11.2 kcal a minute for 4 minutes and 3.5 for 2: 51.8.
        assertEquals(52, estimateCalories(workSeconds = 240, otherSeconds = 120, weightKg = 80.0, Effort.Hard))
    }

    @Test
    fun `harder effort burns more`() {
        val burned = Effort.entries.map { estimateCalories(600, 0, 70.0, it) }
        assertEquals(burned.sorted(), burned)
        assertEquals(burned.distinct(), burned)
    }

    @Test
    fun `weights convert between units in whole numbers`() {
        assertEquals(72, WeightUnit.Kg.fromKg(72.4))
        assertEquals(154, WeightUnit.Lb.fromKg(70.0))
        assertEquals(180, WeightUnit.Lb.fromKg(WeightUnit.Lb.toKg(180)))
        assertEquals(70.0, WeightUnit.Kg.toKg(70))
    }

    private val config = IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 8)

    @Test
    fun `a finished workout recorded without work time planned all of its work`() {
        assertEquals(160, config.plannedWorkSeconds(elapsedSeconds = 250, endedEarly = false))
    }

    @Test
    fun `an ended workout recorded without work time worked as far as its time reached`() {
        // Get ready, a round of work and rest, then 15 seconds into the next work.
        assertEquals(35, config.plannedWorkSeconds(elapsedSeconds = 10 + 30 + 15, endedEarly = true))
        assertEquals(0, config.plannedWorkSeconds(elapsedSeconds = 5, endedEarly = true))
        assertEquals(160, config.plannedWorkSeconds(elapsedSeconds = 1_000, endedEarly = true))
    }
}
