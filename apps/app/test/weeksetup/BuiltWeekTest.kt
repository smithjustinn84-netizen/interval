package dev.justinsmith.interval.weeksetup

import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.snatchIntervals
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.program
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val MON = 1
private const val TUE = 2
private const val WED = 3
private const val THU = 4
private const val FRI = 5
private const val SAT = 6
private const val SUN = 7

class BuiltWeekTest {
    private val rest = RestClock.DEFAULT_SECONDS
    private val bell = setOf(16.0)

    private fun build(
        answers: WeekAnswers,
        planned: List<PlannedWorkout> = emptyList(),
        followed: ProgramId? = null,
        was: Sport? = null,
    ) = buildWeek(answers, planned, followed?.let(::program), rest, was)

    private fun weekly(id: Long, workout: PlanWorkout, weekday: Int) =
        PlannedWorkout(id, workout, PlanSchedule.Weekly(weekday))

    private val BuiltWeek.programId get() = (program?.key as? ProgramKey.BuiltIn)?.id

    @Test
    fun `a class three days a week takes a program on the days between`() {
        val week = build(WeekAnswers(Sport.Bjj, setOf(MON, WED, FRI), bell, minutes = 15))

        assertEquals(Sport.Bjj, week.sport)
        assertEquals(setOf(MON, WED, FRI), week.sportDays)
        assertEquals(ProgramId.FirstHundredSwings, week.programId)
        assertFalse(week.keepsProgram)
        assertEquals(setOf(TUE, THU, SAT), week.liftDays)
        assertEquals(6, week.goal)
        assertTrue(week.remove.isEmpty())
    }

    @Test
    fun `the bell alone spreads its program over the week`() {
        val three = build(WeekAnswers(bells = bell, minutes = 20))
        assertNull(three.sport)
        assertEquals(ProgramId.KettlebellFoundations, three.programId)
        assertEquals(setOf(MON, WED, FRI), three.liftDays)
        assertEquals(3, three.goal)

        val two = build(WeekAnswers(bells = bell, minutes = 30))
        assertEquals(ProgramId.BeginnerFullBody, two.programId)
        assertEquals(setOf(MON, THU), two.liftDays)
    }

    @Test
    fun `a grappler with one bell gets the program for it and the intervals on a third day`() {
        listOf(20, 30).forEach { minutes ->
            val week = build(WeekAnswers(Sport.Bjj, setOf(TUE, THU), bell, minutes = minutes))
            assertEquals(ProgramId.BjjOneBell, week.programId)
            assertEquals(setOf(MON, FRI), week.liftDays)
            assertEquals(WED, week.conditioningDay)
            assertEquals(5, week.goal)
        }
    }

    @Test
    fun `a grappler with a quarter of an hour starts as anyone does`() {
        val week = build(WeekAnswers(Sport.Bjj, setOf(TUE, THU), bell, minutes = 15))
        assertEquals(ProgramId.FirstHundredSwings, week.programId)
        assertNull(week.conditioningDay)
    }

    @Test
    fun `with no bell the sport's days are the week`() {
        val week = build(WeekAnswers(Sport.Climbing, setOf(TUE, SAT), minutes = 20))
        assertEquals(setOf(TUE, SAT), week.sportDays)
        assertNull(week.program)
        assertTrue(week.liftDays.isEmpty())
        assertEquals(2, week.goal)
    }

    @Test
    fun `with nothing answered there is no week`() {
        val week = build(WeekAnswers())
        assertNull(week.sport)
        assertNull(week.program)
        assertNull(week.goal)
    }

    @Test
    fun `a sport with no day picked plans none`() {
        val week = build(WeekAnswers(Sport.Running, emptySet(), bell, minutes = 15))
        assertNull(week.sport)
        assertEquals(setOf(MON, WED, FRI), week.liftDays)
    }

    @Test
    fun `a rest day is always left`() {
        val four = build(WeekAnswers(Sport.Bjj, setOf(MON, TUE, THU, FRI), bell, minutes = 15))
        assertEquals(setOf(WED, SAT), four.liftDays)
        assertEquals(6, four.goal)

        val six = build(WeekAnswers(Sport.Bjj, setOf(MON, TUE, WED, THU, FRI, SAT), bell, minutes = 15))
        assertTrue(six.liftDays.isEmpty())
        // No day for it, so it isn't taken up.
        assertNull(six.program)
        assertEquals(6, six.goal)
    }

    @Test
    fun `a program followed already is kept and its days planned`() {
        val week = build(
            WeekAnswers(Sport.Bjj, setOf(MON, WED), bell, minutes = 10),
            followed = ProgramId.SimpleAndSinister,
        )
        assertEquals(ProgramId.SimpleAndSinister, week.programId)
        assertTrue(week.keepsProgram)
        // Five days a week asked for; four go beside two classes and a rest day.
        assertEquals(4, week.liftDays.size)
        assertTrue(week.liftDays.none { it in setOf(MON, WED) })
        assertEquals(6, week.goal)
    }

