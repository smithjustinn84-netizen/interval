package dev.justinsmith.interval.weeksetup

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.data.PlanStore
import dev.justinsmith.interval.data.SettingsStore
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.ui.weekdayMask
import dev.justinsmith.interval.ui.weekdaysOf
import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.data.LiftActivity
import dev.justinsmith.lift.data.ProgramStore
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.dayMinutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The first question unanswered: no sport picked, nor "Just the bell". */
const val SPORT_UNANSWERED = -1

/** The pick after [Sport.named]: a sport the user names. */
val SPORT_ANOTHER = Sport.named.size

/** The last pick: the bell alone, with no sport beside it. */
val SPORT_NONE = SPORT_ANOTHER + 1

/** The bell sizes Set up offers, in kg; the gear page has every size. */
val SETUP_BELLS = listOf(8.0, 12.0, 16.0, 20.0, 24.0, 32.0)

/** The session's length Set up opens on. */
const val DEFAULT_SESSION_MINUTES = 15

/**
 * Set up as it stands: the four answers, and the [week] they build. [sport] is a place among [Sport.named],
 * [SPORT_ANOTHER] (named by [sportName]), [SPORT_NONE] or [SPORT_UNANSWERED]; [weekdays] a bit per day, Monday
 * bit 0; [bellChoices] the sizes offered, the [bells] owned among them; [programMinutes] about how long the
 * week's program takes a day.
 */
data class WeekSetupUiState(
    val sport: Int = SPORT_UNANSWERED,
    val sportName: String = "",
    val weekdays: Int = 0,
    val bellChoices: List<Double> = SETUP_BELLS,
    val bells: Set<Double> = emptySet(),
    val minutes: Int = DEFAULT_SESSION_MINUTES,
    val week: BuiltWeek = BuiltWeek(),
    val programMinutes: IntRange? = null,
) {
    /** The sport answered, as far as it is: null for none, the bell alone, or one not yet named. */
    val answeredSport: Sport? get() = Sport.named.getOrNull(sport)
        ?: Sport.custom(sportName).takeIf { sport == SPORT_ANOTHER }

    /** Whether a sport is picked, so its days are asked for. */
    val asksDays: Boolean get() = sport in 0..SPORT_ANOTHER

    /** Whether nothing is answered yet that a week could be made of. */
    val unanswered: Boolean get() = sport == SPORT_UNANSWERED && bells.isEmpty() && week.program == null

    /** Whether the answers come to anything to build. */
    val canBuild: Boolean get() = week.sport != null || week.program != null || bells.isNotEmpty()
}

private const val KEY_SPORT = "setup_sport"
private const val KEY_NAME = "setup_sport_name"
private const val KEY_DAYS = "setup_days"
private const val KEY_BELLS = "setup_bells"
private const val KEY_MINUTES = "setup_minutes"

/**
 * Set up: four answers, shown as the week they would build, then built. It opens on what stands (the sport
 * planned every week and its days, the bells owned, the session's length), so it is also where they are changed.
 * The answers are kept in [savedState] as they are given. Building writes to both sides: the plan, the settings,
 * the gear and the program followed, which it never starts again.
 */
