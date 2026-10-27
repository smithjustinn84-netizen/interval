package dev.justinsmith.bells.platform

import dev.justinsmith.bells.domain.EmomCue

/**
 * Plays an EMOM's cues as short tones, mixed over the user's music. Implementations swallow platform
 * failures: a missing beep must never stop a session. Haptics go through Compose's `LocalHapticFeedback`.
 */
fun interface Beeper {
    fun beep(cue: EmomCue)

    /** Lets go of what the platform holds for the tones. The container calls it on close; most hold nothing. */
    fun release() = Unit
}
