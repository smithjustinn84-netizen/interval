package dev.justinsmith.interval.platform

import kotlin.math.PI
import kotlin.math.sin

/** One note of a [Tone]: a sine at [hertz] for [millis], risen from silence and let back down to it. */
data class Note(val hertz: Double, val millis: Int)

/** A sound's notes, one after the other with no gap: each ends in silence, so none clicks into the next. */
data class Tone(val notes: List<Note>) {
    constructor(vararg notes: Note) : this(notes.toList())

    val millis: Int get() = notes.sumOf { it.millis }
}

/**
 * The app's six sounds, drawn rather than borrowed from the platform, so a workout sounds the same on a phone, an
 * iPhone and a desktop: plain sines, each note risen over [ATTACK_MILLIS] and let down over [RELEASE_MILLIS], none
 * longer than [MAX_MILLIS]. A countdown is a short high tick; halfway one mid note; Start (get ready, and the
 * warm-up) one low note; Work two notes rising; Rest two falling, the lower one held; Finished three rising to the
 * octave. The players take [samples] as 16-bit PCM ([pcm16]) or as a WAV file in memory ([wav]), mono at
 * [SAMPLE_RATE].
 */
object ToneSynth {
    const val SAMPLE_RATE = 44_100
    const val BYTES_PER_SAMPLE = 2
    const val MAX_MILLIS = 400

    /** A note's rise from silence and its fall back: enough that neither end clicks. */
    const val ATTACK_MILLIS = 8
    const val RELEASE_MILLIS = 40

    /** The loudest a sample gets: headroom under full scale for the platform's mixer. */
    const val PEAK = 0.8f

    private const val C5 = 523.25
    private const val E5 = 659.25
    private const val G5 = 783.99
    private const val C6 = 1046.5

    private val countdown = Tone(Note(C6, millis = 60))
    private val halfway = Tone(Note(E5, millis = 140))
    private val start = Tone(Note(C5, millis = 200))
    private val work = Tone(Note(C5, millis = 110), Note(G5, millis = 130))
    private val rest = Tone(Note(G5, millis = 120), Note(C5, millis = 180))
    private val finished = Tone(Note(E5, millis = 100), Note(G5, millis = 100), Note(C6, millis = 190))

    fun tone(beep: Beep): Tone = when (beep) {
        Beep.Countdown -> countdown
        Beep.Halfway -> halfway
        Beep.Start -> start
        Beep.Work -> work
        Beep.Rest -> rest
        Beep.Finished -> finished
    }

    /** [beep]'s samples, -1 to 1, silent at both ends. */
    fun samples(beep: Beep): FloatArray = samples(tone(beep))

    fun samples(tone: Tone): FloatArray {
        val out = FloatArray(samplesIn(tone.millis))
        var at = 0
        for (note in tone.notes) {
            val length = samplesIn(note.millis)
            val attack = samplesIn(ATTACK_MILLIS).coerceAtMost(length / 2)
            val release = samplesIn(RELEASE_MILLIS).coerceAtMost(length / 2)
            for (i in 0 until length) {
                val rise = if (i < attack) i.toFloat() / attack else 1f
                val fall = if (i >= length - release) (length - 1 - i).toFloat() / release else 1f
                val envelope = minOf(rise, fall)
                out[at + i] = PEAK * envelope * sin(2 * PI * note.hertz * i / SAMPLE_RATE).toFloat()
            }
            at += length
        }
        return out
    }

    /** [beep]'s samples as signed 16-bit PCM, for Android's `AudioTrack`. */
    fun pcm16(beep: Beep): ShortArray {
        val samples = samples(beep)
        return ShortArray(samples.size) { (samples[it] * Short.MAX_VALUE).toInt().toShort() }
    }

    /** [beep] as a WAV file in memory: [WAV_HEADER_BYTES] of header, then [pcm16] little-endian. */
    fun wav(beep: Beep): ByteArray {
        val pcm = pcm16(beep)
        val data = pcm.size * BYTES_PER_SAMPLE
        val out = ByteArray(WAV_HEADER_BYTES + data)
        var at = 0
        fun ascii(text: String) = text.forEach { out[at++] = it.code.toByte() }
        fun int(value: Int, bytes: Int) = repeat(bytes) { out[at++] = (value ushr (BITS_PER_BYTE * it)).toByte() }
        ascii("RIFF")
        int(WAV_HEADER_BYTES - RIFF_HEADER_BYTES + data, INT_BYTES)
        ascii("WAVE")
        ascii("fmt ")
        int(FORMAT_CHUNK_BYTES, INT_BYTES)
        int(PCM_FORMAT, SHORT_BYTES)
        int(CHANNELS, SHORT_BYTES)
        int(SAMPLE_RATE, INT_BYTES)
        int(SAMPLE_RATE * BYTES_PER_SAMPLE * CHANNELS, INT_BYTES)
        int(BYTES_PER_SAMPLE * CHANNELS, SHORT_BYTES)
        int(BYTES_PER_SAMPLE * BITS_PER_BYTE, SHORT_BYTES)
        ascii("data")
        int(data, INT_BYTES)
        pcm.forEach { int(it.toInt(), SHORT_BYTES) }
        return out
    }

    private fun samplesIn(millis: Int): Int = millis * SAMPLE_RATE / MILLIS_PER_SECOND

    const val WAV_HEADER_BYTES = 44
    private const val RIFF_HEADER_BYTES = 8
    private const val FORMAT_CHUNK_BYTES = 16
    private const val PCM_FORMAT = 1
    private const val CHANNELS = 1
    private const val INT_BYTES = 4
    private const val SHORT_BYTES = 2
    private const val BITS_PER_BYTE = 8
    private const val MILLIS_PER_SECOND = 1000
}
