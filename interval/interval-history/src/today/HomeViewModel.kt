package dev.justinsmith.interval.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.FlowStore
import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.data.PlanStore
import dev.justinsmith.interval.data.PresetStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.history.DayCell
import dev.justinsmith.interval.history.Lifting
import dev.justinsmith.interval.history.TodayAgenda
import dev.justinsmith.interval.history.TrainingLog
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.history.WeekGoal
import dev.justinsmith.interval.history.asLiftWorkouts
import dev.justinsmith.interval.history.dayCell
import dev.justinsmith.interval.history.lifting
import dev.justinsmith.interval.history.todayAgenda
import dev.justinsmith.interval.history.upNext
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.platform.DAY_CHECK_MILLIS
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.days
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.lift.data.LiftActivity
import dev.justinsmith.lift.domain.ProgramKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val KEY_MINUTES = "home_minutes"

/**
 * What the Today tab shows: this week against the goal, what is up next, and the presets that fit the
 * [minutes] picked.
 */
data class HomeUiState(
    /** Monday to Sunday of this week. */
    val week: List<DayCell> = emptyList(),
    val weekGoal: WeekGoal = WeekGoal(),
    /** What is still to do, soonest first; [agenda] is how the tab leads with it. Empty with nothing planned. */
    val upNext: List<UpNextItem> = emptyList(),
    /** Something is planned or a program is followed, whether or not any of it is still to do: not the first run. */
    val hasPlans: Boolean = false,
    val minutes: Int = DEFAULT_PICK_MINUTES,
    /** The program's next day, first among the picks while it fits the minutes. */
    val strength: StrengthPick? = null,
    val picks: List<QuickPick> = emptyList(),
    /** Not built yet: the screen waits rather than taking it for a day with nothing planned. */
    val loading: Boolean = false,
) {
    /** What leads the tab, with its Start or its Log it, what else today holds beside it, and what comes after. */
    val agenda: TodayAgenda = todayAgenda(upNext)

    companion object {
        /** What the screen shows until the first state is built. */
        val Loading = HomeUiState(loading = true)
    }
}

/**
 * Today, the app's first tab: the answer to "what now?" from what the log, the plan and Lift already know. The
 * week and what is up next are History's own ([TrainingLog], [upNext]), built on [computation] as History builds
 * them and checked every [dayCheckMillis] so they move on at midnight; the presets by time are worked out apart,
 * so a length picked rebuilds nothing else. The length picked is kept in the entry's saved state.
 *
 * It starts nothing itself: [putOnTimer] and [startLift] set up what the page about to open runs, as History's
 * Up next does.
 */
