package dev.justinsmith.interval.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.data.PlanStore
import dev.justinsmith.interval.data.PresetStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.history.TrainingLog
import dev.justinsmith.interval.history.asLiftWorkouts
import dev.justinsmith.interval.history.heading
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LiftWorkout
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.MINUTES_PER_DAY
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.lift.data.LiftActivity
import dev.justinsmith.lift.domain.ProgramDay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/**
 * A planned workout as the plan lists it. [programDay] is the day of the program a plan of its next day comes to
 * next, from where the program stands now; null for any other plan, or past the next two weeks.
 */
data class PlanEntry(val planId: Long, val workout: PlanWorkout, val programDay: Int? = null)

/** One weekday of the routine, 1 for Monday, and what is planned on it every week. */
data class RoutineDay(val weekday: Int, val plans: List<PlanEntry>)

/** A one-off still to come, on [epochDay], headed [day]. */
data class OneOff(val entry: PlanEntry, val epochDay: Long, val day: HistoryDay)

/** A reminder time offered in one tap: [minuteOfDay] after midnight, worded [text] ("7:00 AM"). */
data class ReminderPick(val minuteOfDay: Int, val text: String)

/**
 * The reminder as the plan shows it: [on] or off, and the time it stands at either way ([minuteOfDay],
 * worded [text]), which is the stored one or, while off, where it starts when turned on; [picks] the
 * times offered in one tap.
 */
data class ReminderUi(val on: Boolean, val minuteOfDay: Int, val text: String, val picks: List<ReminderPick>)

data class PlanUiState(
    /** Workouts a week the user aims for; null when there is no goal. */
    val goal: Int?,
    /** Weeks in a row that met the goal, up to now. */
    val streak: Int,
    val routine: List<RoutineDay>,
    val oneOffs: List<OneOff>,
    val choices: List<PlanChoice>,
    val days: List<DayChoice>,
    /** Today's weekday, the add sheet's first pick. */
    val todayWeekday: Int,
    /** Today as an epoch day, which a described routine's "tomorrow" counts from. */
    val today: Long,
    val reminder: ReminderUi,
    /** The day of the program followed to do next, or null when none is followed. */
    val programDay: ProgramDay? = null,
    /** Of the routine's weekdays, how many hold a plan of the program's next day. */
    val programWeekdays: Int = 0,
    /** The routine's sessions on Lift a week, and its interval workouts. */
    val routineLifts: Int = 0,
    val routineIntervals: Int = 0,
    val routineSports: Int = 0,
    /** Not built yet: the screen waits rather than drawing an empty plan. */
    val loading: Boolean = false,
) {
    companion object {
        /** What the screen shows until the first state is built: nothing to draw, the reminder off. */
        val Loading = PlanUiState(
            goal = null,
            streak = 0,
            routine = emptyList(),
            oneOffs = emptyList(),
            choices = emptyList(),
            days = emptyList(),
            todayWeekday = 1,
            today = 0,
            reminder = ReminderUi(on = false, minuteOfDay = 0, text = "", picks = emptyList()),
            loading = true,
        )
    }
}

/** Everything done that the streak counts: the interval workouts, Lift's sessions and the sport days logged. */
private class Done(val history: List<HistoryEntry>, val lifts: List<LiftWorkout>, val sports: List<LoggedSport>)

/**
 * The plan: the weekly goal, the routine for each weekday, and one-offs on dates to come. Adding goes
 * through [planWeekly] or [planOnce] with one of [PlanUiState.choices], or [planDescribed] with a routine
 * read from words. Each state is built on [computation], off the main thread: the streak reads the whole
 * history into a [TrainingLog], and the state is built again on every change of the plan, the history or the
 * settings. The screen starts from [PlanUiState.Loading] until the first is built. The Timer's steps change only
 * [PlanUiState.choices], which are worked out on their own and rebuild nothing else.
 */
