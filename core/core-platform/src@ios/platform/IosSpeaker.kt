@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.interval.platform

import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechUtterance
import platform.AVFAudio.setActive

/**
 * iOS's speech synthesizer. The audio session is Ambient, as the interval side's cue player makes it too: lines
 * mix over the user's music rather than stopping it, and follow the ringer switch as the system-sound beeps do.
 * Set here as well, since Lift may speak before any interval workout has played a cue.
 */
class IosSpeaker : Speaker {
    private val synthesizer = AVSpeechSynthesizer()

    init {
        AVAudioSession.sharedInstance().run {
            setCategory(AVAudioSessionCategoryAmbient, error = null)
            setActive(true, error = null)
        }
    }

    override fun speak(text: String) {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
        synthesizer.speakUtterance(AVSpeechUtterance(string = text))
    }

    override fun release() {
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }
}
