package dev.justinsmith.interval.platform

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import co.touchlab.kermit.Logger

/**
 * [ToneSynth]'s tones as static [AudioTrack]s, each written from its samples the first time it plays and kept for the
 * next. On the media stream (`USAGE_MEDIA`), so they follow the media volume and mix over the user's workout music,
 * as sonification. Each track holds one of the few the system has, and native memory the GC never weighs, so they
 * are let go with the container ([release]).
 */
class AndroidTonePlayer : TonePlayer {
    private val log = Logger.withTag("AndroidTonePlayer")
    private val tracks = mutableMapOf<Tone, AudioTrack>()

    override fun play(tone: Tone) {
        runCatching {
            val track = tracks.getOrPut(tone) { build(tone) }
            // A static track plays to its end and stays there: back to the start before it plays again.
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
            track.reloadStaticData()
            track.play()
        }.onFailure { log.w(it) { "Tone failed" } }
    }

    private fun build(tone: Tone): AudioTrack {
        val pcm = ToneSynth.pcm16(tone)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ToneSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * ToneSynth.BYTES_PER_SAMPLE)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
            .also { it.write(pcm, 0, pcm.size) }
    }

    override fun release() {
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
    }
}
