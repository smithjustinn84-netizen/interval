package dev.justinsmith.interval.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.AppSettings
import dev.justinsmith.interval.data.BodySettings
import dev.justinsmith.interval.data.HistoryEntry
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.data.PlanSchedule
import dev.justinsmith.interval.data.PlannedWorkout
import dev.justinsmith.interval.plan.PlanChoice
import dev.justinsmith.interval.plan.planChoices
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.dateOfEpochDay
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.monthStartAfter
import dev.justinsmith.interval.timer.monthStartOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/** The heading over one day: "Today", "Yesterday", "Tomorrow", or its date. */
sealed interface HistoryDay {
    data object Today : HistoryDay

    data object Yesterday : HistoryDay

    data object Tomorrow : HistoryDay

    /** Any other day, as the platform formats its date: "Sunday, Oct 11". */
    data class On(val date: String) : HistoryDay
}

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

/** A plan still to do: on [day], [name] in [config]'s shape. */
data class UpNextItem(
    val planId: Long,
    val epochDay: Long,
    val day: HistoryDay,
    val name: String,
    val config: IntervalConfig,
)

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

/** What the screen is looking at, apart from the data: the range, the month and the open day. */
private data class HistoryView(
    val range: TrainingRange = TrainingRange.Week,
    val monthOffset: Int = 0,
    val openDay: Long? = null,
    val locale: String? = null,
)

/**
 * The training log: what has been done by day, what is planned, and how the user is doing against their
 * weekly goal over a week, a month or all time. Today is worked out from [clock], and checked every
 * [dayCheckMillis] so that the headings and the week move on at midnight with the screen open.
 */
