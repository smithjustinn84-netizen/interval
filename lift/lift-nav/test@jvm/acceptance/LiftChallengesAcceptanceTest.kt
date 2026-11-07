package dev.justinsmith.lift.acceptance

import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test

/**
 * `journeys/lift-challenges.xml`: the benchmarks under way show under the program on Lift's Today, worked out from
 * the sets logged: 10,000 swings over four weeks, the snatch test's best five minutes, and a lift's reps in all on
 * its page.
 */
class LiftChallengesAcceptanceTest {
    /** Yesterday: ten sets of 10 snatches a minute apart, then 1,200 swings in three sets. */
    private fun afterSnatchesAndSwings() = FakeLiftStorage().apply {
        val start = -DAY_MILLIS
        val snatches = (1..10).map { minute ->
            SetEntity(0, "s", BuiltInExercise.Snatch.key, 16.0, 10, null, start + minute * MINUTE_MILLIS)
        }
        val swings = (1..3).map { set ->
            SetEntity(
                0,
                "s",
                BuiltInExercise.TwoHandSwing.key,
                24.0,
                400,
                null,
                start + (12 + set * 15) * MINUTE_MILLIS,
            )
        }
        sessions.sets.value += snatches + swings
        sessions.sessions.value += SessionEntity("s", start, start + HOUR_MILLIS, "Snatches and swings", start)
    }

    @Test
    fun `the benchmarks under way show under the program`() = liftScenario(afterSnatchesAndSwings()) {
        see("10,000 swings")
        see("1200 in the last four weeks")
        see("Snatch test: 100 in 5 minutes")
        see("Best: 50 in 5 minutes")
    }
}

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 3_600_000L
private const val DAY_MILLIS = 86_400_000L
