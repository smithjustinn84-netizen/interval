package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals

/** What each built-in preset is for, which Train's Conditioning groups them by. */
class PresetPurposeTest {
    @Test
    fun `the built-ins fall into anywhere a bell and the mat or the wall`() {
        val byPurpose = builtInPresets.groupBy({ it.purpose }, { it.name })
        assertEquals(listOf("Tabata", "HIIT 30/30", "Long efforts", "20-minute cap"), byPurpose[PresetPurpose.Anywhere])
        assertEquals(listOf("15:15 MVO2", "Swings on the minute"), byPurpose[PresetPurpose.Bell])
        assertEquals(listOf("Hangboard", "Mat rounds"), byPurpose[PresetPurpose.MatAndWall])
    }

    @Test
    fun `a preset of the user's own goes by what it is done with`() {
        assertEquals(PresetPurpose.Bell, Preset("Mine", IntervalConfig(activity = Activity.Kettlebell)).purpose)
        assertEquals(PresetPurpose.Anywhere, Preset("Mine", IntervalConfig(activity = Activity.Rower)).purpose)
    }
}
