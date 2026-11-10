package dev.justinsmith.interval.history

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.FlowStore
import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.data.PlanStore
import dev.justinsmith.interval.data.PresetStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.LoggedWorkout
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
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
import kotlinx.coroutines.flow.runningReduce
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
// One function a thing the Log's screen does: a plan, a sport day or a workout logged, each with its undo.
@Suppress("detekt.TooManyFunctions")
class HistoryViewModel(
    private val historyStore: HistoryStore,
    private val presetStore: PresetStore,
    private val flowStore: FlowStore,
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
        combine(historyStore.history, historyStore.sportDays, lifting, ::Logged),
        planStore.planned,
        combine(presetStore.presets, flowStore.flows, ::WorkoutNames),
        settingsStore.appSettings,
        combine(days, view) { _, view -> view },
    ) { logged, planned, names, app, view -> buildState(logged, planned, names, app, view) }
        .runningReduce { before, now -> now.keepingListsOf(before) }

    private val choices = combine(presetStore.presets, settingsStore.config, ::planChoices).distinctUntilChanged()

    val state: StateFlow<HistoryUiState> =
        combine(built, choices, flowStore.flows) { state, choices, flows ->
            state.copy(choices = choices, flows = flows)
        }
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

    /** Logs a day of [sport] on [epochDay]: today's from Up next, or a day's own from its sheet. */
    fun logSport(sport: Sport, epochDay: Long) {
        historyStore.logSport(sport, epochDay)
    }

    fun unlogSport(sport: Sport, epochDay: Long) {
        historyStore.unlogSport(sport, epochDay)
    }

    /**
     * Logs a workout of [config] done away from the app on [epochDay], in full: today's as of now, a day gone by at
     * its midday, since the day is all that is known of when. Returns its uid, for Undo ([unlogWorkout]).
     */
    fun logWorkout(config: IntervalConfig, epochDay: Long): String {
        val now = clock.nowMillis()
        val finishedAt = if (epochDay == timestamps.dayNumber(now)) now else timestamps.middayOf(epochDay)
        return historyStore.logWorkout(config, finishedAt)
    }

    fun unlogWorkout(uid: String) {
        historyStore.unlogWorkout(uid)
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
        logged: Logged,
        planned: List<PlannedWorkout>,
        names: WorkoutNames,
        app: AppSettings,
        view: HistoryView,
    ): HistoryUiState {
        val (history, sports, lifting) = logged
        val today = timestamps.dayNumber(clock.nowMillis())
        val lifts = lifting.sessions.asLiftWorkouts()
        val log = TrainingLog(history, planned, today, lifts, sports, timestamps::dayNumber)
        val body = app.body
        val week = log.week()

        val sections = timestamps.sections(loggedWorkouts(history, lifts), sports, log, today) { workout, plans ->
            timestamps.item(workout, names, body, plans)
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
            weekSports = week.sumOf { it.sports.size },
            weekMobility = week.sumOf { it.mobility },
            goal = goal,
            streak = goal?.let(log::streak),
            byKind = week.byKind(),
            weekStart = week.first().epochDay,
            today = today,
            week = week.map { timestamps.dayCell(it) },
            month = MonthView(
                title = timestamps.monthTitle(monthOf),
                cells = log.month(monthOf).map { timestamps.dayCell(it.day, it.inMonth) },
                current = monthOf == monthStartOf(today),
            ),
            weeks = log.weeklyCounts(WEEKS_SHOWN),
            mostDone = log.mostDone(MOST_DONE_SHOWN).map { tally ->
                ShapeRow(names.of(tally.config), tally.config, tally.count, tally.seconds)
            },
            // Led as Today leads it: on a day with a sport, the program's day stands back behind it.
            // The flow suggested before a day's sport is Today's to offer: History lists what is planned.
            upNext = todayAgenda(timestamps.upNext(log, planned, lifting, today).filter { it !is UpNextItem.Flow }).all,
            hasPlans = planned.isNotEmpty() || lifting.programDay != null,
            programDay = lifting.programDay,
            daySheet = view.openDay?.let { day ->
                val training = log.day(day)
                daySheet(training, timestamps.heading(day, today), past = day < today) {
                    timestamps.item(it, names, body, training.plans)
                }
            },
        )
    }

    private companion object {
        const val WEEKS_SHOWN = 12
        const val MOST_DONE_SHOWN = 3
    }
}