    @Test
    fun `a program followed is kept with no bell and with no day free`() {
        val none = build(WeekAnswers(Sport.Running, setOf(TUE), minutes = 15), followed = ProgramId.ProgramMinimum)
        assertEquals(ProgramId.ProgramMinimum, none.programId)
        assertEquals(4, none.liftDays.size)

        val full = build(
            WeekAnswers(Sport.Bjj, setOf(MON, TUE, WED, THU, FRI, SAT), bell, minutes = 15),
            followed = ProgramId.ProgramMinimum,
        )
        assertEquals(ProgramId.ProgramMinimum, full.programId)
        assertTrue(full.keepsProgram)
        assertTrue(full.liftDays.isEmpty())
    }

    @Test
    fun `building again replaces the week it built and leaves the rest`() {
        val interval = PlanWorkout.Interval("Tabata", IntervalConfig())
        val planned = listOf(
            weekly(1, PlanWorkout.SportDay(Sport.Bjj), MON),
            weekly(2, PlanWorkout.SportDay(Sport.Bjj), WED),
            weekly(3, PlanWorkout.Lift.ProgramDay, TUE),
            weekly(4, PlanWorkout.Lift.ProgramDay, THU),
            weekly(5, interval, SAT),
            weekly(6, PlanWorkout.SportDay(Sport.Running), SUN),
            weekly(7, PlanWorkout.Lift.AnySession, FRI),
            PlannedWorkout(8, PlanWorkout.SportDay(Sport.Bjj), PlanSchedule.Once(20_000)),
        )

        val week = build(
            WeekAnswers(Sport.Climbing, setOf(TUE), bell, minutes = 15),
            planned,
            followed = ProgramId.FirstHundredSwings,
            was = Sport.Bjj,
        )

        // The classes it planned and the program's days go; the interval, the run, the free session and the
        // one-off stay, and their weekdays are taken.
        assertEquals(listOf(1L, 2L, 3L, 4L), week.remove)
        assertEquals(setOf(TUE), week.sportDays)
        // Friday, Saturday and Sunday are taken and Tuesday is the wall: two of the program's three days fit with
        // a rest day left, and of Monday, Wednesday and Thursday the two furthest apart are taken.
        assertEquals(setOf(MON, THU), week.liftDays)
        assertEquals(6, week.goal)
    }

    private val pair = setOf(24.0)

    private fun intervals(id: Long, weekday: Int) = weekly(id, snatchIntervals.asPlan(), weekday)

    @Test
    fun `a grappler with a pair on the mat twice gets the program's two days and the intervals on a third`() {
        val week = build(WeekAnswers(Sport.Bjj, setOf(TUE, THU), pair, minutes = 20, pairs = pair))
        assertEquals(ProgramId.BjjKettlebell, week.programId)
        assertEquals(setOf(MON, FRI), week.liftDays)
        assertEquals(WED, week.conditioningDay)
        assertEquals(5, week.goal)
    }

    @Test
    fun `on the mat three days the grappler's program keeps to its two`() {
        val week = build(WeekAnswers(Sport.Bjj, setOf(MON, WED, FRI), pair, minutes = 20, pairs = pair))
        assertEquals(ProgramId.BjjKettlebell, week.programId)
        assertEquals(2, week.liftDays.size)
        assertNull(week.conditioningDay)
        assertEquals(5, week.goal)
    }

    @Test
    fun `on the mat three days the one-bell program keeps to its two as well`() {
        val week = build(WeekAnswers(Sport.Bjj, setOf(MON, WED, FRI), bell, minutes = 20))
        assertEquals(ProgramId.BjjOneBell, week.programId)
        assertEquals(2, week.liftDays.size)
        assertNull(week.conditioningDay)
    }

    @Test
    fun `building again beside the one-bell program takes its intervals off first too`() {
        val planned = listOf(weekly(3, PlanWorkout.Lift.ProgramDay, MON), intervals(5, WED))
        val answers = WeekAnswers(Sport.Bjj, setOf(TUE, THU), bell, minutes = 20)
        val again = build(answers, planned, followed = ProgramId.BjjOneBell, was = Sport.Bjj)
        assertEquals(listOf(3L, 5L), again.remove)
        assertEquals(WED, again.conditioningDay)
    }

    @Test
    fun `building again takes the intervals it planned off first and plans the same week`() {
        val answers = WeekAnswers(Sport.Bjj, setOf(TUE, THU), pair, minutes = 20, pairs = pair)
        val planned = listOf(
            weekly(1, PlanWorkout.SportDay(Sport.Bjj), TUE),
            weekly(2, PlanWorkout.SportDay(Sport.Bjj), THU),
            weekly(3, PlanWorkout.Lift.ProgramDay, MON),
            weekly(4, PlanWorkout.Lift.ProgramDay, FRI),
            intervals(5, WED),
        )
        val again = build(answers, planned, followed = ProgramId.BjjKettlebell, was = Sport.Bjj)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), again.remove)
        assertEquals(setOf(MON, FRI), again.liftDays)
        assertEquals(WED, again.conditioningDay)
        assertEquals(5, again.goal)
    }

    @Test
    fun `intervals planned beside another program stay`() {
        val planned = listOf(intervals(5, SAT))
        val week = build(WeekAnswers(Sport.Bjj, setOf(TUE, THU), bell, minutes = 15), planned)
        assertTrue(week.remove.isEmpty())
        assertFalse(SAT in week.liftDays)
    }
}
