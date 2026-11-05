package dev.justinsmith.interval.timer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutDescriptionTest {
    private val current = IntervalConfig(prepSeconds = 25, workSeconds = 30, restSeconds = 15, rounds = 8)

    private fun read(text: String, presets: List<Preset> = builtInPresets): DescribedWorkout? =
        describeWorkout(text, current, presets)

    /** Work, rest and rounds as [text] reads, for the many cases that only check the shape. */
    private fun shape(text: String): Triple<Int, Int, Int>? =
        read(text)?.config?.let { Triple(it.workSeconds, it.restSeconds, it.rounds) }

    @Test
    fun `a preset's name starts from that preset and keeps the get-ready countdown`() {
        val described = read("Tabata")!!
        assertEquals(IntervalConfig(prepSeconds = 25, workSeconds = 20, restSeconds = 10, rounds = 8), described.config)
        assertEquals(ConfigField.shape.toSet(), described.understood)
    }

    @Test
    fun `numbers after a preset's name change it`() {
        assertEquals(Triple(20, 10, 12), shape("tabata x12"))
    }

    @Test
    fun `the user's own preset wins over a built-in of the same name`() {
        val mine = Preset("Tabata", IntervalConfig(workSeconds = 25, restSeconds = 5, rounds = 6), id = 1)
        assertEquals(25, read("tabata", builtInPresets + mine)!!.config.workSeconds)
    }

    @Test
    fun `a name inside another word is not a preset's`() {
        assertNull(read("tabatas"))
    }

    @Test
    fun `work and rest read from a slash`() {
        assertEquals(Triple(40, 20, 6), shape("40/20 x6"))
        assertEquals(Triple(90, 30, 10), shape("1:30/0:30 × 10"))
        assertEquals(Triple(45, 15, 12), shape("45s/15s, 12 rounds"))
    }

    @Test
    fun `work and rest read from on and off`() {
        assertEquals(Triple(40, 20, 10), shape("40 on 20 off x10"))
        assertEquals(Triple(60, 30, 5), shape("1 min on, 30s off, 5 sets"))
        assertEquals(Triple(40, 20, 8), shape("40 seconds of work and 20 seconds of rest"))
    }

    @Test
    fun `work and rest read with the word first`() {
        assertEquals(Triple(90, 30, 4), shape("work 1:30 rest 0:30 4 rounds"))
        assertEquals(Triple(30, 15, 8), shape("30 seconds work and rest 15"))
    }

    @Test
    fun `rounds read in every common form`() {
        assertEquals(12, shape("12 rounds")!!.third)
        assertEquals(12, shape("x12")!!.third)
        assertEquals(12, shape("12x 30s")!!.third)
        assertEquals(12, shape("12 x 30 sec")!!.third)
        assertEquals(5, shape("5 times")!!.third)
    }

    @Test
    fun `durations no word names are the work then the rest`() {
        assertEquals(Triple(30, 15, 10), shape("10 rounds of 30 seconds"))
        assertEquals(Triple(45, 20, 6), shape("6 rounds of 45s and 20s"))
    }

    @Test
    fun `a total length with no rounds gives as many as fit`() {
        // 20 minutes of 30 seconds work and 15 rest: 27 rounds, the last rest left out.
        val described = read("20 min of burpees, short rests")!!
        assertEquals(Triple(30, 15, 27), described.config.let { Triple(it.workSeconds, it.restSeconds, it.rounds) })
        assertEquals(setOf(ConfigField.Rest, ConfigField.Rounds), described.understood)
        assertEquals(Activity.Bodyweight, described.config.activity)
    }

    @Test
    fun `a length on a machine is not work time`() {
        assertEquals(Triple(30, 15, 27), shape("20 minutes on the bike"))
    }

    @Test
    fun `words for the rest fill it only where no number does`() {
        assertEquals(0, shape("no rest")!!.second)
        assertEquals(60, shape("long rests")!!.second)
        assertEquals(20, shape("short rest of 20s")!!.second)
        assertEquals(10, shape("tabata with short rests")!!.second)
    }

    @Test
    fun `values are kept within their ranges`() {
        assertEquals(Triple(ConfigField.Work.range.first, 0, ConfigField.Rounds.range.last), shape("1/0 x500"))
        assertEquals(ConfigField.Rounds.range.last, shape("99999999999 rounds")!!.third)
        assertEquals(ConfigField.Work.range.last, shape("99999999999 seconds on")!!.first)
    }

    @Test
    fun `a distance is no duration`() {
        val described = read("run 400m")!!
        assertEquals(emptySet(), described.understood)
        assertEquals(current.copy(activity = Activity.Running), described.config)
    }

    @Test
    fun `other counts are no durations`() {
        assertEquals(Triple(30, 15, 3), shape("3 sets of 10 reps"))
    }

    @Test
    fun `the activity is the first one named`() {
        assertEquals(Activity.Rower, read("45/15 x12 rower")!!.config.activity)
        assertEquals(Activity.SkiErg, read("ski erg sprints")!!.config.activity)
        assertEquals(Activity.Bodyweight, read("mountain climbers")!!.config.activity)
        assertEquals(Activity.Kettlebell, read("kb swings 30 on 30 off")!!.config.activity)
        assertEquals(Activity.Climbing, read("hangboard 7 on 3 off x6")!!.config.activity)
        assertTrue(read("bike")!!.activityFound)
    }

    @Test
    fun `no activity named keeps the Timer's`() {
        val described = read("40/20")!!
        assertFalse(described.activityFound)
        assertEquals(current.activity, described.config.activity)
    }

    @Test
    fun `words with nothing to read are not understood`() {
        assertNull(read("something fun"))
        assertNull(read(""))
    }

    /** Sets, the rest between them, and the rounds of each, as [text] reads. */
    private fun sets(text: String): Triple<Int, Int, Int>? =
        read(text)?.config?.let { Triple(it.sets, it.setRestSeconds, it.rounds) }

    @Test
    fun `a count of sets of a count of rounds reads as sets of rounds`() {
        assertEquals(Triple(4, 60, 8), sets("4 x (8 x 20/10), 1:00 between"))
        assertEquals(Triple(20, 10, 8), shape("4 x (8 x 20/10), 1:00 between"))
        assertEquals(Triple(4, 60, 8), sets("4 sets of 8 rounds 20/10 with 1 min between sets"))
        assertEquals(Triple(20, 10, 8), shape("4 sets of 8 rounds 20/10 with 1 min between sets"))
        assertEquals(Triple(3, 90, 5), sets("3 sets of 5 x 40/20, rest between sets 90s"))
    }

    @Test
    fun `sets with no rest named between them rest a minute`() {
        assertEquals(Triple(4, 60, 8), sets("4 x (8 x 20/10)"))
        val described = read("4 sets of 8 rounds 20/10")!!
        assertTrue(ConfigField.Sets in described.understood && ConfigField.SetRest in described.understood)
    }

    @Test
    fun `one count alone is still the rounds`() {
        assertEquals(Triple(20, 10, 8), shape("8 sets of 20/10"))
        assertEquals(1, read("8 sets of 20/10")!!.config.sets)
        assertEquals(1, read("3 sets of 10 reps")!!.config.sets)
    }

    /** Work, the ladder's step and the rounds, as [text] reads. */
    private fun ladder(text: String): Triple<Int, Int, Int>? =
        read(text)?.config?.let { Triple(it.workSeconds, it.workStepSeconds, it.rounds) }

    @Test
    fun `a span with a step reads as a ladder and sets the rounds`() {
        assertEquals(Triple(20, 10, 5), ladder("ladder 20 to 60 by 10"))
        assertEquals(Triple(60, -10, 5), ladder("60 down to 20 by 10, 10 off"))
        assertEquals(10, read("60 down to 20 by 10, 10 off")!!.config.restSeconds)
        assertTrue(ConfigField.WorkStep in read("ladder 20 to 60 by 10")!!.understood)
    }

    @Test
    fun `a span the step doesn't divide reads to the last round that fits`() {
        assertEquals(Triple(20, 10, 4), ladder("ladder 20 to 55 by 10"))
    }

    @Test
    fun `a step a round climbs or falls from the work`() {
        assertEquals(Triple(20, 10, 8), ladder("20/10 climbing 10 a round"))
        assertEquals(10, read("20/10 climbing 10 a round")!!.config.restSeconds)
        assertFalse(read("20/10 climbing 10 a round")!!.activityFound)
        assertEquals(Triple(60, -5, 8), ladder("60/15 down 5 each round"))
        assertEquals(Triple(30, 15, 6), ladder("30 on 15 off x6 +15s per round"))
    }

    @Test
    fun `climbing with no step a round is still the activity`() {
        val described = read("30 min climbing")!!
        assertEquals(Activity.Climbing, described.config.activity)
        assertEquals(0, described.config.workStepSeconds)
    }

    @Test
    fun `a ladder's step fits the words' work and rounds and not the timer's`() {
        // The Timer is at 30 s × 8: a fall of 10 a round from it would not fit, from 60 s × 5 it does.
        assertEquals(Triple(60, -10, 5), ladder("60 on, 10 off, 5 rounds, down 10 a round"))
    }
}
