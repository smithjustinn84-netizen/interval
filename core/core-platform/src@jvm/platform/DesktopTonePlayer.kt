package dev.justinsmith.interval.platform

import co.touchlab.kermit.Logger
import java.io.ByteArrayInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip

/**
 * [ToneSynth]'s tones through a Java Sound [Clip] each, opened from the tone as a WAV in memory the first time it
 * plays and kept. A machine with no sound device logs the failure and the tone goes unheard.
 */
class DesktopTonePlayer : TonePlayer {
    private val log = Logger.withTag("DesktopTonePlayer")
    private val clips = mutableMapOf<Tone, Clip>()

    override fun play(tone: Tone) {
        runCatching {
            val clip = clips.getOrPut(tone) {
                AudioSystem.getClip().apply {
                    open(AudioSystem.getAudioInputStream(ByteArrayInputStream(ToneSynth.wav(tone))))
                }
            }
            // A clip that has played to its end stays there: back to the start before it plays again.
            clip.stop()
            clip.framePosition = 0
            clip.start()
        }.onFailure { log.w(it) { "Tone failed" } }
    }

    override fun release() {
        clips.values.forEach { runCatching { it.close() } }
        clips.clear()
    }
}
