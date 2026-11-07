package dev.justinsmith.lift.platform

import dev.justinsmith.interval.platform.Tone
import dev.justinsmith.interval.platform.TonePlayer
import dev.justinsmith.interval.platform.ToneSynth
import dev.justinsmith.lift.domain.EmomCue

/**
 * Plays the timers' cues (an EMOM's, a rest's, a hold's) as short tones, mixed over the user's music: [ToneBeeper]
 * in the app, a recording one in tests. A missing beep must never stop a session. Haptics go through Compose's
 * `LocalHapticFeedback`.
 */
fun interface Beeper {
    fun beep(cue: EmomCue)

    /** Lets go of what the platform holds for the tones. The container calls it on close; most hold nothing. */
    fun release() = Unit
}

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
