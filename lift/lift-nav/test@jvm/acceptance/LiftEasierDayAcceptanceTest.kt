package dev.justinsmith.lift.acceptance

import androidx.compose.ui.test.hasText
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test

/**
 * `journeys/lift-easier-day.xml`: after a session that felt very hard, the next on the program is offered an
 * easier day before its first set: presses and squats a bell lighter, swings and snatches shorter, rests by the
 * lift, and the program's day lighter on the grip named. Taken, today's plan is kept that way, and the offer goes.
 */
class LiftEasierDayAcceptanceTest {
    /** A session finished an hour before the clock's start, said to have felt very hard. */
    private fun afterAHardSession() = FakeLiftStorage().apply {
        val at = -HOUR_MILLIS
        sessions.sets.value += SetEntity(0, "hard", BuiltInExercise.Snatch.key, 24.0, 20, null, at)
        sessions.sessions.value += SessionEntity(
            "hard",
            at - HOUR_MILLIS,
            at,
            "Snatches",
            at,
            effort = PerceivedEffort.VeryHard.level,
        )
    }

    @Test
    fun `a very hard session is followed by an easier day offered`() = liftScenario(afterAHardSession()) {
        followProgram("20 minutes beside BJJ")
        // Train says why on the program's card; with its Easier today switched off, the day starts in full and the
        // session offers the easier plan again before its first set.
        see("Your last session felt very hard, and it was a day and a half ago at most")
        tap("Easier today")
        tap("Start day 1")
        seeEasierDay("Your last session felt very hard, and it was a day and a half ago at most")
        see("Day 2 of this program is the lighter one on the grip")
        // The ABC on the minute is thrown: its 15 minutes become 12.
        useEasierDay(rounds = 12)
    }

    @Test
    fun `train starts the day after a very hard session easier from its card`() = liftScenario(afterAHardSession()) {
        followProgram("20 minutes beside BJJ")
        see("Easier today")
        tap("Start day 1, easier")
        // Eased as it starts: the ABC on the minute is 12 rounds, and the session offers it no more.
        see("Start EMOM")
        see(hasText("1 of 12 sets", substring = true))
        seeNo("Use it")
    }
}

private const val HOUR_MILLIS = 3_600_000L
