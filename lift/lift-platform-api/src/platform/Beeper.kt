package dev.justinsmith.lift.platform

import dev.justinsmith.lift.domain.EmomCue

/**
 * Plays the timers' cues (an EMOM's, a rest's, a hold's) as short tones, mixed over the user's music: `ToneBeeper`
 * in the app, a recording one in tests. A missing beep must never stop a session. Haptics go through Compose's
 * `LocalHapticFeedback`.
 */
fun interface Beeper {
    fun beep(cue: EmomCue)

    /** Lets go of what the platform holds for the tones. The container calls it on close; most hold nothing. */
    fun release() = Unit
}
