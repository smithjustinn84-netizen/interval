package dev.justinsmith.interval.workout

import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WorkoutEngine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ShownKeyTest {
    private val body = BodySettings(weightKg = 80.0)

    /** The run ticked at the ticker's pace from its start for [millis], a round tapped at each of [taps]. */
    private fun ticks(config: IntervalConfig, millis: Long, taps: Set<Long> = emptySet()): List<WorkoutRun> {
        var run =
            WorkoutRun(id = 1, engine = WorkoutEngine.start(config).state, running = true, startedAt = 0, body = body)
        val runs = mutableListOf(run)
        var now = 0L
        while (now < millis && !run.over) {
            now += TICK
            run = run.stepped(WorkoutEngine.tick(run.engine, TICK), now)
            if (now in taps) run = run.roundDone()
            runs += run
        }
        return runs
    }

    /** Every tick whose key matches the one before shows the same: dropping it loses nothing on screen. */
    private fun assertKeyHoldsTheScreen(runs: List<WorkoutRun>) {
        runs.zipWithNext().forEach { (before, after) ->
            if (before.shownKey == after.shownKey) {
                assertEquals(before.toUiState(emptyList(), emptyList()), after.toUiState(emptyList(), emptyList()))
            }
        }
    }

    @Test
    fun `ticks within a shown second share a key and show the same`() {
        val runs = ticks(IntervalConfig(prepSeconds = 3, workSeconds = 20, restSeconds = 10, rounds = 2), 70_000)

        assertKeyHoldsTheScreen(runs)
        val keys = runs.map { it.shownKey }.distinct().size
        assertTrue(keys < runs.size / 3, "$keys keys for ${runs.size} ticks")
    }

    @Test
    fun `a new shown second is a new key`() {
        val runs = ticks(IntervalConfig(prepSeconds = 0, workSeconds = 20, restSeconds = 10, rounds = 1), 2_000)

        assertNotEquals(runs.first().shownKey, runs.last().shownKey)
    }

    @Test
    fun `a cap's round seconds count from the last tap`() {
        val cap = IntervalConfig(prepSeconds = 0, workSeconds = 120, restSeconds = 0, rounds = 1, capped = true)
        val runs = ticks(cap, 30_000, taps = setOf(4_300L, 11_700L))

        assertKeyHoldsTheScreen(runs)
    }

    @Test
    fun `a pause is a new key at once`() {
        val run = ticks(IntervalConfig(prepSeconds = 0, workSeconds = 20, restSeconds = 10, rounds = 1), 1_000).last()

        assertNotEquals(run.shownKey, run.copy(running = false).shownKey)
    }

    private companion object {
        const val TICK = 100L
    }
}
