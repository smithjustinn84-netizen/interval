package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals

class CaloriesTest {
    @Test
    fun `work burns at the activity's MET for the effort by the ACSM equation`() {
        // Hard on a rower is 11 MET: 11 × 3.5 × 70 kg / 200 = 13.475 kcal a minute.
        assertEquals(135, estimateCalories(600, 0, weightKg = 70.0, Effort.Hard, Activity.Rower))
        // Hard barbell work is 6 MET: 7.35 kcal a minute.
        assertEquals(74, estimateCalories(600, 0, weightKg = 70.0, Effort.Hard, Activity.Barbell))
    }

    @Test
    fun `rest and get ready burn at the recovery MET whatever the effort`() {
        // 2.5 MET × 3.5 × 80 kg / 200 = 3.5 kcal a minute.
        assertEquals(7, estimateCalories(workSeconds = 0, otherSeconds = 120, weightKg = 80.0, Effort.Max))
        assertEquals(7, estimateCalories(workSeconds = 0, otherSeconds = 120, weightKg = 80.0, Effort.Easy))
    }

    @Test
    fun `work and recovery add up and round to the nearest calorie`() {
        // Hard general work is 9 MET: 12.6 kcal a minute for 4 minutes, and 3.5 for 2: 57.4.
        assertEquals(57, estimateCalories(workSeconds = 240, otherSeconds = 120, weightKg = 80.0, Effort.Hard))
    }

    @Test
    fun `every activity burns more the harder the effort`() {
        Activity.entries.forEach { activity ->
            val mets = Effort.entries.map { activity.met(it) }
            assertEquals(mets.sorted().distinct(), mets, "$activity")
        }
    }

    @Test
    fun `an unknown or missing activity name reads as General`() {
        assertEquals(Activity.Bike, Activity.named("Bike"))
        assertEquals(Activity.General, Activity.named("Trampoline"))
        assertEquals(Activity.General, Activity.named(null))
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