/** [workout]'s row, [HistoryItem.planned] when it did one of its day's [plans]. */
private fun TimestampFormatter.item(
    workout: LoggedWorkout,
    names: WorkoutNames,
    body: BodySettings,
    plans: List<DayPlan>,
) =
    when (workout) {
        is LoggedWorkout.Interval -> workout.entry.let { entry ->
            HistoryItem(
                presetName = names.of(entry.config),
                config = entry.config,
                endedEarly = entry.endedEarly,
                time = time(entry.finishedAtEpochMillis),
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
                time = time(lift.finishedAtEpochMillis),
                total = formatClock(lift.elapsedSeconds),
                calories = lift.calories(body),
                planned = plans.any { it.liftUid == lift.uid },
                liftUid = lift.uid,
            )
        }
    }

/**
 * The log by day, the latest first: each day's workouts as [item] makes its row, given the day's plans, and the
 * sports logged on it. A day with only a sport logged is a day of the log too. [workouts] are newest first, so
 * the rows within a day keep that order.
 */
private fun TimestampFormatter.sections(
    workouts: List<LoggedWorkout>,
    sports: List<LoggedSport>,
    log: TrainingLog,
    today: Long,
    item: (LoggedWorkout, plans: List<DayPlan>) -> HistoryItem,
): List<HistorySection> {
    val byDay = workouts.groupBy { dayNumber(it.finishedAtEpochMillis) }
    val sportsByDay = sports.groupBy { it.epochDay }
    return (byDay.keys + sportsByDay.keys).sortedDescending().map { day ->
        val plans = log.day(day).plans
        HistorySection(
            heading(day, today),
            day,
            byDay[day].orEmpty().map { item(it, plans) },
            // As logged, the first of the day first.
            sportsByDay[day].orEmpty().sortedBy { it.id }.map { it.sport },
        )
    }
}

/** The sheet of [day]: its plans as they stand, what was done on it, each workout as [item] makes its row. */
private fun daySheet(day: TrainingDay, heading: HistoryDay, past: Boolean, item: (LoggedWorkout) -> HistoryItem) =
    DaySheet(
        epochDay = day.epochDay,
        day = heading,
        weekday = day.weekday,
        plans = day.plans.map { plan ->
            SheetPlan(plan.plan.id, plan.plan.workout, weekly = plan.plan.schedule is PlanSchedule.Weekly, plan.state)
        },
        workouts = loggedWorkouts(day.workouts, day.liftWorkouts).map(item),
        past = past,
        sports = day.sports.map { it.sport },
    )

/**
 * This state with each of [before]'s lists in place of its own where they are equal: a rebuild for the day sheet,
 * the range or a change elsewhere hands the screen the instances it had, which a screen comparing them by instance
 * (iOS, where the compiler takes them as unstable) skips. The day sheet and the summary are whatever was built.
 */
private fun HistoryUiState.keepingListsOf(before: HistoryUiState) = copy(
    sections = sections.keptFrom(before.sections),
    byKind = byKind.keptFrom(before.byKind),
    week = week.keptFrom(before.week),
    month = month.keptFrom(before.month),
    weeks = weeks.keptFrom(before.weeks),
    mostDone = mostDone.keptFrom(before.mostDone),
    upNext = upNext.keptFrom(before.upNext),
)

/** [before] where it equals this, so a state rebuilt with the same contents hands on the instance it had. */
internal fun <T> T.keptFrom(before: T): T = if (this == before) before else this
