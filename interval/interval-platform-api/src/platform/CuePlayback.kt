package dev.justinsmith.interval.platform

import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.timer.Cue
import dev.justinsmith.interval.timer.FlowStep
import dev.justinsmith.interval.timer.Phase

/**
 * Turns an engine [Cue] into the sounds, speech and vibration the user has switched on. In a flow, [step] is the
 * move the cue lands in, or the one coming: its phases are said in the flow's words, and a hold has no halfway.
 * [switchHands] when a work starting now is worked with the other hand from the last ([Speech.Work.switchHands]).
 */
fun playCue(cue: Cue, settings: CueSettings, player: CuePlayer, step: FlowStep? = null, switchHands: Boolean = false) {
    val switched = SwitchedPlayer(settings, player)
    when {
        step == null -> switched.playWorkoutCue(cue, switchHands)
        cue is Cue.PhaseStarted -> switched.playFlowCue(cue.phase, step)
        cue != Cue.Halfway -> switched.playWorkoutCue(cue)
    }
}

/** A cue of any workout but a flow's change of phase, in the phases' own words. */
private fun SwitchedPlayer.playWorkoutCue(cue: Cue, switchHands: Boolean = false) {
    when (cue) {
        is Cue.Countdown -> {
            beep(Beep.Countdown)
            say(Speech.Countdown(cue.secondsLeft))
        }

        Cue.Halfway -> if (settings.halfway) {
            if (settings.sound) beep(Beep.Halfway) else say(Speech.Halfway)
        }

        is Cue.PhaseStarted -> playPhaseCue(cue, switchHands)

        Cue.Finished -> {
            beep(Beep.Finished)
            buzz(Haptic.Finished)
            say(Speech.Finished)
        }
    }
}

private fun SwitchedPlayer.playPhaseCue(cue: Cue.PhaseStarted, switchHands: Boolean) {
    when (cue.phase) {
        Phase.Prep -> {
            beep(Beep.Start)
            say(Speech.GetReady)
        }

        Phase.Work -> {
            beep(Beep.Work)
            buzz(Haptic.Work)
            say(Speech.Work(cue.lastRound, cue.set, cue.block, switchHands))
        }

        Phase.Rest -> {
            beep(Beep.Rest)
            buzz(Haptic.Rest)
            say(Speech.Rest)
        }

        Phase.SetRest -> {
            beep(Beep.Rest)
            buzz(Haptic.Rest)
            say(cue.block?.let(Speech::BlockRest) ?: Speech.SetRest)
        }

        // Easier stretches, cued as the moves into them: the warm-up like get ready, the cool-down like rest.
        Phase.WarmUp -> {
            beep(Beep.Start)
            say(Speech.WarmUp)
        }

        Phase.CoolDown -> {
            beep(Beep.Rest)
            buzz(Haptic.Rest)
            say(Speech.CoolDown)
        }

        Phase.Done -> Unit
    }
}

/**
 * A flow's change of [phase], with the beeps and buzzes of any workout's: the move coming is named as its
 * change-over starts, the side (or "Begin") as it does, and "Switch sides" between the two.
 */
private fun SwitchedPlayer.playFlowCue(phase: Phase, step: FlowStep) {
    when (phase) {
        Phase.Work -> {
            beep(Beep.Work)
            buzz(Haptic.Work)
            say(step.side?.let(Speech::Side) ?: Speech.Begin)
        }

        Phase.Rest -> {
            beep(Beep.Rest)
            buzz(Haptic.Rest)
            say(Speech.SwitchSides)
        }

        Phase.Prep, Phase.WarmUp -> {
            beep(Beep.Start)
            say(Speech.NextMove(step.move.name))
        }

        Phase.SetRest, Phase.CoolDown -> {
            beep(Beep.Rest)
            buzz(Haptic.Rest)
            say(Speech.NextMove(step.move.name))
        }

        Phase.Done -> Unit
    }
}

/** A [CuePlayer] behind the user's switches: each output does nothing while it is turned off. */
private class SwitchedPlayer(val settings: CueSettings, private val player: CuePlayer) {
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
