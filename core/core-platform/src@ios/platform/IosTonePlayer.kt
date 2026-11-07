@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package dev.justinsmith.interval.platform

import co.touchlab.kermit.Logger
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create

/**
 * [ToneSynth]'s tones through an [AVAudioPlayer] each, built from the tone as a WAV in memory the first time it
 * plays and kept. The audio session is Ambient, set here as the speaker sets it: tones mix over the user's music
 * rather than stopping it, and follow the ringer switch as the system's own sounds do.
 */
class IosTonePlayer : TonePlayer {
    private val log = Logger.withTag("IosTonePlayer")
    private val players = mutableMapOf<Tone, AVAudioPlayer>()

    init {
        AVAudioSession.sharedInstance().run {
            setCategory(AVAudioSessionCategoryAmbient, error = null)
            setActive(true, error = null)
        }
    }

    override fun play(tone: Tone) {
        val player = players[tone] ?: player(tone)?.also { players[tone] = it } ?: return
        player.setCurrentTime(0.0)
        player.play()
    }

    private fun player(tone: Tone): AVAudioPlayer? {
        val wav = ToneSynth.wav(tone)
        val data = wav.usePinned { NSData.create(bytes = it.addressOf(0), length = wav.size.toULong()) }
        return runCatching { AVAudioPlayer(data = data, error = null).apply { prepareToPlay() } }
            .onFailure { log.w(it) { "No player for a tone; it goes unheard" } }
            .getOrNull()
    }

    override fun release() {
        players.values.forEach { it.stop() }
        players.clear()
    }
}