class HistoryViewModel(
    private val repository: IntervalRepository,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
) : ViewModel() {
    private val days = flow {
        while (true) {
            emit(timestamps.dayNumber(clock.nowMillis()))
            delay(dayCheckMillis)
        }
    }.distinctUntilChanged()

    private val view = MutableStateFlow(HistoryView())

    val state: StateFlow<HistoryUiState> =
        combine(
            repository.history,
            repository.planned,
            repository.presets,
            repository.appSettings,
            combine(days, view, repository.config) { _, view, config -> view to config },
        ) { history, planned, presets, app, (view, config) ->
            buildState(history, planned, presets, app, view, config)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = buildState(
                    repository.history.value,
                    repository.planned.value,
                    repository.presets.value,
                    repository.appSettings.value,
                    view.value,
                    repository.config.value,
                ),
            )

    fun setRange(range: TrainingRange) {
        view.update { it.copy(range = range) }
    }

    /** Moves the calendar [months] months on, or back when negative. */
    fun shiftMonth(months: Int) {
        view.update { it.copy(monthOffset = it.monthOffset + months) }
    }

    /** Opens the day sheet on [epochDay], or closes it with null. */
    fun openDay(epochDay: Long?) {
        view.update { it.copy(openDay = epochDay) }
    }

    /** Puts [name]'s [config] on the Timer, for the workout about to start. */
    fun putOnTimer(name: String, config: IntervalConfig) {
        repository.applyPreset(Preset(name, config))
    }

    fun planWeekly(preset: Preset, weekdays: Set<Int>) {
        repository.planWeekly(preset, weekdays)
    }

    fun planOnce(preset: Preset, epochDay: Long) {
        repository.planOnce(preset, epochDay)
    }

    fun removePlanned(planId: Long) {
        repository.removePlanned(planId)
    }

    fun clear() {
        repository.clearHistory()
    }

    /**
     * The app's locale, as a language tag. The dates and times are formatted in this ViewModel, which
     * outlives a language change, so they are formatted again when it changes.
     */
    fun setLocale(languageTag: String) {
        view.update { it.copy(locale = languageTag) }
    }

    private fun buildState(
        history: List<HistoryEntry>,
        planned: List<PlannedWorkout>,
        presets: List<Preset>,
        app: AppSettings,
        view: HistoryView,
        config: IntervalConfig,
    ): HistoryUiState {
        val today = timestamps.dayNumber(clock.nowMillis())
        val log = TrainingLog(history, planned, today, timestamps::dayNumber)
        val body = app.body
        val week = log.week()

        // History is newest first, so grouping keeps both the days and the rows within them in order.
        val sections = history.groupBy { timestamps.dayNumber(it.finishedAtEpochMillis) }.map { (dayNumber, entries) ->
            val done = log.day(dayNumber).plans.mapNotNull { it.workoutId }.toSet()
            HistorySection(heading(dayNumber, today), dayNumber, entries.map { item(it, presets, body, it.id in done) })
        }

        val monthOf = monthStartAfter(today, view.monthOffset)
        val stats = log.stats(view.range, body, monthOf)
        val goal = app.weeklyGoal
        return HistoryUiState(
            sections = sections,
            range = view.range,
            summary = RangeSummary(
                count = stats.count,
                time = formatClock(stats.seconds),
                totalSeconds = stats.seconds,
                calories = stats.calories,
                plannedDone = stats.plannedDone,
                plannedDue = stats.plannedDue,
                since = stats.firstDay?.let(timestamps::dateOfDay),
            ),
            weekCount = week.sumOf { it.workouts.size },
            goal = goal,
            streak = goal?.let(log::streak),
            week = week.map { cell(it, inMonth = true) },
            month = MonthView(
                title = timestamps.monthTitle(monthOf),
                cells = log.month(monthOf).map { cell(it.day, it.inMonth) },
                current = monthOf == monthStartOf(today),
            ),
            weeks = log.weeklyCounts(WEEKS_SHOWN),
            mostDone = log.mostDone(MOST_DONE_SHOWN).map { tally ->
                ShapeRow(presets.matching(tally.config)?.name, tally.config, tally.count, tally.seconds)
            },
            upNext = log.upNext().map {
                UpNextItem(it.plan.id, it.epochDay, heading(it.epochDay, today), it.plan.name, it.plan.config)
            },
            hasPlans = planned.isNotEmpty(),
            choices = planChoices(presets, config),
            daySheet = view.openDay?.let { day ->
                val training = log.day(day)
                DaySheet(
                    epochDay = day,
                    day = heading(day, today),
                    weekday = training.weekday,
                    plans = training.plans.map { plan ->
                        SheetPlan(
                            plan.plan.id,
                            plan.plan.name,
                            plan.plan.config,
                            weekly = plan.plan.schedule is PlanSchedule.Weekly,
                            state = plan.state,
                        )
                    },
                    workouts = training.workouts.reversed().map { entry ->
                        item(entry, presets, body, training.plans.any { it.workoutId == entry.id })
                    },
                    past = day < today,
                )
            },
        )
    }

    private fun item(entry: HistoryEntry, presets: List<Preset>, body: BodySettings, planned: Boolean) =
        HistoryItem(
            presetName = presets.matching(entry.config)?.name,
            config = entry.config,
            endedEarly = entry.endedEarly,
            time = timestamps.time(entry.finishedAtEpochMillis),
            total = formatClock(entry.elapsedSeconds),
            calories = entry.calories(body),
            id = entry.id,
            planned = planned,
        )

    private fun cell(day: TrainingDay, inMonth: Boolean) = DayCell(
        epochDay = day.epochDay,
        weekday = day.weekday,
        dayOfMonth = dateOfEpochDay(day.epochDay).day,
        state = day.state,
        seconds = day.seconds,
        today = day.isToday,
        inMonth = inMonth,
        date = timestamps.dateOfDay(day.epochDay),
    )

    private fun heading(day: Long, today: Long): HistoryDay = when (day) {
        today -> HistoryDay.Today
        today - 1 -> HistoryDay.Yesterday
        today + 1 -> HistoryDay.Tomorrow
        else -> HistoryDay.On(timestamps.dateOfDay(day))
    }

    private companion object {
        const val DAY_CHECK_MILLIS = 60_000L
        const val WEEKS_SHOWN = 12
        const val MOST_DONE_SHOWN = 3
    }
}
