package dev.justinsmith.interval.platform

/** Which of core's [ToneSynth] tones each of the workout's beeps is. */
val Beep.tone: Tone
    get() = when (this) {
        Beep.Countdown -> ToneSynth.countdown
        Beep.Halfway -> ToneSynth.halfway
        Beep.Start -> ToneSynth.start
        Beep.Work -> ToneSynth.work
        Beep.Rest -> ToneSynth.rest
        Beep.Finished -> ToneSynth.finished
    }
