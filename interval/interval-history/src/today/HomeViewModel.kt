package dev.justinsmith.interval.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.HistoryStore
import dev.justinsmith.interval.data.PlanStore
import dev.justinsmith.interval.data.PresetStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.history.DayCell
import dev.justinsmith.interval.history.Lifting
import dev.justinsmith.interval.history.TrainingLog
import dev.justinsmith.interval.history.UpNextItem
import dev.justinsmith.interval.history.WeekGoal
import dev.justinsmith.interval.history.asLiftWorkouts
import dev.justinsmith.interval.history.dayCell
import dev.justinsmith.interval.history.lifting
import dev.justinsmith.interval.history.upNext
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.PlannedWorkout
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

private const val KEY_MINUTES = "home_minutes"

/**
 * What the Today tab shows: this week against the goal, what is up next, and the presets that fit the
 * [minutes] picked.
 */
data class HomeUiState(
    /** Monday to Sunday of this week. */
    val week: List<DayCell> = emptyList(),
    val weekGoal: WeekGoal = WeekGoal(),
    /** What is still to do, soonest first: the first leads the tab, with its Start. Empty with nothing planned. */
    val upNext: List<UpNextItem> = emptyList(),
    /** Something is planned or a program is followed, whether or not any of it is still to do: not the first run. */
    val hasPlans: Boolean = false,
    val minutes: Int = DEFAULT_PICK_MINUTES,
    val picks: List<QuickPick> = emptyList(),
    /** Not built yet: the screen waits rather than taking it for a day with nothing planned. */
    val loading: Boolean = false,
) {
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
    historyStore: HistoryStore,
    private val presetStore: PresetStore,
    planStore: PlanStore,
    settingsStore: SettingsStore,
    private val lift: LiftActivity,
    private val timestamps: TimestampFormatter,
    private val clock: WallClock,
    private val savedState: SavedStateHandle = SavedStateHandle(),
    dayCheckMillis: Long = DAY_CHECK_MILLIS,
    computation: CoroutineDispatcher = Dispatchers.Default,
) : ViewModel() {
    private val minutes = MutableStateFlow(
        savedState.get<Int>(KEY_MINUTES)?.takeIf { it in PICK_MINUTES } ?: DEFAULT_PICK_MINUTES,
    )

    // The dates are formatted here, which outlives a language change, so they are formatted again when it changes.
    private val locale = MutableStateFlow<String?>(null)

    private val built = combine(
        combine(historyStore.history, historyStore.sportDays, lift.lifting(), ::Triple),
        planStore.planned,
        settingsStore.appSettings,
        combine(clock.days(timestamps, dayCheckMillis), locale) { day, _ -> day },
    ) { (history, sports, lifting), planned, app, _ -> buildState(history, sports, lifting, planned, app) }

    private val picks = combine(presetStore.presets, settingsStore.config, minutes) { presets, onTimer, minutes ->
        minutes to presets.fitting(minutes, onTimer)
    }

    val state: StateFlow<HomeUiState> =
        combine(built, picks) { state, (minutes, picks) -> state.copy(minutes = minutes, picks = picks) }
            .flowOn(computation)
            .stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState.Loading)

    /** Picks the length, one of [PICK_MINUTES], that the presets offered fit. */
    fun setMinutes(length: Int) {
        if (length !in PICK_MINUTES) return
        minutes.value = length
        savedState[KEY_MINUTES] = length
    }

    /** Puts [name]'s [config] on the Timer, for the workout about to start. */
    fun putOnTimer(name: String, config: IntervalConfig) {
        presetStore.applyPreset(Preset(name, config))
    }

    /** Puts [pick]'s preset on the Timer, for the workout about to start. */
    fun putOnTimer(pick: QuickPick) {
        presetStore.applyPreset(pick.preset)
    }

    /** Starts the next day of [program], or a free session with none, for the Lifting page about to open. */
    fun startLift(program: ProgramKey?) {
        lift.startSession(program)
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
            ),
            upNext = timestamps.upNext(log, planned, lifting, today),
            hasPlans = planned.isNotEmpty() || lifting.programDay != null,
        )
    }
}
