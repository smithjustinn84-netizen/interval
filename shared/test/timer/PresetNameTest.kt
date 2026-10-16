package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals

class PresetNameTest {
    @Test
    fun `a shape names itself by its work and rest and rounds`() {
        assertEquals("40/20 ×6", IntervalConfig(workSeconds = 40, restSeconds = 20, rounds = 6).defaultPresetName())
        assertEquals("45/0 ×1", IntervalConfig(workSeconds = 45, restSeconds = 0, rounds = 1).defaultPresetName())
    }

    @Test
    fun `whole minutes read as minutes`() {
        assertEquals("1m/30 ×5", IntervalConfig(workSeconds = 60, restSeconds = 30, rounds = 5).defaultPresetName())
        assertEquals("2m/1m ×4", IntervalConfig(workSeconds = 120, restSeconds = 60, rounds = 4).defaultPresetName())
        assertEquals("90/90 ×3", IntervalConfig(workSeconds = 90, restSeconds = 90, rounds = 3).defaultPresetName())
    }

    @Test
    fun `the name never passes the limit`() {
        val name = IntervalConfig(workSeconds = 3600, restSeconds = 3600, rounds = 99).defaultPresetName()
        assertEquals("60m/60m ×99", name)
        assertEquals(true, name.length <= Preset.MAX_NAME_LENGTH)
    }

    @Test
    fun `a free name is kept and a taken one is numbered`() {
        assertEquals("Tabata", uniquePresetName("Tabata", listOf("HIIT")))
        assertEquals("Tabata 2", uniquePresetName("Tabata", listOf("Tabata")))
        assertEquals("Tabata 3", uniquePresetName("Tabata", listOf("Tabata", "Tabata 2")))
        assertEquals("Tabata 2", uniquePresetName("Tabata", listOf("Tabata", "Tabata 3")))
    }

    @Test
    fun `a numbered name stays within the limit`() {
        val longest = "x".repeat(Preset.MAX_NAME_LENGTH)
        val numbered = uniquePresetName(longest, listOf(longest))
        assertEquals(Preset.MAX_NAME_LENGTH, numbered.length)
        assertEquals("x".repeat(Preset.MAX_NAME_LENGTH - 2) + " 2", numbered)
    }

    @Test
    fun `a built-in counts as custom only with an id`() {
        assertEquals(false, builtInPresets.first().custom)
        assertEquals(true, builtInPresets.first().copy(id = 4).custom)
    }
}
