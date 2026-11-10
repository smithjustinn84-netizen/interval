package dev.justinsmith.lift.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.data.IntervalActivity
import dev.justinsmith.lift.data.IntervalWorkout
import dev.justinsmith.lift.data.ProgramStore
import dev.justinsmith.lift.data.SessionStore
import dev.justinsmith.lift.data.SetStore
import dev.justinsmith.lift.data.SportDay
import dev.justinsmith.lift.data.snatchSets
import dev.justinsmith.lift.domain.BALANCE_DAYS
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.Challenge
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Followed
import dev.justinsmith.lift.domain.GripLoad
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.MuscleBalance
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.Readiness
import dev.justinsmith.lift.domain.ReadinessCheck
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.barbellLifts
import dev.justinsmith.lift.domain.besideTheMat
import dev.justinsmith.lift.domain.builtInPrograms
import dev.justinsmith.lift.domain.challenges
import dev.justinsmith.lift.domain.estimatedMinutes
import dev.justinsmith.lift.domain.gripLoad
import dev.justinsmith.lift.domain.kettlebellLifts
import dev.justinsmith.lift.domain.oneBell
import dev.justinsmith.lift.domain.pairOfBells
import dev.justinsmith.lift.domain.program
import dev.justinsmith.lift.domain.readiness
import dev.justinsmith.lift.domain.sets
import dev.justinsmith.lift.domain.setsOldestFirst
import dev.justinsmith.lift.domain.startingBellKg
import dev.justinsmith.lift.domain.startingLoadKg
import dev.justinsmith.lift.domain.suggestedBar
import dev.justinsmith.lift.domain.suggestedBell
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class TodayUiState(
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
    /** Sets per muscle group over the last [BALANCE_DAYS] days; null until two sessions finished in them. */
    val balance: MuscleBalance? = null,
    /** The rest between sets, which [dayMinutes] is estimated with. */
    val restSeconds: Int = RestClock.DEFAULT_SECONDS,
    /** Programs for someone who grapples or climbs, other than the one followed: two at most. */
    val suggested: List<Program> = suggestedPrograms(null),
    /** The bells owned, in kg, lightest first. */
    val ownedBells: List<Double> = emptyList(),
    /** The grip's load lately, BJJ's logged days in it; null with nothing in the last four weeks. */
    val grip: GripLoad? = null,
    /** How ready for today's training, from the last session, the mat and the grip. */
    val readiness: ReadinessCheck = ReadinessCheck(Readiness.Normal),
    /** The benchmarks under way: 10,000 swings, the snatch test, the ABC's best, a bodyweight get-up. */
    val challenges: List<Challenge> = emptyList(),
) {
    /** How far through the program followed: the days done of all of them, 1 once it is over. */
    val progress: Float
        get() {
            val program = following ?: return 0f
            val done = programDay?.let { it.number - 1 } ?: program.totalDays
            return done.toFloat() / program.totalDays
        }

    /** About how long [programDay] takes, in minutes; null with none to do. */
    val dayMinutes: Int? get() = programDay?.estimatedMinutes(restSeconds)

    /** The load to show beside [block]: the plan's, else the one last used for its lift, else where it starts. */
    fun suggestedKg(block: Block): Double =
        block.load?.kg ?: bells[block.exercise to block.double] ?: startingLoadKg(block.exercise)
}

/**
 * The program's day, the last session, the balance, and whether a session is in progress. Each state is built on
 * [computation], off the main thread: the suggested load for every lift scans the whole history, and the state
 * is built again on each set logged (the session under way changes with it). Only the first is built on the caller.
 */
