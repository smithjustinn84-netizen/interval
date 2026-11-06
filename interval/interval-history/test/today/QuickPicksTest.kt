package dev.justinsmith.interval.today

import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInFlows
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.estimatedMinutes
import dev.justinsmith.lift.domain.program
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// The built-ins from a Timer with ten seconds to get ready: Tabata 4:00, Hangboard 5:20, HIIT 30/30 9:40,
// Long efforts 11:55 and 15:15 MVO2 19:55.
private val onTimer = IntervalConfig()

private val noFlows = emptyList<Flow>()

private fun List<QuickPick>.names() = map { it.preset.name }

class QuickPicksTest {
    @Test
    fun `five minutes offers the presets that long or shorter with the longest first`() {
        val picks = builtInPresets.fitting(5, onTimer, noFlows)
        assertEquals(listOf("Hangboard", "Tabata"), picks.names())
        assertEquals(listOf(320, 240), picks.map { it.totalSeconds })
    }

    @Test
    fun `at most three are offered and they are the longest that fit`() {
        assertEquals(listOf("HIIT 30/30", "Hangboard", "Tabata"), builtInPresets.fitting(10, onTimer, noFlows).names())
        assertEquals(
            listOf("15:15 MVO2", "Long efforts", "HIIT 30/30"),
            builtInPresets.fitting(20, onTimer, noFlows).names(),
        )
        assertEquals(
            listOf("15:15 MVO2", "Long efforts", "HIIT 30/30"),
            builtInPresets.fitting(30, onTimer, noFlows).names(),
        )
    }

    @Test
    fun `a preset is measured as it would run from the timer`() {
        // A minute to get ready makes the hangboard six minutes ten.
        assertEquals(listOf("Tabata"), builtInPresets.fitting(5, onTimer.copy(prepSeconds = 60), noFlows).names())
    }

    @Test
    fun `of two the same length the user's own comes first`() {
        val tabata = builtInPresets.first { it.name == "Tabata" }
        val mine = Preset("Mine", tabata.config, id = 7)
        assertEquals(listOf("Mine", "Tabata"), listOf(tabata, mine).fitting(4, onTimer, noFlows).names())
    }

    @Test
    fun `nothing fits a length shorter than every preset`() {
        assertTrue(builtInPresets.fitting(3, onTimer).isEmpty())
    }

    @Test
    fun `the longest flow that fits comes after the presets`() {
        val five = builtInPresets.fitting(5, onTimer)
        assertEquals(listOf("Hangboard", "Tabata", "Wrists and fingers"), five.names())
        assertEquals(listOf(false, false, true), five.map { it.mobility })
        assertEquals(300, five.last().totalSeconds)
        assertEquals("Spine and hamstrings", builtInPresets.fitting(10, onTimer).names().last())
        assertEquals("Core on the mat", builtInPresets.fitting(20, onTimer).names().last())
        assertEquals("Full reset", builtInPresets.fitting(30, onTimer).names().last())
        // With no preset that fits, a flow that does is still offered.
        assertEquals(listOf("Wrists and fingers"), emptyList<Preset>().fitting(5, onTimer).names())
    }

    @Test
    fun `a flow is measured after the timer's get-ready and keeps its own shape`() {
        // A minute to get ready makes the wrists 5:50, five whole minutes still; two make them 6:50.
        val picks = emptyList<Preset>().fitting(5, onTimer.copy(prepSeconds = 60))
        assertEquals(350, picks.single().totalSeconds)
        assertEquals(builtInFlows.first { it.name == "Wrists and fingers" }.config, picks.single().preset.config)
        assertTrue(emptyList<Preset>().fitting(5, onTimer.copy(prepSeconds = 120)).isEmpty())
    }

    @Test
    fun `the program's day is the strength pick when it fits the minutes`() {
        val rest = RestClock.DEFAULT_SECONDS
        val day = program(ProgramId.FifteenMinuteKettlebell).day(1)
        val minutes = day.estimatedMinutes(rest)

        assertEquals(StrengthPick(day, minutes), strengthPick(day, rest, minutes, inProgress = false))
        assertNull(strengthPick(day, rest, minutes - 1, inProgress = false))
        // A session under way is Up next's to resume, and with no program there is none.
        assertNull(strengthPick(day, rest, minutes, inProgress = true))
        assertNull(strengthPick(null, rest, 30, inProgress = false))
    }

    @Test
    fun `beside the program's day a preset fewer is offered`() {
        assertEquals(
            listOf("15:15 MVO2", "Long efforts"),
            builtInPresets.fitting(20, onTimer, noFlows, shown = 2).names(),
        )
    }
}
