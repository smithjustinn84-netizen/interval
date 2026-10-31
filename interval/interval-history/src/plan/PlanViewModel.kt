package dev.justinsmith.interval.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.history.HistoryDay
import dev.justinsmith.interval.history.TrainingLog
import dev.justinsmith.interval.history.heading
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LiftWorkout
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.asLiftWorkouts
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.MINUTES_PER_DAY
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.weekdayOf
import dev.justinsmith.lift.data.LiftActivity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** A planned workout as the plan lists it. */
data class PlanEntry(val planId: Long, val name: String, val config: IntervalConfig)

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
)

/**
 * The plan: the weekly goal, the routine for each weekday, and one-offs on dates to come. Adding goes
 * through [planWeekly] or [planOnce] with one of [PlanUiState.choices], or [planDescribed] with a routine
 * read from words. Each state is built on [computation], off the main thread: the streak reads the whole
 * history into a [TrainingLog], and the state is built again on every change of the plan, the history, the
 * settings or the Timer. Only the first is built on the caller.
 */
class PlanViewModel(
    private val repository: IntervalRepository,
    private val bells: LiftActivity,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
    computation: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    // Read again each midnight, so the one-offs and their headings move on with the screen open.
    val state: StateFlow<PlanUiState> =
        combine(
            repository.planned,
            repository.presets,
            repository.config,
            repository.appSettings,
            combine(repository.history, bells.sessions, clock.days(timestamps, dayCheckMillis)) {
                    history,
                    sessions,
                    _,
                ->
                history to sessions.asLiftWorkouts()
            },
        ) { planned, presets, config, app, (history, lifts) ->
            buildState(planned, presets, config, app, history, lifts)
        }.flowOn(computation).stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            buildState(
                repository.planned.value,
                repository.presets.value,
                repository.config.value,
                repository.appSettings.value,
                repository.history.value,
                bells.sessions.value.asLiftWorkouts(),
            ),
        )

    /** Raises or lowers the goal by one, as [steppedGoal] does. */
    fun stepGoal(direction: Int) {
        repository.updateAppSettings { it.copy(weeklyGoal = steppedGoal(it.weeklyGoal, direction)) }
    }

    /** Sets the reminder to [minuteOfDay] after midnight, wrapping past either end of the day; null turns it off. */
    fun setReminder(minuteOfDay: Int?) {
        repository.updateAppSettings { it.copy(reminderMinuteOfDay = minuteOfDay?.mod(MINUTES_PER_DAY)) }
    }

    fun planWeekly(preset: Preset, weekdays: Set<Int>) {
        repository.planWeekly(preset, weekdays)
    }

    fun planOnce(preset: Preset, epochDay: Long) {
        repository.planOnce(preset, epochDay)
    }

    /** Plans each of [routine]'s workouts on its days, and sets its goal if it has one. */
    fun planDescribed(routine: DescribedRoutine) {
        routine.plans.forEach { plan ->
            repository.planWeekly(plan.workout, plan.weekdays)
            plan.days.sorted().forEach { repository.planOnce(plan.workout, it) }
        }
        routine.goal?.let { goal -> repository.updateAppSettings { it.copy(weeklyGoal = goal) } }
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

    private fun buildState(
        planned: List<PlannedWorkout>,
        presets: List<Preset>,
        config: IntervalConfig,
        app: AppSettings,
        history: List<HistoryEntry>,
        lifts: List<LiftWorkout>,
    ): PlanUiState {
        val today = timestamps.dayNumber(clock.nowMillis())
        val entries = planned.associateWith { PlanEntry(it.id, it.name, it.config) }
        return PlanUiState(
            goal = app.weeklyGoal,
            streak = app.weeklyGoal
                ?.let { TrainingLog(history, planned, today, lifts, timestamps::dayNumber).streak(it).current }
                ?: 0,
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
            choices = planChoices(presets, config),
            days = dayChoices(today),
            todayWeekday = weekdayOf(today),
            today = today,
            reminder = reminder(app.reminderMinuteOfDay),
        )
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
