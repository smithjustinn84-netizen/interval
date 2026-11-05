package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CourseTest {
    /** A course as "Phase round:seconds", one interval after another. */
    private fun pinned(config: IntervalConfig) =
        Course.of(config).joinToString(" ") { "${it.phase} ${it.round}:${it.seconds}" }

    /** A course of sets as "Phase set.round:seconds". */
    private fun pinnedSets(config: IntervalConfig) =
        Course.of(config).joinToString(" ") { "${it.phase} ${it.set}.${it.round}:${it.seconds}" }

    private fun preset(name: String) = builtInPresets.first { it.name == name }.config

    @Test
    fun `tabata is get ready then eight rounds with no rest after the last`() {
        assertEquals(
            "Prep 1:10 Work 1:20 Rest 1:10 Work 2:20 Rest 2:10 Work 3:20 Rest 3:10 Work 4:20 Rest 4:10 " +
                "Work 5:20 Rest 5:10 Work 6:20 Rest 6:10 Work 7:20 Rest 7:10 Work 8:20",
            pinned(preset("Tabata")),
        )
    }

    @Test
    fun `hiit 30-30 is ten rounds of thirty on and thirty off`() {
        assertEquals(
            "Prep 1:10 " + (1..9).joinToString(" ") { "Work $it:30 Rest $it:30" } + " Work 10:30",
            pinned(preset("HIIT 30/30")),
        )
    }

    @Test
    fun `hangboard is six rounds of ten on and fifty off`() {
        assertEquals(
            "Prep 1:10 Work 1:10 Rest 1:50 Work 2:10 Rest 2:50 Work 3:10 Rest 3:50 Work 4:10 Rest 4:50 " +
                "Work 5:10 Rest 5:50 Work 6:10",
            pinned(preset("Hangboard")),
        )
    }

    @Test
    fun `long efforts is twelve rounds after five seconds to get ready`() {
        assertEquals(
            "Prep 1:5 " + (1..11).joinToString(" ") { "Work $it:45 Rest $it:15" } + " Work 12:45",
            pinned(preset("Long efforts")),
        )
    }

    @Test
    fun `a warm-up comes after get ready and a cool-down after the last round`() {
        val tabata = preset("Tabata").copy(warmUpSeconds = 120, coolDownSeconds = 180)
        val course = pinned(tabata)
        assertTrue(course.startsWith("Prep 1:10 WarmUp 1:120 Work 1:20 Rest 1:10 "), course)
        assertTrue(course.endsWith(" Rest 7:10 Work 8:20 CoolDown 8:180"), course)
        assertEquals(tabata.totalSeconds, Course.of(tabata).sumOf { it.seconds })
        assertEquals(10 + 120 + 8 * 20 + 7 * 10 + 180, tabata.totalSeconds)
    }

    @Test
    fun `every built-in is in one set and adds up to its total`() {
        builtInPresets.forEach { preset ->
            val course = Course.of(preset.config)
            assertTrue(course.all { it.set == 1 }, preset.name)
            assertEquals(preset.config.totalSeconds, course.sumOf { it.seconds }, preset.name)
            assertEquals(preset.config.rounds * preset.config.workSeconds, Course.workSeconds(preset.config))
        }
    }

    @Test
    fun `no get ready and no rest leave only the work`() {
        val config = IntervalConfig(prepSeconds = 0, workSeconds = 40, restSeconds = 0, rounds = 3)
        assertEquals("Work 1:40 Work 2:40 Work 3:40", pinned(config))
    }

    @Test
    fun `two sets of three rounds rest between the sets and not after the last`() {
        val config = IntervalConfig(prepSeconds = 5, workSeconds = 20, restSeconds = 10, rounds = 3)
        assertEquals(
            "Prep 1.1:5 Work 1.1:20 Rest 1.1:10 Work 1.2:20 Rest 1.2:10 Work 1.3:20 SetRest 1.3:60 " +
                "Work 2.1:20 Rest 2.1:10 Work 2.2:20 Rest 2.2:10 Work 2.3:20",
            pinnedSets(config.copy(sets = 2, setRestSeconds = 60)),
        )
        assertEquals(5 + 2 * (3 * 20 + 2 * 10) + 60, config.copy(sets = 2, setRestSeconds = 60).totalSeconds)
    }

    @Test
    fun `a set rest of none is the ordinary rest between the sets`() {
        val config = IntervalConfig(prepSeconds = 0, workSeconds = 20, restSeconds = 10, rounds = 2, sets = 2)
        assertEquals(
            "Work 1.1:20 Rest 1.1:10 Work 1.2:20 Rest 1.2:10 Work 2.1:20 Rest 2.1:10 Work 2.2:20",
            pinnedSets(config),
        )
        val noRest = config.copy(restSeconds = 0)
        assertEquals("Work 1.1:20 Work 1.2:20 Work 2.1:20 Work 2.2:20", pinnedSets(noRest))
    }

    @Test
    fun `the cool-down is in the last round of the last set`() {
        val config =
            IntervalConfig(prepSeconds = 0, workSeconds = 20, restSeconds = 0, rounds = 1, coolDownSeconds = 60)
        assertEquals(
            "Work 1.1:20 SetRest 1.1:30 Work 2.1:20 CoolDown 2.1:60",
            pinnedSets(config.copy(sets = 2, setRestSeconds = 30)),
        )
    }

    @Test
    fun `the engine walks the course interval by interval`() {
        val config = preset("Tabata").copy(warmUpSeconds = 60, coolDownSeconds = 60, sets = 3, setRestSeconds = 90)
        val walked = generateSequence(WorkoutEngine.start(config).state) { WorkoutEngine.advance(it).state }
            .takeWhile { !it.isDone }
            .map { Interval(it.phase, it.set, it.round, (it.phaseMillis / 1_000).toInt()) }
            .toList()
        assertEquals(Course.of(config), walked)
    }

    @Test
    fun `a ladder climbs a step each round and starts over with each set`() {
        val config =
            IntervalConfig(prepSeconds = 0, workSeconds = 20, restSeconds = 10, rounds = 3, workStepSeconds = 10)
        assertEquals("Work 1:20 Rest 1:10 Work 2:30 Rest 2:10 Work 3:40", pinned(config))
        assertEquals(20 + 30 + 40 + 2 * 10, config.totalSeconds)
        assertEquals(
            "Work 1.1:20 Rest 1.1:10 Work 1.2:30 Rest 1.2:10 Work 1.3:40 SetRest 1.3:60 " +
                "Work 2.1:20 Rest 2.1:10 Work 2.2:30 Rest 2.2:10 Work 2.3:40",
            pinnedSets(config.copy(sets = 2, setRestSeconds = 60)),
        )
    }

    @Test
    fun `a falling ladder works less each round`() {
        val config =
            IntervalConfig(prepSeconds = 0, workSeconds = 60, restSeconds = 0, rounds = 5, workStepSeconds = -10)
        assertEquals("Work 1:60 Work 2:50 Work 3:40 Work 4:30 Work 5:20", pinned(config))
        assertEquals(60 + 50 + 40 + 30 + 20, Course.workSeconds(config))
    }

    @Test
    fun `the engine walks a ladder with each round's own clock and time left`() {
        val config =
            IntervalConfig(prepSeconds = 0, workSeconds = 20, restSeconds = 10, rounds = 3, workStepSeconds = 10)
        val second = WorkoutEngine.advance(WorkoutEngine.advance(WorkoutEngine.start(config).state).state).state
        assertEquals(Phase.Work to 2, second.phase to second.round)
        assertEquals(30_000, second.remainingMillis)
        assertEquals((30 + 10 + 40) * 1_000L, second.sessionRemainingMillis)
    }

    @Test
    fun `every workout today is one block with its cool-down in the last set and round`() {
        val config = IntervalConfig(warmUpSeconds = 60, coolDownSeconds = 60, sets = 2, setRestSeconds = 30)
        assertEquals(1, config.blocks.size)
        builtInPresets.map { it.config }.plus(config).forEach { preset ->
            assertTrue(Course.of(preset).all { it.block == 0 }, preset.toString())
        }
        assertEquals(Interval(Phase.CoolDown, 2, 8, 60), Course.of(config).last())
    }

    @Test
    fun `a later block comes after the last round of the one before`() {
        val config = IntervalConfig(rounds = 3, sets = 2)
        val lastRound = WorkoutState(config, Phase.Work, round = 3, remainingMillis = 0, phaseMillis = 0, set = 2)
        assertTrue(Interval(Phase.Prep, 1, 1, 10, block = 1).comparedTo(lastRound) > 0)
        assertTrue(Interval(Phase.CoolDown, 2, 3, 10, block = 0).comparedTo(lastRound.copy(block = 1)) < 0)
    }

    @Test
    fun `the work behind each place of two sets of three rounds counts every round done`() {
        val config = IntervalConfig(rounds = 3, sets = 2, setRestSeconds = 30, coolDownSeconds = 60)
        var state = WorkoutEngine.start(config).state
        val behind = mutableListOf<String>()
        while (!state.isDone) {
            behind += "${state.phase} ${state.set}.${state.round}:${Course.workBehind(state)}"
            state = WorkoutEngine.advance(state).state
        }
        assertEquals(
            "Prep 1.1:0 Work 1.1:0 Rest 1.1:1 Work 1.2:1 Rest 1.2:2 Work 1.3:2 SetRest 1.3:3 " +
                "Work 2.1:3 Rest 2.1:4 Work 2.2:4 Rest 2.2:5 Work 2.3:5 CoolDown 2.3:6",
            behind.joinToString(" "),
        )
        assertEquals(6, Course.workBehind(state))
    }

    @Test
    fun `only the last round of the last set is the last round`() {
        val config = IntervalConfig(rounds = 3, sets = 2)
        val works = generateSequence(WorkoutEngine.start(config).state) { WorkoutEngine.advance(it).state }
            .takeWhile { !it.isDone }
            .filter { it.phase == Phase.Work }
            .toList()
        assertEquals(listOf(false, false, false, false, false, true), works.map { it.isLastRound })
    }

    @Test
    fun `a sequence runs its blocks one after another with the rest between them`() {
        val config = IntervalConfig(
            prepSeconds = 0,
            workSeconds = 20,
            restSeconds = 10,
            rounds = 2,
            then = listOf(
                Block(
                    workSeconds = 30,
                    restSeconds = 15,
                    rounds = 2,
                    sets = 2,
                    setRestSeconds = 45,
                    workStepSeconds = 10,
                ),
                Block(workSeconds = 60, restSeconds = 0, rounds = 1),
            ),
            blockRestSeconds = 90,
        )
        assertEquals(
            "Work 0.1.1:20 Rest 0.1.1:10 Work 0.1.2:20 SetRest 0.1.2:90 " +
                "Work 1.1.1:30 Rest 1.1.1:15 Work 1.1.2:40 SetRest 1.1.2:45 " +
                "Work 1.2.1:30 Rest 1.2.1:15 Work 1.2.2:40 SetRest 1.2.2:90 " +
                "Work 2.1.1:60",
            Course.of(config).joinToString(" ") { "${it.phase} ${it.block}.${it.set}.${it.round}:${it.seconds}" },
        )
        val rests = Course.of(config).filter { it.phase == Phase.SetRest }.map { config.isBlockRest(it) }
        assertEquals(listOf(true, false, true), rests)
    }

    @Test
    fun `blocks with no rest between them are joined by the block's own rest`() {
        val config = IntervalConfig(
            prepSeconds = 0,
            workSeconds = 20,
            restSeconds = 10,
            rounds = 1,
            then = listOf(Block(workSeconds = 40, restSeconds = 0, rounds = 1)),
        )
        assertEquals("Work 1:20 Rest 1:10 Work 1:40", pinned(config))
        val noRest = config.copy(restSeconds = 0)
        assertEquals("Work 1:20 Work 1:40", pinned(noRest))
    }
}
