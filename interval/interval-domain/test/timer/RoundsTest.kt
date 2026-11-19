package dev.justinsmith.interval.timer

import dev.justinsmith.interval.model.ClassSample
import dev.justinsmith.interval.model.SportClass.Companion.SAMPLE_SECONDS
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RoundsTest {
    private fun steady(seconds: Int, bpm: Int, movement: Float) =
        List(seconds / SAMPLE_SECONDS) { ClassSample(bpm, movement) }

    /** A rest: the heart rate falling evenly from [from] to [to], the arm still. */
    private fun rest(seconds: Int, from: Int, to: Int): List<ClassSample> {
        val count = seconds / SAMPLE_SECONDS
        return List(count) { ClassSample(from + (to - from) * (it + 1) / count, STILL) }
    }

    private fun sparring(rounds: Int, restSeconds: Int = 60, restTo: Int = 150): List<ClassSample> =
        steady(seconds = 600, bpm = 110, movement = 0.3f) +
            (1..rounds).flatMap { round ->
                steady(seconds = 300, bpm = 170, movement = 1f) +
                    if (round < rounds) rest(restSeconds, from = 170, to = restTo) else emptyList()
            } +
            rest(seconds = 120, from = 170, to = 120) +
            steady(seconds = 300, bpm = 100, movement = 0.1f)

    private fun assertNear(expected: Int, actual: Int, within: Int = 2 * SAMPLE_SECONDS) =
        assertTrue(abs(expected - actual) <= within, "expected about $expected but was $actual")

    @Test
    fun `five rounds with a minute between are five rounds`() {
        val rounds = detectRounds(sparring(rounds = 5))

        assertEquals(5, rounds.size)
        rounds.forEach { assertNear(300, it.seconds) }
        assertNear(600, rounds.first().startSecond)
        assertNear(960, rounds[1].startSecond)
    }

    @Test
    fun `a round says its heart rate`() {
        val round = detectRounds(sparring(rounds = 3)).first()

        assertNear(170, round.averageBpm, within = 3)
        assertEquals(170, round.peakBpm)
    }

    @Test
    fun `the rest after a round is how far the heart rate fell`() {
        val rounds = detectRounds(sparring(rounds = 3, restSeconds = 90, restTo = 140))

        val first = rounds.first()
        val drop30 = assertNotNull(first.drop30)
        val drop60 = assertNotNull(first.drop60)
        assertNear(10, drop30, within = 4)
        assertNear(20, drop60, within = 4)
        assertTrue(drop60 > drop30)
    }

    @Test
    fun `a rest the next round cuts short has no minute's drop`() {
        val rounds = detectRounds(sparring(rounds = 3, restSeconds = 40, restTo = 155))

        assertEquals(3, rounds.size)
        assertNotNull(rounds.first().drop30)
        assertNull(rounds.first().drop60)
    }

    @Test
    fun `a recording that ends with its round has no drop`() {
        val samples =
            steady(seconds = 600, bpm = 105, movement = 0.2f) + steady(seconds = 300, bpm = 172, movement = 1f)

        val round = detectRounds(samples).single()

        assertNull(round.drop30)
        assertNull(round.drop60)
    }

    @Test
    fun `a short dip stays in its round`() {
        val samples = steady(seconds = 600, bpm = 110, movement = 0.3f) +
            steady(seconds = 150, bpm = 170, movement = 1f) +
            steady(seconds = 15, bpm = 120, movement = 1f) +
            steady(seconds = 150, bpm = 170, movement = 1f) +
            steady(seconds = 300, bpm = 100, movement = 0.1f)

        val round = detectRounds(samples).single()

        assertNear(315, round.seconds)
    }

    @Test
    fun `with no movement recorded the heart rate alone splits rounds`() {
        val still = sparring(rounds = 3, restSeconds = 90, restTo = 115).map { it.copy(movement = 0f) }

        assertEquals(3, detectRounds(still).size)
    }

    @Test
    fun `a burst too short is no round`() {
        val samples = steady(seconds = 900, bpm = 110, movement = 0.3f) +
            steady(seconds = 60, bpm = 175, movement = 1f) +
            steady(seconds = 900, bpm = 105, movement = 0.2f)

        assertEquals(emptyList(), detectRounds(samples))
    }

    @Test
    fun `a class at one effort has no rounds`() {
        assertEquals(emptyList(), detectRounds(steady(seconds = 3600, bpm = 128, movement = 0.4f)))
    }

    @Test
    fun `nothing read is no rounds`() {
        assertEquals(emptyList(), detectRounds(steady(seconds = 3600, bpm = 0, movement = 0f)))
        assertEquals(emptyList(), detectRounds(emptyList()))
    }

    @Test
    fun `a stretch unread inside a round leaves its heart rate alone`() {
        val samples = steady(seconds = 600, bpm = 110, movement = 0.3f) +
            steady(seconds = 150, bpm = 170, movement = 1f) +
            steady(seconds = 10, bpm = 0, movement = 1f) +
            steady(seconds = 150, bpm = 170, movement = 1f) +
            steady(seconds = 300, bpm = 100, movement = 0.1f)

        val round = detectRounds(samples).single()

        assertEquals(170, round.averageBpm)
    }

    @Test
    fun `a recording is kept a sample every five seconds`() {
        val bpm = List(10) { 100 } + List(5) { 0 } + listOf(120, 0, 0, 0, 130)
        val magnitudes = List(52 * 5) { 1f } + List(52 * 5) { if (it % 2 == 0) 0.5f else 1.5f }

        val samples = classSamples(bpm, magnitudes, accelerometerHz = 52)

        assertEquals(listOf(100, 100, 0, 125), samples.map { it.bpm })
        assertEquals(0f, samples[0].movement)
        assertEquals(0.5f, samples[1].movement)
        assertEquals(0f, samples[3].movement)
    }

    private companion object {
        const val STILL = 0.05f
    }
}