class PlanViewModel(
    private val planStore: PlanStore,
    private val historyStore: HistoryStore,
    private val presetStore: PresetStore,
    private val settingsStore: SettingsStore,
    private val bells: LiftActivity,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
    computation: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    // Read again each midnight, so the one-offs and their headings move on with the screen open.
    private val built = combine(
        planStore.planned,
        settingsStore.appSettings,
        combine(
            historyStore.history,
            historyStore.sportDays,
            bells.sessions,
            bells.programDay,
            clock.days(timestamps, dayCheckMillis),
        ) { history, sports, sessions, programDay, _ ->
            Done(history, sessions.asLiftWorkouts(), sports) to programDay
        },
    ) { planned, app, (done, programDay) -> buildState(planned, app, done, programDay) }

    private val choices = combine(presetStore.presets, settingsStore.config, ::planChoices).distinctUntilChanged()

    val state: StateFlow<PlanUiState> =
        combine(built, choices) { state, choices -> state.copy(choices = choices) }
            .flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.Eagerly, PlanUiState.Loading)

    /** Raises or lowers the goal by one, as [steppedGoal] does. */
    fun stepGoal(direction: Int) {
        settingsStore.updateAppSettings { it.copy(weeklyGoal = steppedGoal(it.weeklyGoal, direction)) }
    }

    /** Sets the reminder to [minuteOfDay] after midnight, wrapping past either end of the day; null turns it off. */
    fun setReminder(minuteOfDay: Int?) {
        settingsStore.updateAppSettings { it.copy(reminderMinuteOfDay = minuteOfDay?.mod(MINUTES_PER_DAY)) }
    }

    fun planWeekly(workout: PlanWorkout, weekdays: Set<Int>) {
        planStore.planWeekly(workout, weekdays)
    }

    fun planOnce(workout: PlanWorkout, epochDay: Long) {
        planStore.planOnce(workout, epochDay)
    }

    /** Plans each of [routine]'s workouts on its days, and sets its goal if it has one. */
    fun planDescribed(routine: DescribedRoutine) {
        routine.plans.forEach { plan ->
            planStore.planWeekly(plan.workout, plan.weekdays)
            plan.days.sorted().forEach { planStore.planOnce(plan.workout, it) }
        }
        routine.goal?.let { goal -> settingsStore.updateAppSettings { it.copy(weeklyGoal = goal) } }
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

    private fun buildState(
        planned: List<PlannedWorkout>,
        app: AppSettings,
        done: Done,
        programDay: ProgramDay?,
    ): PlanUiState {
        val today = timestamps.dayNumber(clock.nowMillis())
        val log = TrainingLog(done.history, planned, today, done.lifts, done.sports, timestamps::dayNumber)
        val days = programDays(log, programDay)
        val entries = planned.associateWith { PlanEntry(it.id, it.workout, days[it.id]) }
        val weekly = planned.filter { it.schedule is PlanSchedule.Weekly }
        return PlanUiState(
            goal = app.weeklyGoal,
            streak = app.weeklyGoal?.let { log.streak(it).current } ?: 0,
            routine = (1..DAYS_PER_WEEK).map { weekday ->
                RoutineDay(
                    weekday,
                    planned.filter {
                        (it.schedule as? PlanSchedule.Weekly)?.weekday == weekday
                    }.map { entries.getValue(it) },
                )
            },
            oneOffs = planned
                .mapNotNull { plan ->
                    (plan.schedule as? PlanSchedule.Once)?.epochDay?.takeIf { it >= today }?.let { plan to it }
                }
                .sortedWith(compareBy({ it.second }, { it.first.id }))
                .map { (plan, day) -> OneOff(entries.getValue(plan), day, timestamps.heading(day, today)) },
            choices = emptyList(),
            days = dayChoices(today),
            todayWeekday = weekdayOf(today),
            today = today,
            reminder = reminder(app.reminderMinuteOfDay),
            programDay = programDay,
            programWeekdays = weekly
                .filter { it.workout == PlanWorkout.Lift.ProgramDay }
                .mapNotNull { (it.schedule as? PlanSchedule.Weekly)?.weekday }
                .distinct()
                .size,
            routineLifts = weekly.count { it.workout is PlanWorkout.Lift },
            routineIntervals = weekly.count { it.workout is PlanWorkout.Interval },
            routineSports = weekly.count { it.workout is PlanWorkout.SportDay },
        )
    }

    /**
     * The day of the program each plan of its next day comes to next, by plan: the open ones over the next two
     * weeks, in order, take the program's days from [next] on, a weekly plan at its first. None past the end.
     */
    private fun programDays(log: TrainingLog, next: ProgramDay?): Map<Long, Int> {
        next ?: return emptyMap()
        val days = mutableMapOf<Long, Int>()
        var number = next.number
        log.upNext(ONE_OFF_DAYS)
            .filter { it.plan.workout == PlanWorkout.Lift.ProgramDay }
            .forEach { upcoming ->
                if (number <= next.program.totalDays && upcoming.plan.id !in days) days[upcoming.plan.id] = number
                number++
            }
        return days
    }

    private fun reminder(minuteOfDay: Int?): ReminderUi {
        val shown = minuteOfDay ?: AppSettings.DEFAULT_REMINDER
        return ReminderUi(
            on = minuteOfDay != null,
            minuteOfDay = shown,
            text = timestamps.timeOfDay(shown),
            picks = REMINDER_PICKS.map { ReminderPick(it, timestamps.timeOfDay(it)) },
        )
    }
}