class HomeViewModel(
    private val historyStore: HistoryStore,
    private val presetStore: PresetStore,
    flowStore: FlowStore,
    private val planStore: PlanStore,
    settingsStore: SettingsStore,
    private val lift: LiftActivity,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
    computation: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    /** The length picked by hand; null until one is, and again once the session's length is set anew. */
    private val picked = MutableStateFlow(savedState.get<Int>(KEY_MINUTES)?.takeIf { it in PICK_MINUTES })

    /** The session's length from Set up, where it is one Today offers. */
    private val session = settingsStore.appSettings
        .map { app -> app.sessionMinutes?.takeIf { it in PICK_MINUTES } }
        .distinctUntilChanged()

    private val minutes = combine(picked, session) { picked, session -> picked ?: session ?: DEFAULT_PICK_MINUTES }

    // The dates are formatted here, which outlives a language change, so they are formatted again when it changes.
    private val locale = MutableStateFlow<String?>(null)

    private val built = combine(
        combine(historyStore.history, historyStore.sportDays, lift.lifting(), ::Triple),
        planStore.planned,
        settingsStore.appSettings,
        combine(clock.days(timestamps, dayCheckMillis), locale) { day, _ -> day },
    ) { (history, sports, lifting), planned, app, _ -> buildState(history, sports, lifting, planned, app) }

    private val picks = combine(
        presetStore.presets,
        flowStore.flows,
        settingsStore.config,
        minutes,
        lift.lifting(),
    ) { presets, flows, onTimer, minutes, lifting ->
        val strength = strengthPick(lifting.programDay, lifting.restSeconds, minutes, lifting.inProgress)
        // With the program's day among them, a preset fewer: four rows at most.
        val shown = if (strength == null) PICKS_SHOWN else PICKS_SHOWN - 1
        Picked(minutes, strength, presets.fitting(minutes, onTimer, flows, shown), onTimer.prepSeconds)
    }

    val state: StateFlow<HomeUiState> =
        combine(built, picks) { state, picked ->
            // A flow suggested runs after the get-ready the Timer is set to, as one picked by time does.
            val upNext = state.upNext.map {
                if (it is UpNextItem.Flow) {
                    it.copy(
                        totalSeconds = it.flow.config(picked.prepSeconds).totalSeconds,
                    )
                } else {
                    it
                }
            }
            state.copy(minutes = picked.minutes, strength = picked.strength, picks = picked.picks, upNext = upNext)
        }
            .flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState.Loading)

    init {
        // This page is alive under Set up: a length answered there is what it shows on the way back, whatever
        // was picked by hand before.
        viewModelScope.launch {
            session.drop(1).collect {
                picked.value = null
                savedState.remove<Int>(KEY_MINUTES)
            }
        }
    }

    /** Picks the length, one of [PICK_MINUTES], that the presets offered fit. */
    fun setMinutes(length: Int) {
        if (length !in PICK_MINUTES) return
        picked.value = length
        savedState[KEY_MINUTES] = length
    }

    /** Puts [name]'s [config] on the Timer, for the workout about to start. */
    fun putOnTimer(name: String, config: IntervalConfig) {
        presetStore.applyPreset(Preset(name, config))
    }

    /** Puts [preset] on the Timer, for the workout about to start: a pick's, which keeps its id. */
    fun putOnTimer(preset: Preset) {
        presetStore.applyPreset(preset)
    }

    /** Plans [workout] every week on [weekdays], from a look before Start's Plan for later. */
    fun planWeekly(workout: PlanWorkout, weekdays: Set<Int>) {
        planStore.planWeekly(workout, weekdays)
    }

    /** Plans [workout] once, on [epochDay]. */
    fun planOnce(workout: PlanWorkout, epochDay: Long) {
        planStore.planOnce(workout, epochDay)
    }

    /** Starts the next day of [program], or a free session with none, for the Lifting page about to open. */
    fun startLift(program: ProgramKey?) {
        lift.startSession(program)
    }

    /** Logs a day of [sport] today. Returns the day it went on, for Undo to take off ([unlogSport]). */
    fun logSport(sport: Sport): Long {
        val today = timestamps.dayNumber(clock.nowMillis())
        historyStore.logSport(sport, today)
        return today
    }

    fun unlogSport(sport: Sport, epochDay: Long) {
        historyStore.unlogSport(sport, epochDay)
    }

    /** The app's locale, as a language tag: the days are named again when it changes. */
    fun setLocale(languageTag: String) {
        locale.value = languageTag
    }

    private fun buildState(
        history: List<HistoryEntry>,
        sports: List<LoggedSport>,
        lifting: Lifting,
        planned: List<PlannedWorkout>,
        app: AppSettings,
    ): HomeUiState {
        val today = timestamps.dayNumber(clock.nowMillis())
        val log = TrainingLog(history, planned, today, lifting.sessions.asLiftWorkouts(), sports, timestamps::dayNumber)
        val week = log.week()
        val goal = app.weeklyGoal
        return HomeUiState(
            week = week.map { timestamps.dayCell(it) },
            weekGoal = WeekGoal(
                count = week.sumOf { it.count },
                lifts = week.sumOf { it.liftWorkouts.size },
                goal = goal,
                streak = goal?.let(log::streak),
                sports = week.sumOf { it.sports.size },
                mobility = week.sumOf { it.mobility },
            ),
            upNext = timestamps.upNext(log, planned, lifting, today),
            hasPlans = planned.isNotEmpty() || lifting.programDay != null,
        )
    }
}

/** What the minutes picked come to: the strength that fits, the presets and flow, and the Timer's get-ready. */
private data class Picked(
    val minutes: Int,
    val strength: StrengthPick?,
    val picks: List<QuickPick>,
    val prepSeconds: Int,
)
