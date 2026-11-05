package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IntervalConfigTest {
    @Test
    fun `stepping clamps to each field's range`() {
        assertEquals(0, IntervalConfig(prepSeconds = 0).stepped(ConfigField.Prep, -1).prepSeconds)
        assertEquals(5, IntervalConfig(workSeconds = 5).stepped(ConfigField.Work, -1).workSeconds)
        assertEquals(99, IntervalConfig(rounds = 99).stepped(ConfigField.Rounds, 1).rounds)
        assertEquals(20, IntervalConfig(restSeconds = 15).stepped(ConfigField.Rest, 1).restSeconds)
    }

    @Test
    fun `a value out of its range is refused`() {
        assertFailsWith<IllegalArgumentException> { IntervalConfig(rounds = 0) }
        assertFailsWith<IllegalArgumentException> { IntervalConfig(workSeconds = 1) }
        assertFailsWith<IllegalArgumentException> { IntervalConfig(coolDownSeconds = -1) }
    }

    @Test
    fun `sanitized takes each value to the nearest in its range`() {
        val config = IntervalConfig.sanitized(prepSeconds = -5, workSeconds = 99_999, restSeconds = 15, rounds = 0)
        assertEquals(IntervalConfig(prepSeconds = 0, workSeconds = 3600, restSeconds = 15, rounds = 1), config)
        assertTrue(config.totalSeconds > 0)
    }

    @Test
    fun `a ladder that would fall below five seconds is refused`() {
        IntervalConfig(workSeconds = 45, rounds = 5, workStepSeconds = -10)
        assertFailsWith<IllegalArgumentException> {
            IntervalConfig(workSeconds = 40, rounds = 5, workStepSeconds = -10)
        }
        assertFailsWith<IllegalArgumentException> { IntervalConfig(workStepSeconds = 305) }
        assertFailsWith<IllegalArgumentException> {
            IntervalConfig(workSeconds = 3500, rounds = 3, workStepSeconds = 60)
        }
    }

    @Test
    fun `sanitized shortens a ladder's step so the last round fits`() {
        assertEquals(-5, IntervalConfig.sanitized(workSeconds = 40, rounds = 5, workStepSeconds = -10).workStepSeconds)
        assertEquals(50, IntervalConfig.sanitized(workSeconds = 3500, rounds = 3, workStepSeconds = 60).workStepSeconds)
        assertEquals(45, IntervalConfig.sanitized(workSeconds = 3510, rounds = 3, workStepSeconds = 60).workStepSeconds)
        assertEquals(0, IntervalConfig.sanitized(workSeconds = 10, rounds = 8, workStepSeconds = -5).workStepSeconds)
        assertEquals(-10, IntervalConfig.sanitized(workSeconds = 45, rounds = 5, workStepSeconds = -10).workStepSeconds)
    }

    @Test
    fun `less work or more rounds on a falling ladder shorten its step rather than fail`() {
        val ladder = IntervalConfig(workSeconds = 60, rounds = 5, workStepSeconds = -10)
        assertEquals(-5, ladder.withValue(ConfigField.Work, 30).workStepSeconds)
        assertEquals(-5, ladder.withValue(ConfigField.Rounds, 10).workStepSeconds)
        assertEquals(-10, ladder.stepped(ConfigField.Work, 1).workStepSeconds)
        assertEquals(-10, ladder.withValue(ConfigField.WorkStep, -20).workStepSeconds)
    }

    @Test
    fun `a ladder's step shows its sign`() {
        assertEquals("+0:10", ConfigField.WorkStep.format(10))
        assertEquals("\u22121:00", ConfigField.WorkStep.format(-60))
        assertEquals("0:00", ConfigField.WorkStep.format(0))
    }

    @Test
    fun `a ladder is part of the shape`() {
        val config = IntervalConfig(workSeconds = 20, rounds = 5)
        assertTrue(!config.sameShape(config.copy(workStepSeconds = 10)))
    }

    @Test
    fun `a sequence refuses a block out of range and too many blocks`() {
        assertFailsWith<IllegalArgumentException> { Block(workSeconds = 2, restSeconds = 10, rounds = 2) }
        assertFailsWith<IllegalArgumentException> {
            Block(workSeconds = 10, restSeconds = 10, rounds = 3, workStepSeconds = -5)
        }
        val block = Block(workSeconds = 20, restSeconds = 10, rounds = 2)
        assertFailsWith<IllegalArgumentException> { IntervalConfig(then = List(IntervalConfig.MAX_BLOCKS) { block }) }
        assertFailsWith<IllegalArgumentException> { IntervalConfig(then = listOf(block), blockRestSeconds = 3601) }
        assertEquals(
            IntervalConfig.MAX_BLOCKS,
            IntervalConfig(
                then = List(IntervalConfig.MAX_BLOCKS - 1) {
                    block
                },
            ).blocks.size,
        )
    }

    @Test
    fun `sanitized takes each block to the nearest in its range and keeps at most ten`() {
        val block = Block(workSeconds = 20, restSeconds = 10, rounds = 2)
        val config = IntervalConfig.sanitized(then = List(12) { block }, blockRestSeconds = 5000)
        assertEquals(IntervalConfig.MAX_BLOCKS, config.blocks.size)
        assertEquals(3600, config.blockRestSeconds)
        assertEquals(
            Block(workSeconds = 5, restSeconds = 3600, rounds = 99, sets = 20),
            Block.sanitized(1, 9000, 500, 40),
        )
        assertEquals(
            -5,
            Block.sanitized(workSeconds = 15, restSeconds = 0, rounds = 3, workStepSeconds = -30).workStepSeconds,
        )
    }

    @Test
    fun `blocks are the first block in the config's own fields and those after it`() {
        val first = Block(workSeconds = 40, restSeconds = 20, rounds = 6, sets = 2, setRestSeconds = 60)
        val second = Block(workSeconds = 60, restSeconds = 30, rounds = 4, workStepSeconds = 10)
        val config = IntervalConfig(blockRestSeconds = 120).withBlocks(listOf(first, second))
        assertEquals(listOf(first, second), config.blocks)
        assertEquals(40 to 6, config.workSeconds to config.rounds)
        assertEquals(listOf(second), config.then)
        assertTrue(config.isSequence)
        assertEquals(0, config.withBlocks(listOf(second)).blockRestSeconds)
    }

    @Test
    fun `a stepper on a sequence changes its first block and keeps the others`() {
        val second = Block(workSeconds = 60, restSeconds = 30, rounds = 4)
        val config = IntervalConfig(then = listOf(second), blockRestSeconds = 90)
        val stepped = config.stepped(ConfigField.Rounds, +1)
        assertEquals(config.rounds + 1, stepped.rounds)
        assertEquals(listOf(second), stepped.then)
        assertEquals(90, stepped.blockRestSeconds)
    }

    @Test
    fun `the blocks after the first and the rest between them are part of the shape`() {
        val second = Block(workSeconds = 60, restSeconds = 30, rounds = 4)
        val sequence = IntervalConfig(then = listOf(second), blockRestSeconds = 90)
        assertTrue(sequence.sameShape(sequence.copy(prepSeconds = 0)))
        assertTrue(!sequence.sameShape(IntervalConfig()))
        assertTrue(!sequence.sameShape(sequence.copy(blockRestSeconds = 60)))
        assertTrue(!sequence.sameShape(sequence.copy(then = listOf(second.copy(rounds = 5)))))
    }
}
