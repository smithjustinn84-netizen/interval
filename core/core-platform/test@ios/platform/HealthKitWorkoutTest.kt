package dev.justinsmith.interval.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HealthKitWorkoutTest {
    private val workout = HealthWorkout(
        id = "interval-42",
        kind = HealthWorkoutKind.Strength,
        title = "Simple & Sinister · day 3",
        startedAtEpochMillis = 1_000,
        endedAtEpochMillis = 9_000,
        segments = listOf(
            HealthSegment(1_000, 3_000, HealthMove.KettlebellSwing, repetitions = 10),
            HealthSegment(3_000, 4_000, HealthMove.Rest),
            HealthSegment(4_000, 5_000, HealthMove.Pause),
            HealthSegment(5_000, 9_000, HealthMove.Squat, repetitions = 5),
        ),
        calories = 120,
        notes = "Felt strong",
        version = 7,
    )

    @Test
    fun `every segment but a pause is a stretch with its move and reps`() {
        val stretches = workout.toHealthKit().stretches

        assertEquals(
            listOf(
                HealthKitStretch(
                    1_000,
                    3_000,
                    mapOf(
                        HealthKitKeys.MOVE to "KettlebellSwing",
                        HealthKitKeys.REPETITIONS to 10,
                    ),
                ),
                HealthKitStretch(3_000, 4_000, mapOf(HealthKitKeys.MOVE to "Rest")),
                HealthKitStretch(5_000, 9_000, mapOf(HealthKitKeys.MOVE to "Squat", HealthKitKeys.REPETITIONS to 5)),
            ),
            stretches,
        )
    }

    @Test
    fun `a pause is a pause and a resume`() {
        assertEquals(listOf(HealthKitPause(4_000, 5_000)), workout.toHealthKit().pauses)
    }

    @Test
    fun `the workout and its calories each carry a sync id and the version`() {
        val written = workout.toHealthKit()

        assertEquals("interval-42", written.metadata[HealthKitKeys.SYNC_IDENTIFIER])
        assertEquals(7L, written.metadata[HealthKitKeys.SYNC_VERSION])
        assertEquals("interval-42-calories", written.caloriesSyncId)
        assertEquals(7L, written.version)
        assertEquals(120, written.calories)
    }

    @Test
    fun `the title and notes go in the metadata only when there are some`() {
        assertEquals("Simple & Sinister · day 3", workout.toHealthKit().metadata[HealthKitKeys.TITLE])
        assertEquals("Felt strong", workout.toHealthKit().metadata[HealthKitKeys.NOTES])

        val bare = workout.copy(title = null, notes = null, calories = null).toHealthKit()

        assertNull(bare.metadata[HealthKitKeys.TITLE])
        assertNull(bare.metadata[HealthKitKeys.NOTES])
        assertNull(bare.calories)
    }
}