class WeekSetupViewModel(
    private val planStore: PlanStore,
    private val settingsStore: SettingsStore,
    private val gearStore: GearStore,
    private val programStore: ProgramStore,
    lift: LiftActivity,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    /** The sport planned every week as the page opened: its days are the ones building replaces. */
    private val was: Sport? = planStore.planned.value.weeklySport()

    /** The sizes offered: Set up's, and any other owned, so none is read as not owned. */
    private val chips = (SETUP_BELLS + gearStore.bells.value).distinct().sorted()

    private val answers = MutableStateFlow(saved() ?: standing())

    /** Built once: the plan follows its writes a moment late, and a second tap would plan the week twice. */
    private var built = false

    val state: StateFlow<WeekSetupUiState> =
        combine(answers, planStore.planned, programStore.following, lift.restSeconds, ::uiState).stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            uiState(answers.value, planStore.planned.value, programStore.following.value, lift.restSeconds.value),
        )

    /** Answers the first question: a place among [Sport.named], [SPORT_ANOTHER] or [SPORT_NONE]. */
    fun pickSport(sport: Int) = answer { it.copy(sport = sport.coerceIn(0, SPORT_NONE)) }

    /** Names the sport of [SPORT_ANOTHER]. */
    fun nameSport(name: String) = answer { it.copy(sportName = name.take(Sport.NAME_MAX_LENGTH)) }

    /** The days the sport is on, a bit per day. */
    fun setWeekdays(weekdays: Int) = answer { it.copy(weekdays = weekdayMask(weekdaysOf(weekdays))) }

    /** Ticks or unticks the bell of [kg]. */
    fun toggleBell(kg: Double) = answer { it.copy(bells = if (kg in it.bells) it.bells - kg else it.bells + kg) }

    /** How long a session is, one of [AppSettings.SESSION_MINUTES]. */
    fun setMinutes(minutes: Int) {
        if (minutes in AppSettings.SESSION_MINUTES) answer { it.copy(minutes = minutes) }
    }

    /**
     * Builds the week as [state] shows it; true if it did, false with nothing to build or once built. The plans
     * it made the last time go first, then the sport and the program's days are planned, the program taken up
     * unless one is followed, the bells ticked and unticked owned or not, and the goal and the session's length
     * kept.
     */
    fun build(): Boolean {
        val state = state.value
        if (built || !state.canBuild) return false
        built = true
        val week = state.week
        week.remove.forEach(planStore::removePlanned)
        week.sport?.let { planStore.planWeekly(PlanWorkout.SportDay(it), week.sportDays) }
        if (week.liftDays.isNotEmpty()) planStore.planWeekly(PlanWorkout.Lift.ProgramDay, week.liftDays)
        week.program?.takeIf { !week.keepsProgram }?.let { programStore.follow(it.key) }
        val owned = gearStore.bells.value.toSet()
        chips.filter { (it in owned) != (it in state.bells) }.forEach { gearStore.setOwned(it, it in state.bells) }
        settingsStore.updateAppSettings {
            it.copy(weeklyGoal = week.goal ?: it.weeklyGoal, sessionMinutes = state.minutes, setupOffered = true)
        }
        return true
    }

    private fun answer(change: (Answers) -> Answers) {
        val next = change(answers.value)
        answers.value = next
        savedState[KEY_SPORT] = next.sport
        savedState[KEY_NAME] = next.sportName
        savedState[KEY_DAYS] = next.weekdays
        savedState[KEY_BELLS] = next.bells.sorted().joinToString(BELL_SEPARATOR)
        savedState[KEY_MINUTES] = next.minutes
    }

    /** The answers as they were left, for a page brought back; null on its first opening. */
    private fun saved(): Answers? {
        val sport = savedState.get<Int>(KEY_SPORT) ?: return null
        return Answers(
            sport = sport,
            sportName = savedState.get<String>(KEY_NAME).orEmpty(),
            weekdays = savedState.get<Int>(KEY_DAYS) ?: 0,
            bells = savedState.get<String>(KEY_BELLS).orEmpty()
                .split(BELL_SEPARATOR)
                .mapNotNull { it.toDoubleOrNull() }
                .toSet(),
            minutes = savedState.get<Int>(KEY_MINUTES) ?: DEFAULT_SESSION_MINUTES,
        )
    }

    /** The answers as the app stands: what Set up built before, or was told elsewhere. */
    private fun standing(): Answers {
        val named = Sport.named.indexOf(was)
        val days = planStore.planned.value
            .filter { (it.workout as? PlanWorkout.SportDay)?.sport == was }
            .mapNotNull { (it.schedule as? PlanSchedule.Weekly)?.weekday }
        return Answers(
            sport = when {
                was == null -> SPORT_UNANSWERED
                named >= 0 -> named
                else -> SPORT_ANOTHER
            },
            sportName = (was as? Sport.Custom)?.name.orEmpty(),
            weekdays = if (was == null) 0 else weekdayMask(days.toSet()),
            bells = gearStore.bells.value.toSet(),
            minutes = settingsStore.appSettings.value.sessionMinutes ?: DEFAULT_SESSION_MINUTES,
        )
    }

    private fun uiState(
        answers: Answers,
        planned: List<PlannedWorkout>,
        following: Program?,
        restSeconds: Int,
    ): WeekSetupUiState {
        val state = WeekSetupUiState(
            sport = answers.sport,
            sportName = answers.sportName,
            weekdays = answers.weekdays,
            bellChoices = chips,
            bells = answers.bells,
            minutes = answers.minutes,
        )
        val asked = WeekAnswers(state.answeredSport, weekdaysOf(answers.weekdays), answers.bells, answers.minutes)
        val week = buildWeek(asked, planned, following, restSeconds, was)
        return state.copy(week = week, programMinutes = week.program?.dayMinutes(restSeconds))
    }
}

private const val BELL_SEPARATOR = ","

/** The answers as given, in the shapes they are saved in. */
private data class Answers(
    val sport: Int,
    val sportName: String,
    val weekdays: Int,
    val bells: Set<Double>,
    val minutes: Int,
)

/** The sport planned every week, the first of them when there are several; null with none. */
private fun List<PlannedWorkout>.weeklySport(): Sport? =
    firstNotNullOfOrNull { plan ->
        (plan.workout as? PlanWorkout.SportDay)?.sport.takeIf { plan.schedule is PlanSchedule.Weekly }
    }
