package dev.justinsmith.interval.platform

import dev.justinsmith.interval.fakes.RecordingCuePlayer
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.timer.Cue
import dev.justinsmith.interval.timer.Phase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CuePlaybackTest {
    private val cues = listOf(
        Cue.Countdown(2),
        Cue.Halfway,
        Cue.PhaseStarted(Phase.Prep, lastRound = false),
        Cue.PhaseStarted(Phase.Work, lastRound = true),
        Cue.PhaseStarted(Phase.Rest, lastRound = false),
        Cue.Finished,
    )

    /** Every combination of the four switches. */
    private val everySetting = (0 until 16).map { bits ->
        fun on(bit: Int) = (bits and bit) != 0
        CueSettings(sound = on(1), voice = on(2), halfway = on(4), vibrate = on(8))
    }

    private fun played(cue: Cue, settings: CueSettings) = RecordingCuePlayer().also { playCue(cue, settings, it) }

    @Test
    fun `no output plays while its switch is off`() {
        for (settings in everySetting) {
            for (cue in cues) {
                val player = played(cue, settings)
                if (!settings.sound) assertTrue(player.beeps.isEmpty(), "$cue beeped with sound off")
                if (!settings.voice) assertTrue(player.spoken.isEmpty(), "$cue spoke with voice off")
                if (!settings.vibrate) assertTrue(player.haptics.isEmpty(), "$cue vibrated with vibration off")
            }
        }
    }

    @Test
    fun `with everything on each cue plays its beep and line and the phases their buzz`() {
        val on = CueSettings()
        with(played(Cue.Countdown(2), on)) {
            assertEquals(listOf(Beep.Countdown), beeps)
            assertEquals(listOf<Speech>(Speech.Countdown(2)), spoken)
            assertTrue(haptics.isEmpty())
        }
        with(played(Cue.PhaseStarted(Phase.Work, lastRound = true), on)) {
            assertEquals(listOf(Beep.Work), beeps)
            assertEquals(listOf<Speech>(Speech.Work(lastRound = true)), spoken)
            assertEquals(listOf(Haptic.Work), haptics)
        }
        with(played(Cue.PhaseStarted(Phase.Prep, lastRound = false), on)) {
            assertEquals(listOf(Beep.Start), beeps)
            assertEquals(listOf<Speech>(Speech.GetReady), spoken)
            assertTrue(haptics.isEmpty())
        }
        with(played(Cue.Finished, on)) {
            assertEquals(listOf(Beep.Finished), beeps)
            assertEquals(listOf<Speech>(Speech.Finished), spoken)
            assertEquals(listOf(Haptic.Finished), haptics)
        }
    }

    @Test
    fun `halfway beeps when sound is on and is spoken only without sound`() {
        with(played(Cue.Halfway, CueSettings())) {
            assertEquals(listOf(Beep.Halfway), beeps)
            assertTrue(spoken.isEmpty())
        }
        with(played(Cue.Halfway, CueSettings(sound = false))) {
            assertTrue(beeps.isEmpty())
            assertEquals(listOf<Speech>(Speech.Halfway), spoken)
        }
    }

    @Test
    fun `halfway is silent when its own switch is off`() {
        val player = played(Cue.Halfway, CueSettings(halfway = false))
        assertTrue(player.beeps.isEmpty() && player.spoken.isEmpty() && player.haptics.isEmpty())
    }
}
