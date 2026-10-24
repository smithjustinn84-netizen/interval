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
import dev.justinsmith.interval.model.LoggedWorkout
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.asLiftWorkouts
import dev.justinsmith.interval.model.loggedWorkouts
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

        // Newest first, so grouping keeps both the days and the rows within them in order.
        val sections = loggedWorkouts(history, lifts)
            .groupBy { timestamps.dayNumber(it.finishedAtEpochMillis) }
            .map { (dayNumber, workouts) ->
                val done = log.day(dayNumber).plans.mapNotNull { it.workoutId }.toSet()
                HistorySection(
                    timestamps.heading(dayNumber, today),
                    dayNumber,
                    workouts.map {
                        item(it, presets, body, done)
                    },
                )
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
                val done = training.plans.mapNotNull { it.workoutId }.toSet()
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
                    workouts = loggedWorkouts(training.workouts, training.liftWorkouts).map {
                        item(it, presets, body, done)
                    },
                    past = day < today,
                )
            },
        )
    }

    /** [workout]'s row; an interval workout is [planned] when its id is among those the day's plans were done by. */
    private fun item(workout: LoggedWorkout, presets: List<Preset>, body: BodySettings, planned: Set<Long>) =
        when (workout) {
            is LoggedWorkout.Interval -> workout.entry.let { entry ->
                HistoryItem(
                    presetName = presets.matching(entry.config)?.name,
                    config = entry.config,
                    endedEarly = entry.endedEarly,
                    time = timestamps.time(entry.finishedAtEpochMillis),
                    total = formatClock(entry.elapsedSeconds),
                    calories = entry.calories(body),
                    id = entry.id,
                    planned = entry.id in planned,
                )
            }

            is LoggedWorkout.Lift -> workout.workout.let { lift ->
                HistoryItem(
                    presetName = lift.title,
                    config = IntervalConfig(activity = Activity.Kettlebell),
                    endedEarly = false,
                    time = timestamps.time(lift.finishedAtEpochMillis),
                    total = formatClock(lift.elapsedSeconds),
                    calories = lift.calories(body),
                    liftUid = lift.uid,
                )
            }
        }

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