class TodayViewModel(
    private val sessionStore: SessionStore,
    private val programStore: ProgramStore,
    private val gearStore: GearStore,
    setStore: SetStore,
    intervals: IntervalActivity,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
    computation: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    // Read again each midnight, so the last session's "today" and the balance's weeks move on with the screen open.
    val state: StateFlow<TodayUiState> =
        combine(
            sessionStore.sessions,
            sessionStore.active,
            combine(gearStore.bar, gearStore.bells, ::Pair),
            combine(intervals.sportDays, intervals.workouts, ::Pair),
            clock.days(timestamps, dayCheckMillis),
        ) { sessions, active, gear, (sportDays, workouts), _ -> build(sessions, active, gear, sportDays, workouts) }
            .combine(
                combine(
                    programStore.following,
                    programStore.programDay,
                    setStore.restSeconds,
                    gearStore.bells,
                    gearStore.pairs,
                ) { following, day, rest, bells, pairs ->
                    { state: TodayUiState -> state.withProgram(following, day, rest, bells, pairs.isNotEmpty()) }
                },
            ) { state, with -> with(state) }
            .combine(
                combine(sessionStore.sessions, programStore.followed, intervals.bodyWeightKg, intervals.workouts) {
                        sessions,
                        followed,
                        weight,
                        workouts,
                    ->
                    { state: TodayUiState -> state.withChallenges(sessions, followed, weight, workouts) }
                },
            ) { state, with -> with(state) }
            .flowOn(computation)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                build(
                    sessionStore.sessions.value,
                    sessionStore.active.value,
                    gearStore.bar.value to gearStore.bells.value,
                    intervals.sportDays.value,
                    intervals.workouts.value,
                ).withProgram(
                    programStore.following.value,
                    programStore.programDay.value,
                    setStore.restSeconds.value,
                    gearStore.bells.value,
                    gearStore.pairs.value.isNotEmpty(),
                ).withChallenges(
                    sessionStore.sessions.value,
                    programStore.followed.value,
                    intervals.bodyWeightKg.value,
                    intervals.workouts.value,
                ),
            )

    /**
     * [state] with the challenges under way over [sessions] and the interval [workouts]' snatches, the program
     * [followed], and the body [weight].
     */
    private fun TodayUiState.withChallenges(
        sessions: List<LiftSession>,
        followed: Followed?,
        weight: Double?,
        workouts: List<IntervalWorkout>,
    ) = copy(
        challenges = challenges(
            sessions,
            clock.nowMillis(),
            followed?.program?.key,
            followed?.startedAtEpochMillis,
            weight,
            builtInPrograms + programStore.customPrograms.value,
            intervals = workouts.snatchSets,
        ),
    )

    /** Starts the session about to open: [program]'s next day, or a free one with null; none with one in progress. */
    fun start(program: ProgramKey?) {
        sessionStore.startSession(program)
    }

    /** Drops the session in progress and starts [program]'s next day in its place. */
    fun startOver(program: ProgramKey) {
        sessionStore.discardSession()
        sessionStore.startSession(program)
    }

    private fun build(
        sessions: List<LiftSession>,
        active: LiftSession?,
        gear: Pair<Double?, List<Double>>,
        sportDays: List<SportDay>,
        workouts: List<IntervalWorkout>,
    ): TodayUiState {
        val (bar, owned) = gear
        val now = clock.nowMillis()
        val today = timestamps.dayNumber(now)
        val balanceFrom = today - BALANCE_DAYS + 1
        val last = sessions.firstOrNull()
        val grappling = sportDays.filter { it.grappling }.map { it.epochDay }
        // 15:15's snatches load the grip as Lift's own sets do.
        val lifted = sessions.flatMap { it.sets } + workouts.snatchSets.sets
        val grip = gripLoad(lifted, grappling, today, timestamps::dayNumber)
        return TodayUiState(
            grip = grip.takeIf { it.chronic > 0 },
            readiness = readiness(sessions, grappling, grip, now, timestamps::dayNumber),
            last = last,
            lastWhen = last?.finishedAtEpochMillis?.let { at ->
                if (timestamps.dayNumber(at) == today) timestamps.time(at) else timestamps.date(at)
            }.orEmpty(),
            inProgress = active != null,
            openHasSets = !active?.sets.isNullOrEmpty(),
            // The session in progress is not among [sessions]: its sets count once it finishes.
            balance = MuscleBalance.of(
                sessions.filter { session ->
                    session.finishedAtEpochMillis?.let(timestamps::dayNumber)?.let { it in balanceFrom..today } == true
                },
            ),
            bells = sessions.setsOldestFirst().let { history ->
                kettlebellLifts.flatMap {
                    listOf(it to false, it to true)
                }.associateWith { (exercise, double) ->
                    suggestedBell(exercise, double, history, startingBellKg(exercise, owned))
                } + barbellLifts.associate { (it to false) to suggestedBar(it, history, bar) }
            },
        )
    }
}

/**
 * [this] with the program followed, its next [day], the [rest] between sets and the [bells] owned, a [pair] among
 * them or not.
 */
private fun TodayUiState.withProgram(
    following: Program?,
    day: ProgramDay?,
    rest: Int,
    bells: List<Double>,
    pair: Boolean,
) = copy(
    following = following,
    programDay = day,
    restSeconds = rest,
    suggested = suggestedPrograms(following, pair),
    ownedBells = bells.sorted(),
)

/**
 * The built-ins for grapplers, which suit a climber's week too, other than [following]: the first two of
 * [besideTheMat] that one bell is enough for, or with a [pair] owned that two are.
 */
fun suggestedPrograms(following: Program?, pair: Boolean = false): List<Program> = besideTheMat.map(::program)
    .filter { (if (pair) it.pairOfBells else it.oneBell) && it.key != following?.key }
    .take(SUGGESTED_SHOWN)

private const val SUGGESTED_SHOWN = 2
