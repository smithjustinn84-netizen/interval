package dev.justinsmith.interval.history

import dev.justinsmith.interval.fakes.DAY_MILLIS
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LiftWorkout
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.epochDayOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrainingTest {
    private val tabata = IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 8)
    private val hiit = IntervalConfig(prepSeconds = 10, workSeconds = 30, restSeconds = 30, rounds = 10)

    // Thursday 15 October 2026; its week runs Monday the 12th to Sunday the 18th.
    private val today = epochDayOf(2026, 10, 15)
    private val monday = epochDayOf(2026, 10, 12)

    private var nextId = 1L

    private fun workout(day: Long, config: IntervalConfig = tabata, seconds: Int = 240, hour: Int = 9) =
        HistoryEntry(day * DAY_MILLIS + hour * HOUR_MILLIS, config, seconds, endedEarly = false, id = nextId++)

    private fun weekly(weekday: Int, config: IntervalConfig = tabata, madeOn: Long = 0) =
        PlannedWorkout(
            nextId++,
            "Plan",
            config,
            PlanSchedule.Weekly(weekday),
            createdAtEpochMillis =
            madeOn * DAY_MILLIS,
        )

    private fun once(day: Long, config: IntervalConfig = tabata) =
        PlannedWorkout(nextId++, "Once", config, PlanSchedule.Once(day))

    private fun log(history: List<HistoryEntry>, planned: List<PlannedWorkout> = emptyList(), on: Long = today) =
        TrainingLog(history, planned, on) { it.floorDiv(DAY_MILLIS) }

    @Test
    fun `a plan is done by a workout of its shape that day`() {
        val plan = once(monday)
        val done = workout(monday)
        val day = log(listOf(done), listOf(plan)).day(monday)
        assertEquals(listOf(DayPlan(plan, PlanState.Done, done.id)), day.plans)
        assertEquals(DayState.Done, day.state)
    }

    @Test
    fun `a workout of another shape leaves the plan missed but the day done`() {
        val day = log(listOf(workout(monday, hiit)), listOf(once(monday))).day(monday)
        assertEquals(PlanState.Missed, day.plans.single().state)
        assertEquals(DayState.Done, day.state)
    }

    @Test
    fun `each workout does one plan and the earliest goes first`() {
        val first = once(monday)
        val second = once(monday)
        val morning = workout(monday, hour = 7)
        val evening = workout(monday, hour = 19)
        val plans = log(listOf(evening, morning), listOf(first, second)).day(monday).plans
        assertEquals(listOf(morning.id, evening.id), plans.map { it.workoutId })

        val oneRun = log(listOf(morning), listOf(first, second)).day(monday).plans
        assertEquals(listOf(PlanState.Done, PlanState.Missed), oneRun.map { it.state })
    }

    @Test
    fun `a plan left over is missed once its day has gone and open until then`() {
        val log = log(emptyList(), listOf(once(monday), once(today), once(today + 1)))
        assertEquals(DayState.Missed, log.day(monday).state)
        assertEquals(PlanState.Open, log.day(today).plans.single().state)
        assertEquals(DayState.Planned, log.day(today).state)
        assertEquals(DayState.Planned, log.day(today + 1).state)
        assertEquals(DayState.Rest, log.day(today + 2).state)
    }

    @Test
    fun `a weekly plan counts only from the day it was made`() {
        // Made on Wednesday the 14th for Mondays and Wednesdays.
        val mondays = weekly(1, madeOn = monday + 2)
        val wednesdays = weekly(3, madeOn = monday + 2)
        val log = log(emptyList(), listOf(mondays, wednesdays))
        assertEquals(emptyList(), log.plansOn(monday))
        assertEquals(listOf(wednesdays), log.plansOn(monday + 2))
        assertEquals(listOf(mondays), log.plansOn(monday + 7))
        assertEquals(emptyList(), log.plansOn(monday - 5))
    }

    @Test
    fun `the week runs Monday to Sunday with today marked`() {
        val week = log(emptyList()).week()
        assertEquals((monday..monday + 6).toList(), week.map { it.epochDay })
        assertEquals((1..7).toList(), week.map { it.weekday })
        assertEquals(listOf(false, false, false, true, false, false, false), week.map { it.isToday })
    }

    @Test
    fun `the month pads to whole weeks from Monday`() {
        val october = log(emptyList()).month(today)
        assertEquals(35, october.size)
        assertEquals(epochDayOf(2026, 9, 28), october.first().day.epochDay)
        assertEquals(epochDayOf(2026, 11, 1), october.last().day.epochDay)
        assertEquals(31, october.count { it.inMonth })
        // February 2021 began on a Monday and had exactly four weeks; August 2026 needs six.
        assertEquals(28, log(emptyList()).month(epochDayOf(2021, 2, 10)).size)
        assertEquals(42, log(emptyList()).month(epochDayOf(2026, 8, 31)).size)
    }

    @Test
    fun `stats cover the week the month or everything`() {
        val history = listOf(
            workout(monday, seconds = 240),
            workout(today, seconds = 100),
            workout(epochDayOf(2026, 10, 2), seconds = 60),
            workout(epochDayOf(2026, 3, 2), seconds = 30),
        )
        val log = log(history, listOf(once(monday), once(monday + 1), once(today), once(today + 2)))
        val none = BodySettings(weightKg = null)

        val week = log.stats(TrainingRange.Week, none)
        assertEquals(RangeStats(count = 2, seconds = 340, calories = null, plannedDone = 2, plannedDue = 3), week)

        val month = log.stats(TrainingRange.Month, none)
        assertEquals(3, month.count)
        assertEquals(400, month.seconds)

        val all = log.stats(TrainingRange.AllTime, none)
        assertEquals(4, all.count)
        assertEquals(epochDayOf(2026, 3, 2), all.firstDay)

        val march = log.stats(TrainingRange.Month, none, monthOf = epochDayOf(2026, 3, 20))
        assertEquals(1, march.count)
        assertNull(week.firstDay)
    }

    @Test
    fun `calories count once a weight is set`() {
        val stats = log(listOf(workout(monday))).stats(TrainingRange.Week, BodySettings(weightKg = 70.0))
        assertTrue(requireNotNull(stats.calories) > 0)
    }

    @Test
    fun `weekly counts run oldest first to this week`() {
        val counts = log(listOf(workout(monday), workout(today), workout(monday - 7))).weeklyCounts(3)
        assertEquals(listOf(monday - 14, monday - 7, monday), counts.map { it.weekStart })
        assertEquals(listOf(0, 1, 2), counts.map { it.count })
        assertEquals(listOf(false, false, true), counts.map { it.current })
    }

    @Test
    fun `this week joins the streak once it meets the goal`() {
        val lastTwoWeeks = listOf(workout(monday - 7), workout(monday - 6), workout(monday - 14), workout(monday - 13))
        assertEquals(Streak(current = 2, best = 2), log(lastTwoWeeks).streak(goal = 2))
        val withThisWeek = lastTwoWeeks + listOf(workout(monday), workout(today))
        assertEquals(Streak(current = 3, best = 3), log(withThisWeek).streak(goal = 2))
    }

    @Test
    fun `a week short of the goal breaks the streak but best remembers`() {
        val history = listOf(
            workout(monday - 35), workout(monday - 34),
            workout(monday - 28), workout(monday - 27),
            workout(monday - 21), workout(monday - 20),
            // A week with one: the run of three ends.
            workout(monday - 14),
            workout(monday - 7), workout(monday - 6),
        )
        assertEquals(Streak(current = 1, best = 3), log(history).streak(goal = 2))
        assertEquals(Streak(0, 0), log(emptyList()).streak(goal = 2))
    }

    @Test
    fun `up next lists open plans from today by day`() {
        val doneToday = once(today)
        val later = once(today + 3)
        val tomorrow = weekly(5, madeOn = 0)
        val tooFar = once(today + 7)
        val log = log(listOf(workout(today)), listOf(later, doneToday, tomorrow, tooFar, once(monday)))
        assertEquals(
            listOf(UpNextPlan(today + 1, tomorrow), UpNextPlan(today + 3, later)),
            log.upNext(),
        )
    }

    @Test
    fun `most done ranks shapes by count then time`() {
        val history = listOf(workout(monday), workout(today), workout(monday, hiit, seconds = 580))
        val top = log(history).mostDone(limit = 5)
        assertEquals(listOf(2, 1), top.map { it.count })
        assertEquals(listOf(tabata, hiit), top.map { it.config })
        assertEquals(480, top.first().seconds)
    }

    private fun bells(day: Long, seconds: Int = 900) = LiftWorkout(
        uid = "bells-${nextId++}",
        finishedAtEpochMillis = day * DAY_MILLIS + 7 * HOUR_MILLIS,
        elapsedSeconds = seconds,
        title = "Swings",
    )

    @Test
    fun `a kettlebell session makes its day done and adds to the totals`() {
        val log = TrainingLog(listOf(workout(monday)), emptyList(), today, listOf(bells(monday), bells(today))) {
            it.floorDiv(DAY_MILLIS)
        }
        assertEquals(DayState.Done, log.day(today).state)
        assertEquals(2, log.day(monday).count)
        assertEquals(240 + 900, log.day(monday).seconds)
        val week = log.stats(TrainingRange.Week, BodySettings())
        assertEquals(3, week.count)
        assertEquals(240 + 900 + 900, week.seconds)
    }

    @Test
    fun `a kettlebell session counts toward the streak but does no plan`() {
        val plan = once(monday)
        val log = TrainingLog(emptyList(), listOf(plan), today, listOf(bells(monday), bells(monday - 7))) {
            it.floorDiv(DAY_MILLIS)
        }
        assertEquals(PlanState.Missed, log.day(monday).plans.single().state)
        assertEquals(DayState.Done, log.day(monday).state)
        assertEquals(Streak(current = 2, best = 2), log.streak(goal = 1))
    }

    private companion object {
        const val HOUR_MILLIS = 60L * 60 * 1000
    }
}
