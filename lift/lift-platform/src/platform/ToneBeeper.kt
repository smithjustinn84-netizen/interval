package dev.justinsmith.lift.platform

import dev.justinsmith.interval.platform.Tone
import dev.justinsmith.interval.platform.TonePlayer
import dev.justinsmith.interval.platform.ToneSynth
import dev.justinsmith.lift.domain.EmomCue

/**
 * The timers' cues as core's [ToneSynth] tones, through the process's [TonePlayer], so a rest's end sounds as the
 * interval workout's work does on every platform: a tick for each of the last three seconds, the rising "go" as a
 * minute or the next set starts, and the three-note resolve as an EMOM ends.
 */
class ToneBeeper(private val tones: TonePlayer) : Beeper {
    override fun beep(cue: EmomCue) {
        tones.play(cue.tone)
    }
}

/** Which of core's tones each of the timers' cues is. */
val EmomCue.tone: Tone
    get() = when (this) {
        EmomCue.Countdown -> ToneSynth.countdown
        EmomCue.Start -> ToneSynth.work
        EmomCue.Finished -> ToneSynth.finished
    }
