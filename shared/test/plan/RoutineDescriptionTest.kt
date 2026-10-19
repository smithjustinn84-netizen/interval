package dev.justinsmith.interval.plan

import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.epochDayOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

// Thursday 15 October 2026.
private val TODAY = epochDayOf(2026, 10, 15)

class RoutineDescriptionTest {
    private val current = IntervalConfig(prepSeconds = 25, workSeconds = 30, restSeconds = 15, rounds = 8)

    private fun read(text: String, presets: List<Preset> = builtInPresets): DescribedRoutine? =
        describeRoutine(text, current, presets, TODAY)

    /** Each plan as "name weekdays days", the days counted from today. */
    private fun plans(text: String): List<String> = read(text)!!.plans.map { plan ->
        "${plan.workout.name} ${plan.weekdays.sorted()} ${plan.days.map { it - TODAY }.sorted()}"
    }

    @Test
    fun `a preset goes on every weekday named after it`() {
        assertEquals(listOf("Tabata [1, 3, 5] []"), plans("Tabata Mon, Wed and Fri"))
        assertEquals(listOf("Tabata [1, 3, 5] []"), plans("tabata on mondays wednesdays and fridays"))
    }

    @Test
    fun `days said first go with the workout after them`() {
        assertEquals(listOf("Tabata [1, 3, 5] []"), plans("mon wed fri: tabata"))
    }

    @Test
    fun `each workout keeps its own days`() {
        val routine = read("tabata mon and thu, 20 min bike saturday")!!
        assertEquals(listOf(setOf(1, 4), setOf(6)), routine.plans.map { it.weekdays })
        assertEquals("Tabata", routine.plans[0].workout.name)
        val bike = routine.plans[1].workout
        assertEquals(Activity.Bike, bike.config.activity)
        assertNull(bike.id)
        assertEquals("30/15 ×27", bike.name)
    }

    @Test
    fun `a preset changed by the words is named by its shape`() {
        assertEquals(listOf("20/10 ×12 [2] []"), plans("tabata x12 tuesdays"))
    }

    @Test
    fun `the user's own preset keeps its id`() {
        val mine = Preset("Hills", IntervalConfig(workSeconds = 60, restSeconds = 60, rounds = 6), id = 7)
        assertEquals(7, read("hills on sunday", builtInPresets + mine)!!.plans.single().workout.id)
    }

    @Test
    fun `runs of days and the words for them`() {
        assertEquals(listOf("Tabata [1, 2, 3, 4, 5] []"), plans("tabata mon-fri"))
        assertEquals(listOf("Tabata [1, 5, 6, 7] []"), plans("tabata fri to mon"))
        assertEquals(listOf("Tabata [1, 2, 3, 4, 5] []"), plans("tabata on weekdays"))
        assertEquals(listOf("Tabata [6, 7] []"), plans("tabata weekends"))
        assertEquals(listOf("Tabata [1, 2, 3, 4, 5, 6, 7] []"), plans("tabata every day"))
    }

    @Test
    fun `today and tomorrow and the coming weekdays are one-offs`() {
        assertEquals(listOf("Tabata [] [0]"), plans("tabata today"))
        assertEquals(listOf("Tabata [] [1]"), plans("tabata tomorrow"))
        assertEquals(listOf("Tabata [] [1]"), plans("tabata this friday"))
        assertEquals(listOf("Tabata [] [7]"), plans("tabata next thursday"))
        assertEquals(listOf("Tabata [] [0]"), plans("tabata this thursday"))
    }

    @Test
    fun `a goal is read and kept in range`() {
        assertEquals(4, read("tabata mon wed, 4 a week")!!.goal)
        assertEquals(3, read("3 times per week")!!.goal)
        assertEquals(5, read("5x/week")!!.goal)
        assertEquals(14, read("20 workouts a week")!!.goal)
        assertEquals(emptyList(), read("4 days a week")!!.plans)
    }

    @Test
    fun `times a week is no count of rounds`() {
        val tabata = read("tabata 3 times a week")!!.plans.single()
        assertEquals(8, tabata.workout.config.rounds)
        assertFalse(tabata.scheduled)
    }

    @Test
    fun `on before a day is not work`() {
        val plan = read("40 on 20 off on mondays")!!.plans.single()
        assertEquals(40, plan.workout.config.workSeconds)
        assertEquals(20, plan.workout.config.restSeconds)
        assertEquals(setOf(1), plan.weekdays)
    }

    @Test
    fun `a workout split over clauses stays one`() {
        assertEquals(listOf("40/20 ×6 [3] []"), plans("40 on 20 off and 6 rounds on wednesday"))
    }

    @Test
    fun `a workout with no day is read but not scheduled`() {
        assertFalse(read("hiit 30/30")!!.plans.single().scheduled)
    }

    @Test
    fun `days alone or nothing at all read as nothing`() {
        assertNull(read("mondays"))
        assertNull(read("whenever I feel like it"))
    }
}
