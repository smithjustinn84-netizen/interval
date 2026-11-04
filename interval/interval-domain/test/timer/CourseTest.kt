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
}
