package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/**
 * A save that fails says so, over whatever screen is up. No journey: a full disk can't be made on the emulator,
 * so this is the only check, over a fake History that refuses every write.
 */
class SaveFailedAcceptanceTest {
    @Test
    fun `a workout that can't be saved says so and is not kept`() = acceptanceScenario {
        openTimer()
        diskIsFull()
        endWorkoutAfterFirstRound()
        seeSaveFailed()
        nothingIsSaved()
    }
}
