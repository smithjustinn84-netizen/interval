package dev.justinsmith.interval.history

import dev.justinsmith.bells.domain.ProgramDay
import dev.justinsmith.interval.plan.PlanChoice
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.formatClock

data class HistoryItem(
    /** The matching preset's name, or null for a workout no preset has; the row then leads with its shape. */
    val presetName: String?,
    val config: IntervalConfig,
    val endedEarly: Boolean,
    /** The time of day it finished: "7:42 PM". */
    val time: String,
    val total: String,
    /** The estimated calories burned, or null when no body weight is set. */
    val calories: Int? = null,
    /** The id it is stored under, to open or delete it; null for one not stored yet. */
    val id: Long? = null,
    /** Whether it did one of the day's plans. */
    val planned: Boolean = false,
    /**
     * The kettlebell session it is, by uid, or null for an interval workout. A session is named by
     * [presetName], its own title, of [config] only the activity means anything, and it opens its own page.
     */
    val liftUid: String? = null,
)

/** One day's workouts. [dayNumber] is its epoch day. */
data class HistorySection(val day: HistoryDay, val dayNumber: Long, val items: List<HistoryItem>)

/** One day of the week strip or the calendar. [dayOfMonth] is its number on the calendar. */
data class DayCell(
    val epochDay: Long,
    val weekday: Int,
    val dayOfMonth: Int,
    val state: DayState,
    val seconds: Int,
    val today: Boolean,
    /** In the month the calendar shows, rather than padding its first or last week. */
    val inMonth: Boolean = true,
    /** The day's date, for screen readers: "Wednesday, Oct 14". */
    val date: String = "",
)

/** Something still to do on [day]: an interval plan, or the next day of the lift program followed. */
sealed interface UpNextItem {
    val epochDay: Long
    val day: HistoryDay

    /** A plan: [name] in [config]'s shape. */
    data class Planned(
        val planId: Long,
        override val epochDay: Long,
        override val day: HistoryDay,
        val name: String,
        val config: IntervalConfig,
    ) : UpNextItem

    /**
     * The program's next day: today, or tomorrow once a day of it is done today. [resume] when a session is
     * in progress already, which Lift picks up before starting another.
     */
    data class Lift(
        override val epochDay: Long,
        override val day: HistoryDay,
        val programDay: ProgramDay,
        val resume: Boolean = false,
    ) : UpNextItem
}

/** The summary's figures for its range. [time] is a clock, "16:35"; [totalSeconds] is for longer ones. */
data class RangeSummary(
    val count: Int,
    val time: String,
    val totalSeconds: Int,
    val calories: Int?,
    val plannedDone: Int,
    val plannedDue: Int,
    /** For All time, the first workout's date. */
    val since: String? = null,
)

/** The month the calendar shows: its title, "October 2026", its whole weeks, and whether it is this month. */
data class MonthView(val title: String, val cells: List<DayCell>, val current: Boolean)

/** A shape and how often it has been done, named by its preset when one matches. */
data class ShapeRow(val name: String?, val config: IntervalConfig, val count: Int, val totalSeconds: Int)

/** A plan on the open day: whether it repeats every week, and how it stands. */
data class SheetPlan(
    val planId: Long,
    val name: String,
    val config: IntervalConfig,
    val weekly: Boolean,
    val state: PlanState,
)

/** The day sheet: the day a tap opened, with its plans and its workouts. */
data class DaySheet(
    val epochDay: Long,
    val day: HistoryDay,
    val weekday: Int,
    val plans: List<SheetPlan>,
    val workouts: List<HistoryItem>,
    val past: Boolean,
)

data class HistoryUiState(
    val sections: List<HistorySection>,
    val range: TrainingRange = TrainingRange.Week,
    val summary: RangeSummary = RangeSummary(0, formatClock(0), 0, null, 0, 0),
    /** This week's workouts against the weekly goal; [goal] is null until the user sets one. */
    val weekCount: Int = 0,
    val goal: Int? = null,
    val streak: Streak? = null,
    /** Monday to Sunday of this week. */
    val week: List<DayCell> = emptyList(),
    val month: MonthView = MonthView("", emptyList(), current = true),
    /** Workouts a week over the last twelve, for All time. */
    val weeks: List<WeekCount> = emptyList(),
    val mostDone: List<ShapeRow> = emptyList(),
    val upNext: List<UpNextItem> = emptyList(),
    val hasPlans: Boolean = false,
    val daySheet: DaySheet? = null,
    /** What the day sheet's Add workout can plan: the Timer's workout, then every preset. */
    val choices: List<PlanChoice> = emptyList(),
) {
    /** Nothing done and nothing planned: the first run. */
    val isEmpty: Boolean get() = sections.isEmpty() && !hasPlans

    /** The most workouts in one of [weeks], for scaling its bars; at least the goal, so its line fits. */
    val weeksMax: Int get() = maxOf(weeks.maxOfOrNull { it.count } ?: 0, goal ?: 0, 1)
}
