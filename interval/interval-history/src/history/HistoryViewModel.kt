package dev.justinsmith.interval.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.data.PlanStore
import dev.justinsmith.interval.data.PresetStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedWorkout
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.loggedWorkouts
import dev.justinsmith.interval.plan.planChoices
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.monthStartAfter
import dev.justinsmith.interval.timer.monthStartOf
import dev.justinsmith.lift.data.LiftActivity
import dev.justinsmith.lift.domain.ProgramKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet

private const val KEY_RANGE = "history_range"
private const val KEY_MONTH_OFFSET = "history_month_offset"
private const val KEY_OPEN_DAY = "history_open_day"

private fun SavedStateHandle.restoredView() = HistoryView(
    range = TrainingRange.entries.firstOrNull { it.name == get<String>(KEY_RANGE) } ?: TrainingRange.Week,
    monthOffset = get<Int>(KEY_MONTH_OFFSET) ?: 0,
    openDay = get<Long>(KEY_OPEN_DAY),
)

private fun SavedStateHandle.keep(view: HistoryView) {
    this[KEY_RANGE] = view.range.name
    this[KEY_MONTH_OFFSET] = view.monthOffset
    this[KEY_OPEN_DAY] = view.openDay
}

/**
 * What the screen is looking at, apart from the data: the range, the month and the open day, each kept in the
 * entry's saved state so that they outlive the process. The locale is the screen's, set again as it shows.
 */
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
 *
 * Each state is built on [computation], not the main thread: the build walks the whole history, formats every
 * row's time and date and reads the calendar for every day since the first workout, and it is built again on
 * every change, each set logged on Lift among them. The screen starts from [HistoryUiState.Loading] until the
 * first is built. The Timer's steps change only the day sheet's [HistoryUiState.choices], which are worked out
 * on their own and rebuild nothing else.
 */
class HistoryViewModel(
    private val historyStore: HistoryStore,
    private val presetStore: PresetStore,
    private val planStore: PlanStore,
    private val settingsStore: SettingsStore,
    private val bells: LiftActivity,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
    computation: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val days = clock.days(timestamps, dayCheckMillis)

    private val view = MutableStateFlow(savedState.restoredView())

    // Distinct, so a set logged on Lift (which changes `active`) rebuilds nothing here: only a session finished,
    // the program's day or whether one is under way at all count.
    private val lifting = bells.lifting()

    private val built = combine(
        combine(historyStore.history, lifting) { history, lifting -> history to lifting },
        planStore.planned,
        presetStore.presets,
        settingsStore.appSettings,
        combine(days, view) { _, view -> view },
    ) { (history, lifting), planned, presets, app, view ->
        buildState(history, lifting, planned, presets, app, view)
    }

    private val choices = combine(presetStore.presets, settingsStore.config, ::planChoices).distinctUntilChanged()

    val state: StateFlow<HistoryUiState> =
        combine(built, choices) { state, choices -> state.copy(choices = choices) }
            .flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState.Loading)

    fun setRange(range: TrainingRange) {
        changeView { it.copy(range = range) }
    }

    /** Moves the calendar [months] months on, or back when negative. */
    fun shiftMonth(months: Int) {
        changeView { it.copy(monthOffset = it.monthOffset + months) }
    }

    /** Opens the day sheet on [epochDay], or closes it with null. */
    fun openDay(epochDay: Long?) {
        changeView { it.copy(openDay = epochDay) }
    }

    /** Puts [name]'s [config] on the Timer, for the workout about to start. */
    fun putOnTimer(name: String, config: IntervalConfig) {
        presetStore.applyPreset(Preset(name, config))
    }

    fun planWeekly(workout: PlanWorkout, weekdays: Set<Int>) {
        planStore.planWeekly(workout, weekdays)
    }

    fun planOnce(workout: PlanWorkout, epochDay: Long) {
        planStore.planOnce(workout, epochDay)
    }

    /** Starts the next day of [program], or a free session with none, for the Lifting page about to open. */
    fun startLift(program: ProgramKey?) {
        bells.startSession(program)
    }

    /** Takes the plan off, handing it back for Undo ([restorePlanned]); null when it was gone already. */
    fun removePlanned(planId: Long): PlannedWorkout? {
        val plan = planStore.planned.value.firstOrNull { it.id == planId }
        planStore.removePlanned(planId)
        return plan
    }

    fun restorePlanned(plan: PlannedWorkout) {
        planStore.restorePlanned(plan)
    }

    fun clear() {
        historyStore.clearHistory()
    }

    /**
     * The app's locale, as a language tag. The dates and times are formatted in this ViewModel, which
     * outlives a language change, so they are formatted again when it changes.
     */
    fun setLocale(languageTag: String) {
        view.update { it.copy(locale = languageTag) }
    }

    // Kept once the change is in, outside the update, whose lambda can run more than once.
    private fun changeView(transform: (HistoryView) -> HistoryView) {
        savedState.keep(view.updateAndGet(transform))
    }

    private fun buildState(
        history: List<HistoryEntry>,
        lifting: Lifting,
        planned: List<PlannedWorkout>,
        presets: List<Preset>,
        app: AppSettings,
        view: HistoryView,
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
                val plans = log.day(dayNumber).plans
                HistorySection(
                    timestamps.heading(dayNumber, today),
                    dayNumber,
                    workouts.map {
                        item(it, presets, body, plans)
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
            weekLifts = week.sumOf { it.liftWorkouts.size },
            goal = goal,
            streak = goal?.let(log::streak),
            week = week.map { timestamps.dayCell(it) },
            month = MonthView(
                title = timestamps.monthTitle(monthOf),
                cells = log.month(monthOf).map { timestamps.dayCell(it.day, it.inMonth) },
                current = monthOf == monthStartOf(today),
            ),
            weeks = log.weeklyCounts(WEEKS_SHOWN),
            mostDone = log.mostDone(MOST_DONE_SHOWN).map { tally ->
                ShapeRow(presets.matching(tally.config)?.name, tally.config, tally.count, tally.seconds)
            },
            upNext = timestamps.upNext(log, planned, lifting, today),
            hasPlans = planned.isNotEmpty() || lifting.programDay != null,
            programDay = lifting.programDay,
            daySheet = view.openDay?.let { day ->
                val training = log.day(day)
                DaySheet(
                    epochDay = day,
                    day = timestamps.heading(day, today),
                    weekday = training.weekday,
                    plans = training.plans.map { plan ->
                        SheetPlan(
                            plan.plan.id,
                            plan.plan.workout,
                            weekly = plan.plan.schedule is PlanSchedule.Weekly,
                            state = plan.state,
                        )
                    },
                    workouts = loggedWorkouts(training.workouts, training.liftWorkouts).map {
                        item(it, presets, body, training.plans)
                    },
                    past = day < today,
                )
            },
        )
    }

    /** [workout]'s row, [HistoryItem.planned] when it did one of its day's [plans]. */
    private fun item(workout: LoggedWorkout, presets: List<Preset>, body: BodySettings, plans: List<DayPlan>) =
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
                    planned = plans.any { it.workoutId != null && it.workoutId == entry.id },
                    effort = entry.effort,
                    note = entry.note,
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
                    planned = plans.any { it.liftUid == lift.uid },
                    liftUid = lift.uid,
                )
            }
        }

    private companion object {
        const val WEEKS_SHOWN = 12
        const val MOST_DONE_SHOWN = 3
    }
}
