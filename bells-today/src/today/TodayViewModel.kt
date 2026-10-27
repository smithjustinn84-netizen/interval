package dev.justinsmith.bells.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.data.IntervalActivity
import dev.justinsmith.bells.data.IntervalWorkout
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.DEFAULT_BELL_KG
import dev.justinsmith.bells.domain.Exercise
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.ProgramDay
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.barbellLifts
import dev.justinsmith.bells.domain.kettlebellLifts
import dev.justinsmith.bells.domain.setsOldestFirst
import dev.justinsmith.bells.domain.startingLoadKg
import dev.justinsmith.bells.domain.suggestedBar
import dev.justinsmith.bells.domain.suggestedBell
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.weekStartOf
import dev.justinsmith.interval.timer.weekdayOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

/** One day of this week: how many sessions each app logged on it. */
data class WeekDay(
    val epochDay: Long,
    val weekday: Int,
    val bells: Int,
    val interval: Int,
    val today: Boolean,
    val date: String,
)

data class TodayUiState(
    val week: List<WeekDay> = emptyList(),
    val last: LiftSession? = null,
    /** When [last] finished: "7:42 AM" today, else its date. */
    val lastWhen: String = "",
    val inProgress: Boolean = false,
    /** Whether the session in progress has sets logged: with none, Start day N on Lift drops it without asking. */
    val openHasSets: Boolean = false,
    /** The program followed, if any, and its next day: null once it is over. */
    val following: Program? = null,
    val programDay: ProgramDay? = null,
    /**
     * The weight to suggest for each bell lift, with one bell and with two, and each barbell lift (with one): the
     * last used for it so.
     */
    val bells: Map<Pair<Exercise, Boolean>, Double> = emptyMap(),
    /** Workouts a week the user aims for, lifts and intervals alike, as set on Plan; null for none. */
    val weeklyGoal: Int? = null,
) {
    val weekCount: Int get() = week.sumOf { it.bells + it.interval }

    /** The load to show beside [block]: the plan's, else the one last used for its lift, else where it starts. */
    fun suggestedKg(block: Block): Double =
        block.kg ?: bells[block.exercise to block.double] ?: startingLoadKg(block.exercise)
    val intervalCount: Int get() = week.sumOf { it.interval }
}

/**
 * This week's lifts and intervals, the last session, and whether one is in progress. Each state is built on
 * [computation], off the main thread: the suggested load for every lift scans the whole history, and the state
 * is built again on each set logged (the session under way changes with it). Only the first is built on the caller.
 */
class TodayViewModel(
    private val repository: BellsRepository,
    private val intervals: IntervalActivity,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
    computation: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    // Read again each midnight, so the week strip and its "today" move on with the screen open.
    val state: StateFlow<TodayUiState> =
        combine(
            repository.sessions,
            intervals.workouts,
            repository.active,
            repository.bar,
            clock.days(timestamps, dayCheckMillis),
        ) { sessions, interval, active, bar, _ -> build(sessions, interval, active, bar) }
            .combine(
                combine(repository.following, repository.programDay, intervals.weeklyGoal, ::Triple),
            ) { state, (following, day, goal) ->
                state.copy(following = following, programDay = day, weeklyGoal = goal)
            }.flowOn(computation).stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                build(
                    repository.sessions.value,
                    intervals.workouts.value,
                    repository.active.value,
                    repository.bar.value,
                )
                    .copy(
                        following = repository.following.value,
                        programDay = repository.programDay.value,
                        weeklyGoal = intervals.weeklyGoal.value,
                    ),
            )

    /** Starts the session about to open: [program]'s next day, or a free one with null. */
    fun start(program: ProgramKey?) {
        repository.startSession(program)
    }

    /** Drops the session in progress and starts [program]'s next day in its place. */
    fun startOver(program: ProgramKey) {
        repository.discardSession()
        repository.startSession(program)
    }

    private fun build(
        sessions: List<LiftSession>,
        interval: List<IntervalWorkout>,
        active: LiftSession?,
        bar: Double?,
    ): TodayUiState {
        val today = timestamps.dayNumber(clock.nowMillis())
        val bellsDays = sessions.mapNotNull { it.finishedAtEpochMillis?.let(timestamps::dayNumber) }
        val intervalDays = interval.map { timestamps.dayNumber(it.finishedAtEpochMillis) }
        val start = weekStartOf(today)
        val last = sessions.firstOrNull()
        return TodayUiState(
            week = (start until start + DAYS_PER_WEEK).map { day ->
                WeekDay(
                    epochDay = day,
                    weekday = weekdayOf(day),
                    bells = bellsDays.count { it == day },
                    interval = intervalDays.count { it == day },
                    today = day == today,
                    date = timestamps.dateOfDay(day),
                )
            },
            last = last,
            lastWhen = last?.finishedAtEpochMillis?.let { at ->
                if (timestamps.dayNumber(at) == today) timestamps.time(at) else timestamps.date(at)
            }.orEmpty(),
            inProgress = active != null,
            openHasSets = !active?.sets.isNullOrEmpty(),
            bells = sessions.setsOldestFirst().let { history ->
                kettlebellLifts.flatMap {
                    listOf(it to false, it to true)
                }.associateWith { (exercise, double) ->
                    suggestedBell(exercise, double, history, DEFAULT_BELL_KG)
                } + barbellLifts.associate { (it to false) to suggestedBar(it, history, bar) }
            },
        )
    }
}
