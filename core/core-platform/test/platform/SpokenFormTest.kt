package dev.justinsmith.interval.platform

import kotlin.test.Test
import kotlin.test.assertEquals

class SpokenFormTest {
    @Test
    fun `rep alone is spelled out so the voice does not say representative`() {
        assertEquals("Stop a repetition early", spokenForm("Stop a rep early"))
        assertEquals("Repetition by repetition", spokenForm("Rep by rep"))
        assertEquals("make one repetition", spokenForm("make one rep"))
    }

    @Test
    fun `reps and words with rep in them stay as written`() {
        assertEquals("5 reps. Prepare the bell and repeat", spokenForm("5 reps. Prepare the bell and repeat"))
    }
}
