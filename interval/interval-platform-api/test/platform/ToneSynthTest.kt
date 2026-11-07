package dev.justinsmith.interval.platform

import kotlin.math.PI
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToneSynthTest {
    private val beeps = Beep.entries

    private fun Int.samples() = this * ToneSynth.SAMPLE_RATE / 1000

    @Test
    fun `every tone is under 400 ms and starts and ends in silence`() {
        for (beep in beeps) {
            val tone = ToneSynth.tone(beep)
            val samples = ToneSynth.samples(beep)
            assertTrue(tone.millis <= ToneSynth.MAX_MILLIS, "$beep is ${tone.millis} ms")
            assertEquals(tone.millis.samples(), samples.size, "$beep's length")
            assertEquals(0f, abs(samples.first()), "$beep's first sample")
            assertEquals(0f, abs(samples.last()), "$beep's last sample")
        }
    }

    @Test
    fun `every tone peaks under full scale and loud enough to hear`() {
        for (beep in beeps) {
            val peak = ToneSynth.samples(beep).maxOf { abs(it) }
            assertTrue(peak < 1f, "$beep peaks at $peak")
            assertTrue(peak > 0.7f, "$beep peaks at $peak")
        }
    }

    @Test
    fun `no sample jumps far enough to click`() {
        // The steepest a sine at the highest note can be at the loudest: anything beyond is a step in the envelope.
        val steepest = 2 * PI * 1046.5 / ToneSynth.SAMPLE_RATE * ToneSynth.PEAK * 1.05
        for (beep in beeps) {
            val samples = ToneSynth.samples(beep)
            val jump = (1 until samples.size).maxOf { abs(samples[it] - samples[it - 1]) }
            assertTrue(jump < steepest, "$beep jumps by $jump")
        }
    }

    @Test
    fun `each note is silent at its own ends so the notes meet without a click`() {
        for (beep in beeps) {
            val samples = ToneSynth.samples(beep)
            var at = 0
            for (note in ToneSynth.tone(beep).notes) {
                assertEquals(0f, abs(samples[at]), "$beep's note at $at starts silent")
                at += note.millis.samples()
                assertEquals(0f, abs(samples[at - 1]), "$beep's note before $at ends silent")
            }
            assertEquals(samples.size, at)
        }
    }

    @Test
    fun `work rises and rest falls and finished resolves up through three notes`() {
        val start = ToneSynth.tone(Beep.Start).notes.single().hertz
        val work = ToneSynth.tone(Beep.Work).notes.map { it.hertz }
        val rest = ToneSynth.tone(Beep.Rest).notes.map { it.hertz }
        val finished = ToneSynth.tone(Beep.Finished).notes.map { it.hertz }
        assertEquals(2, work.size)
        assertTrue(work[0] < work[1], "work rises: $work")
        assertEquals(2, rest.size)
        assertTrue(rest[0] > rest[1], "rest falls: $rest")
        assertEquals(3, finished.size)
        assertEquals(finished.sorted(), finished, "finished rises: $finished")
        assertEquals(start * 2, finished.last(), 1.0, "finished resolves to the octave of Start's note")
    }

    @Test
    fun `the countdown is the shortest tone and a single tick`() {
        val countdown = ToneSynth.tone(Beep.Countdown)
        assertEquals(1, countdown.notes.size)
        for (beep in beeps - Beep.Countdown) {
            assertTrue(countdown.millis < ToneSynth.tone(beep).millis, "$beep is shorter than the countdown")
        }
    }

    @Test
    fun `the pcm carries every sample scaled to 16 bits`() {
        for (beep in beeps) {
            val samples = ToneSynth.samples(beep)
            val pcm = ToneSynth.pcm16(beep)
            assertEquals(samples.size, pcm.size)
            val loudest = samples.indices.maxBy { abs(samples[it]) }
            assertEquals((samples[loudest] * Short.MAX_VALUE).toInt(), pcm[loudest].toInt())
        }
    }

    @Test
    fun `a wav wraps the pcm in a 44-byte header that says mono 16-bit at the sample rate`() {
        val wav = ToneSynth.wav(Beep.Work)
        val pcm = ToneSynth.pcm16(Beep.Work)
        fun ascii(at: Int, length: Int) = wav.copyOfRange(at, at + length).decodeToString()
        fun int(at: Int, bytes: Int) = (0 until bytes).sumOf { (wav[at + it].toInt() and 0xFF) shl (8 * it) }
        assertEquals(ToneSynth.WAV_HEADER_BYTES + pcm.size * 2, wav.size)
        assertEquals("RIFF", ascii(0, 4))
        assertEquals(wav.size - 8, int(4, 4))
        assertEquals("WAVE", ascii(8, 4))
        assertEquals("fmt ", ascii(12, 4))
        assertEquals(16, int(16, 4))
        assertEquals(1, int(20, 2), "PCM")
        assertEquals(1, int(22, 2), "mono")
        assertEquals(ToneSynth.SAMPLE_RATE, int(24, 4))
        assertEquals(ToneSynth.SAMPLE_RATE * 2, int(28, 4), "byte rate")
        assertEquals(2, int(32, 2), "block align")
        assertEquals(16, int(34, 2), "bits per sample")
        assertEquals("data", ascii(36, 4))
        assertEquals(pcm.size * 2, int(40, 4))
        val loudest = pcm.indices.maxBy { abs(pcm[it].toInt()) }
        assertEquals(pcm[loudest].toInt(), int(44 + loudest * 2, 2).toShort().toInt(), "a sample little-endian")
    }
}
