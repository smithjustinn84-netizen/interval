package dev.justinsmith.interval.today

import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// The built-ins from a Timer with ten seconds to get ready: Tabata 4:00, Hangboard 5:20, HIIT 30/30 9:40,
// Long efforts 11:55 and 15:15 MVO2 19:55.
private val onTimer = IntervalConfig()

private fun List<QuickPick>.names() = map { it.preset.name }

class QuickPicksTest {
    @Test
    fun `five minutes offers the presets that long or shorter with the longest first`() {
        val picks = builtInPresets.fitting(5, onTimer)
        assertEquals(listOf("Hangboard", "Tabata"), picks.names())
        assertEquals(listOf(320, 240), picks.map { it.totalSeconds })
    }

    @Test
    fun `at most three are offered and they are the longest that fit`() {
        assertEquals(listOf("HIIT 30/30", "Hangboard", "Tabata"), builtInPresets.fitting(10, onTimer).names())
        assertEquals(listOf("15:15 MVO2", "Long efforts", "HIIT 30/30"), builtInPresets.fitting(20, onTimer).names())
        assertEquals(listOf("15:15 MVO2", "Long efforts", "HIIT 30/30"), builtInPresets.fitting(30, onTimer).names())
    }

    @Test
    fun `a preset is measured as it would run from the timer`() {
        // A minute to get ready makes the hangboard six minutes ten.
        assertEquals(listOf("Tabata"), builtInPresets.fitting(5, onTimer.copy(prepSeconds = 60)).names())
    }

    @Test
    fun `of two the same length the user's own comes first`() {
        val tabata = builtInPresets.first { it.name == "Tabata" }
        val mine = Preset("Mine", tabata.config, id = 7)
        assertEquals(listOf("Mine", "Tabata"), listOf(tabata, mine).fitting(4, onTimer).names())
    }

    @Test
    fun `nothing fits a length shorter than every preset`() {
        assertTrue(builtInPresets.fitting(3, onTimer).isEmpty())
    }
}
