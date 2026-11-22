package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasText
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.TestId
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test

/** `journeys/trends.xml`: the Log's All time opens Trends, which draws the weeks, the records and the tests. */
class TrendsAcceptanceTest {
    @Test
    fun `trends show the weeks the records and a test's results at its bell`() = acceptanceScenario(
        lift = FakeLiftStorage().apply {
            // Two snatch tests with a 16 kg bell, ten days and three days before the clock's start: 80, then 100.
            snatchTest("first", daysAgo = 10, rounds = 8)
            snatchTest("second", daysAgo = 3, rounds = 10)
        },
    ) {
        openTab("Log")
        openTrends()
        see("Time a week")
        see("Lifted a week")
        seeNo("Heart load a week")
        see("Records")
        see("1 record in 12 weeks")
        see(hasText("Heaviest 16 kg × 10", substring = true))
        see("Snatch test")
        see("100 reps with 16 kg")
        see("80 reps → 100 reps")
        goBack()
        seeNo("Time a week")
        tap("Trends")
        see("Snatch test")
    }
}

private fun IntervalAcceptanceDriver.openTrends() {
    tap("All time")
    tap("Trends")
    see(hasText("Since", substring = true))
}

/** A snatch test finished [daysAgo] days before the clock's start, [rounds] of ten snatches with a 16 kg bell. */
private fun FakeLiftStorage.snatchTest(uid: String, daysAgo: Int, rounds: Int) {
    val finished = -daysAgo * DAY_MILLIS
    val started = finished - TEST_MILLIS
    sessions.sessions.value += SessionEntity(
        uid,
        started,
        finished,
        "Snatch test",
        finished,
        programId = ProgramKey.Test(TestId.SnatchTest).stored,
    )
    sessions.sets.value += (0 until rounds).map {
        SetEntity(0, uid, BuiltInExercise.Snatch.key, BELL_KG, ROUND_REPS, null, started + it)
    }
}

private const val DAY_MILLIS = 86_400_000L
private const val TEST_MILLIS = 5 * 60_000L
private const val BELL_KG = 16.0
private const val ROUND_REPS = 10
