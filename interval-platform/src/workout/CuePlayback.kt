package dev.justinsmith.interval.workout

import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.platform.Speech
import dev.justinsmith.interval.timer.Cue
import dev.justinsmith.interval.timer.Phase

/** Turns an engine [Cue] into the sounds, speech and vibration the user has switched on. */
fun playCue(cue: Cue, settings: CueSettings, player: CuePlayer) {
    with(SwitchedPlayer(settings, player)) {
        when (cue) {
            is Cue.Countdown -> {
                beep(Beep.Countdown)
                say(Speech.Countdown(cue.secondsLeft))
            }

            Cue.Halfway -> if (settings.halfway) {
                if (settings.sound) beep(Beep.Halfway) else say(Speech.Halfway)
            }

            is Cue.PhaseStarted -> when (cue.phase) {
                Phase.Prep -> {
                    beep(Beep.Start)
                    say(Speech.GetReady)
                }

                Phase.Work -> {
                    beep(Beep.Work)
                    buzz(Haptic.Work)
                    say(Speech.Work(cue.lastRound))
                }

                Phase.Rest -> {
                    beep(Beep.Rest)
                    buzz(Haptic.Rest)
                    say(Speech.Rest)
                }

                Phase.Done -> Unit
            }

            Cue.Finished -> {
                beep(Beep.Finished)
                buzz(Haptic.Finished)
                say(Speech.Finished)
            }
        }
    }
}

/** A [CuePlayer] behind the user's switches: each output does nothing while it is turned off. */
private class SwitchedPlayer(private val settings: CueSettings, private val player: CuePlayer) {
    fun beep(beep: Beep) {
        if (settings.sound) player.beep(beep)
    }

    fun say(speech: Speech) {
        if (settings.voice) player.speak(speech)
    }

    fun buzz(haptic: Haptic) {
        if (settings.vibrate) player.vibrate(haptic)
    }
}
