package dev.justinsmith.interval.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.ProgramDay
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LiftWorkout
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.asLiftWorkouts
import dev.justinsmith.interval.plan.PlanChoice
import dev.justinsmith.interval.plan.planChoices
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.Activity
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

/** A day's heading: Today, Yesterday or Tomorrow beside [today], else its date. */
private fun TimestampFormatter.heading(day: Long, today: Long): HistoryDay = when (day) {
    today -> HistoryDay.Today
    today - 1 -> HistoryDay.Yesterday
    today + 1 -> HistoryDay.Tomorrow
    else -> HistoryDay.On(dateOfDay(day))
}

/**
 * The plans still to do from [today], with the next day of the program followed on Lift among them, by day: it
 * falls today, unless a day of it was finished today already, and leads the plans of its day.
 */
private fun TimestampFormatter.upNext(log: TrainingLog, lifting: Lifting, today: Long): List<UpNextItem> {
    val lift = lifting.programDay?.let { day ->
        val doneToday = lifting.sessions.any { session ->
            session.program == day.program.key && session.finishedAtEpochMillis?.let(::dayNumber) == today
        }
        val epochDay = if (doneToday) today + 1 else today
        UpNextItem.Lift(epochDay, heading(epochDay, today), day, resume = lifting.inProgress)
    }
    val plans = log.upNext().map {
        UpNextItem.Planned(it.plan.id, it.epochDay, heading(it.epochDay, today), it.plan.name, it.plan.config)
    }
    return (listOfNotNull(lift) + plans).sortedBy { it.epochDay }
}

/** Lift's side: its finished sessions, the followed program's next day, and whether one is in progress. */
private data class Lifting(val sessions: List<LiftSession>, val programDay: ProgramDay?, val inProgress: Boolean)

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
    private val bells: BellsRepository,
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

    private val lifting = combine(bells.sessions, bells.programDay, bells.active) { sessions, day, active ->
        Lifting(sessions, day, active != null)
    }

    val state: StateFlow<HistoryUiState> =
        combine(
            combine(repository.history, lifting) { history, lifting -> history to lifting },
            repository.planned,
            repository.presets,
            repository.appSettings,
            combine(days, view, repository.config) { _, view, config -> view to config },
        ) { (history, lifting), planned, presets, app, (view, config) ->
            buildState(history, lifting, planned, presets, app, view, config)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.Eagerly,
                initialValue = buildState(
                    repository.history.value,
                    Lifting(bells.sessions.value, bells.programDay.value, bells.active.value != null),
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

    /** Starts the next day of [program], for the Lifting page about to open. */
    fun startLift(program: ProgramKey) {
        bells.startSession(program)
    }

    /** Takes the plan off, handing it back for Undo ([restorePlanned]); null when it was gone already. */
    fun removePlanned(planId: Long): PlannedWorkout? {
        val plan = repository.planned.value.firstOrNull { it.id == planId }
        repository.removePlanned(planId)
        return plan
    }

    fun restorePlanned(plan: PlannedWorkout) {
        repository.restorePlanned(plan)
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
        lifting: Lifting,
        planned: List<PlannedWorkout>,
        presets: List<Preset>,
        app: AppSettings,
        view: HistoryView,
        config: IntervalConfig,
    ): HistoryUiState {
        val today = timestamps.dayNumber(clock.nowMillis())
        val lifts = lifting.sessions.asLiftWorkouts()
        val log = TrainingLog(history, planned, today, lifts, timestamps::dayNumber)
        val body = app.body
        val week = log.week()

        // Both kinds of workout, newest first, so grouping keeps both the days and the rows within them in order.
        val rows = history.map { it.finishedAtEpochMillis to it } + lifts.map { it.finishedAtEpochMillis to it }
        val sections = rows.sortedByDescending { it.first }
            .groupBy { timestamps.dayNumber(it.first) }
            .map { (dayNumber, entries) ->
                val done = log.day(dayNumber).plans.mapNotNull { it.workoutId }.toSet()
                val items = entries.map { (_, entry) ->
                    when (entry) {
                        is HistoryEntry -> item(entry, presets, body, entry.id in done)
                        else -> liftItem(entry as LiftWorkout, body)
                    }
                }
                HistorySection(timestamps.heading(dayNumber, today), dayNumber, items)
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
            weekCount = week.sumOf { it.count },
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
            upNext = timestamps.upNext(log, lifting, today),
            hasPlans = planned.isNotEmpty() || lifting.programDay != null,
            choices = planChoices(presets, config),
            daySheet = view.openDay?.let { day ->
                val training = log.day(day)
                DaySheet(
                    epochDay = day,
                    day = timestamps.heading(day, today),
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
                    workouts = (
                        training.workouts.map { entry ->
                            entry.finishedAtEpochMillis to
                                item(entry, presets, body, training.plans.any { it.workoutId == entry.id })
                        } + training.liftWorkouts.map { it.finishedAtEpochMillis to liftItem(it, body) }
                        ).sortedByDescending { it.first }.map { it.second },
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

    private fun liftItem(workout: LiftWorkout, body: BodySettings) = HistoryItem(
        presetName = workout.title,
        config = IntervalConfig(activity = Activity.Kettlebell),
        endedEarly = false,
        time = timestamps.time(workout.finishedAtEpochMillis),
        total = formatClock(workout.elapsedSeconds),
        calories = workout.calories(body),
        liftUid = workout.uid,
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

    private companion object {
        const val DAY_CHECK_MILLIS = 60_000L
        const val WEEKS_SHOWN = 12
        const val MOST_DONE_SHOWN = 3
    }
}
