package dev.justinsmith.lift.programs

import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.SetLog
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecapArtTest {
    @Test
    fun `a heavier finishing set spans rightward and marks a gain`() {
        val first = SetLog.of(
            1,
            BuiltInExercise.TwoHandSwing,
            kg = 16.0,
            reps = 10,
            side = null,
            loggedAtEpochMillis = 0,
        )
        val last = SetLog.of(
            2,
            BuiltInExercise.TwoHandSwing,
            kg = 24.0,
            reps = 10,
            side = null,
            loggedAtEpochMillis = 1,
        )
        val span = liftChangeSpan(first, last)
        assertTrue(span.gained)
        assertTrue(span.end > span.start)
    }

    @Test
    fun `a steady finishing set stays balanced without marking a gain`() {
        val first = SetLog.of(1, BuiltInExercise.Press, kg = 16.0, reps = 5, side = null, loggedAtEpochMillis = 0)
        val last = SetLog.of(2, BuiltInExercise.Press, kg = 16.0, reps = 5, side = null, loggedAtEpochMillis = 1)
        val span = liftChangeSpan(first, last)
        assertFalse(span.gained)
        assertTrue(span.end > span.start)
    }

    @Test
    fun `bodyweight rep growth marks a gain without bell weight`() {
        val first = SetLog.of(1, BuiltInExercise.PullUp, kg = 0.0, reps = 5, side = null, loggedAtEpochMillis = 0)
        val last = SetLog.of(2, BuiltInExercise.PullUp, kg = 0.0, reps = 10, side = null, loggedAtEpochMillis = 1)
        val span = liftChangeSpan(first, last)
        assertTrue(span.gained)
        assertTrue(span.end > span.start)
    }

    @Test
    fun `a lighter finishing set spans leftward without marking a gain`() {
        val first = SetLog.of(
            1,
            BuiltInExercise.GobletSquat,
            kg = 24.0,
            reps = 10,
            side = null,
            loggedAtEpochMillis = 0,
        )
        val last = SetLog.of(2, BuiltInExercise.GobletSquat, kg = 16.0, reps = 10, side = null, loggedAtEpochMillis = 1)
        val span = liftChangeSpan(first, last)
        assertFalse(span.gained)
        assertTrue(span.end < span.start)
    }
}
