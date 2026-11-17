package dev.justinsmith.interval.platform

import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthConnectWorkoutTest {
    private val workout = HealthWorkout(
        id = "interval-42",
        kind = HealthWorkoutKind.Intervals,
        title = "Tabata",
        startedAtEpochMillis = 1_000,
        endedAtEpochMillis = 2_000,
        segments = listOf(HealthSegment(1_000, 2_000, HealthMove.Work)),
        calories = 12,
        notes = "Hard · Felt strong",
        version = 7,
    )

    @Test
    fun `a workout with positive calories writes both the session and the total calories`() {
        val records = workout.records()

        assertEquals(2, records.size)
        val session = records[0] as ExerciseSessionRecord
        val calories = records[1] as TotalCaloriesBurnedRecord
        assertEquals("interval-42", session.metadata.clientRecordId)
        assertEquals(7L, session.metadata.clientRecordVersion)
        assertEquals("interval-42-calories", calories.metadata.clientRecordId)
        assertEquals(12.0, calories.energy.inKilocalories)
    }

    @Test
    fun `a short workout whose calorie estimate rounds to zero still writes the session alone`() {
        val records = workout.copy(calories = 0).records()

        assertEquals(1, records.size)
        assertTrue(records.single() is ExerciseSessionRecord)
    }

    @Test
    fun `a workout that did not advance the clock writes no records`() {
        val sameInstant = workout.copy(endedAtEpochMillis = workout.startedAtEpochMillis, segments = emptyList())

        assertEquals(emptyList(), sameInstant.records())
    }
}
