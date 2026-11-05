package dev.justinsmith.interval.acceptance

import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.platform.Speech
import dev.justinsmith.interval.timer.Block
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

/**
 * A sequence (journey to come with its editor, sprint 0.5's ticket 3): a preset of blocks runs each block's rounds
 * one after another with the rest between them, says the block as it goes, and is kept with the workout as its
 * shape.
 */
class SequenceAcceptanceTest {
    private val twoBlocks = IntervalConfig(
        workSeconds = 20,
        restSeconds = 10,
        rounds = 2,
        then = listOf(Block(workSeconds = 40, restSeconds = 20, rounds = 2)),
        blockRestSeconds = 30,
    )

    @Test
    fun `a preset of two blocks runs both with a rest between them and finishes`() = acceptanceScenario(
        storage = FakeStorage().apply { repository().presets.savePreset(Preset("Two blocks", twoBlocks)) },
    ) {
        openAllPresets()
        seePresetReading("Two blocks", "20s on · 10s off · ×2, then 40s on · 20s off · ×2 · 30s between blocks")
        openPreset("Two blocks")
        startWorkout()
        seePhase("Get ready")
        letTimePass(11.seconds)
        seePhase("Work")
        seeRound("Block 1 of 2 · Round 1 of 2")
        heard(Speech.Work(lastRound = false, block = 1))
        // The first block's 2 × 20 s on with 10 s between, then the rest before the second.
        letTimePass(50.seconds)
        seePhase("Rest between blocks")
        heard(Speech.BlockRest(block = 2))
        see("Next: block 2 of 2 · 0:40")
        letTimePass(30.seconds)
        seePhase("Work")
        seeRound("Block 2 of 2 · Round 1 of 2")
        heard(Speech.Work(lastRound = false, block = 2))
        letTimePass((2 * 40 + 20).seconds)
        see("Saved to history")
        see("4/4")
        workoutKeptAsSequence(blocks = 2)
    }

    @Test
    fun `a block added on the Timer makes a sequence that runs both blocks`() = acceptanceScenario {
        seeTimer()
        lowerRoundsTo(2)
        addBlockOnTimer()
        setLastBlockWork("1:00")
        finishEditingWorkout(blocks = 2)
        see("30s on · 15s off · ×2")
        see("1m on · 15s off · ×2")
        nothingSavedAsPreset()

        startWorkout()
        seePhase("Get ready")
        letTimePass(11.seconds)
        seeRound("Block 1 of 2 · Round 1 of 2")
        // The first block's 2 × 30s with 15s between, then its 15s rest before the second.
        letTimePass((2 * 30 + 15 + 15).seconds)
        seeRound("Block 2 of 2 · Round 1 of 2")
        seePhase("Work")
    }
}
