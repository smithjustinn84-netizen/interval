package dev.justinsmith.interval.workout

import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.timer.Cue
import dev.justinsmith.interval.timer.Phase

/** Turns an engine [Cue] into the sounds, speech and vibration the user has switched on. */
fun playCue(cue: Cue, settings: CueSettings, player: CuePlayer) {
    with(SwitchedPlayer(settings, player)) {
        when (cue) {
            is Cue.Countdown -> {
                beep(Beep.Countdown)
                say(cue.secondsLeft.toString())
            }

            Cue.Halfway -> if (settings.halfway) {
                if (settings.sound) beep(Beep.Halfway) else say("Halfway")
            }

            is Cue.PhaseStarted -> when (cue.phase) {
                Phase.Prep -> {
                    beep(Beep.Start)
                    say("Get ready")
                }

                Phase.Work -> {
                    beep(Beep.Work)
                    buzz(Haptic.Work)
                    say(if (cue.lastRound) "Last round. Work" else "Work")
                }

                Phase.Rest -> {
                    beep(Beep.Rest)
                    buzz(Haptic.Rest)
                    say("Rest")
                }

                Phase.Done -> Unit
            }

            Cue.Finished -> {
                beep(Beep.Finished)
                buzz(Haptic.Finished)
                say("Done. Nice work")
            }
        }
    }
}

/** A [CuePlayer] behind the user's switches: each output does nothing while it is turned off. */
private class SwitchedPlayer(private val settings: CueSettings, private val player: CuePlayer) {
    fun beep(beep: Beep) {
        if (settings.sound) player.beep(beep)
    }

    fun say(text: String) {
        if (settings.voice) player.speak(text)
    }

    fun buzz(haptic: Haptic) {
        if (settings.vibrate) player.vibrate(haptic)
    }
}
