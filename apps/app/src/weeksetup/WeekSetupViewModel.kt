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
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.ui.weekdayMask
import dev.justinsmith.interval.ui.weekdaysOf
import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.data.LiftActivity
import dev.justinsmith.lift.data.ProgramStore
import dev.justinsmith.lift.domain.DEFAULT_BAR_KG
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.bars
import dev.justinsmith.lift.domain.dayMinutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** The first question unanswered: no sport picked, nor "Nothing else". */
const val SPORT_UNANSWERED = -1

/** The last pick: no sport beside strength and conditioning. */
val SPORT_NONE = Sport.OTHER + 1

/** The bell sizes Set up offers, in kg; the gear page has every size. */
val SETUP_BELLS = listOf(8.0, 12.0, 16.0, 20.0, 24.0, 32.0)

/** The session's length Set up opens on. */
const val DEFAULT_SESSION_MINUTES = 15

/**
 * Set up as it stands: the answers and the [week] they build. [sport] is a place among [Sport.named],
 * [Sport.OTHER] (named by [sportName]), [SPORT_NONE] or [SPORT_UNANSWERED]; [weekdays] a bit per day, Monday
 * bit 0; [equipment] what the user trains with; [bellChoices] the sizes offered, the [bells] owned among them;
 * [barKg] the empty bar weight when [SetupEquipment.Barbell] is picked; [programMinutes] about how long the
 * week's program takes a day.
 */
data class WeekSetupUiState(
    val sport: Int = SPORT_UNANSWERED,
    val sportName: String = "",
    val weekdays: Int = 0,
    val equipment: Set<SetupEquipment> = emptySet(),
    val bellChoices: List<Double> = SETUP_BELLS,
    val bells: Set<Double> = emptySet(),
    val pairs: Set<Double> = emptySet(),
    val barKg: Double = DEFAULT_BAR_KG,
    val unit: WeightUnit = WeightUnit.Kg,
    val minutes: Int = DEFAULT_SESSION_MINUTES,
    val week: BuiltWeek = BuiltWeek(),
    val programMinutes: IntRange? = null,
) {
    /** The sport answered, as far as it is: null for none, nothing else, or one not yet named. */
    val answeredSport: Sport? get() = Sport.picked(sport, sportName)

    /** Whether a sport is picked, so its days are asked for. */
    val asksDays: Boolean get() = sport in 0..Sport.OTHER

    /** Whether kettlebells are selected in [equipment]. */
    val hasKettlebells: Boolean get() = SetupEquipment.Kettlebells in equipment

    /** Whether a barbell is selected in [equipment]. */
    val hasBarbell: Boolean get() = SetupEquipment.Barbell in equipment

    /** Whether conditioning-only is selected in [equipment]. */
    val isConditioningOnly: Boolean get() = SetupEquipment.ConditioningOnly in equipment

    /** Whether nothing is answered yet that a week could be made of. */
    val unanswered: Boolean
        get() = sport == SPORT_UNANSWERED && equipment.isEmpty() && bells.isEmpty() && week.program == null

    /** Whether the answers come to anything to build. */
    val canBuild: Boolean
        get() = week.sport != null || week.program != null || week.conditioningDays.isNotEmpty() ||
            bells.isNotEmpty() || hasBarbell
}

private const val KEY_SPORT = "setup_sport"
private const val KEY_NAME = "setup_sport_name"
private const val KEY_DAYS = "setup_days"
private const val KEY_EQUIPMENT = "setup_equipment"
private const val KEY_BELLS = "setup_bells"
private const val KEY_PAIRS = "setup_pairs"
private const val KEY_BAR = "setup_bar"
private const val KEY_UNIT = "setup_unit"
private const val KEY_MINUTES = "setup_minutes"

/**
 * Set up: what else you train, on which days, what equipment you train with and how long a session is, shown as
 * the week they would build, then built. It opens on what stands, so it is also where they are changed. The
 * answers are kept in [savedState] as they are given. Building writes to both sides: the plan, the settings, the
 * gear and the program followed, which it never starts again.
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

    /** Whether the user already had a bar when Set up opened, so unticking Barbell clears it. */
    private val hadBar: Boolean = gearStore.bar.value != null

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

    /** Answers the first question: a place among [Sport.named], [Sport.OTHER] or [SPORT_NONE]. */
    fun pickSport(sport: Int) = answer { it.copy(sport = sport.coerceIn(0, SPORT_NONE)) }

    /** Names the sport of [Sport.OTHER]. */
    fun nameSport(name: String) = answer { it.copy(sportName = name.take(Sport.NAME_MAX_LENGTH)) }

    /** The days the sport is on, a bit per day. */
    fun setWeekdays(weekdays: Int) = answer { it.copy(weekdays = weekdayMask(weekdaysOf(weekdays))) }

    /**
     * Toggles [item] among the user's equipment. [SetupEquipment.ConditioningOnly] is exclusive with strength
     * equipment chips: selecting it clears strength gear; selecting any strength gear clears conditioning-only.
     */
    fun toggleEquipment(item: SetupEquipment) = answer { current ->
        when {
            item == SetupEquipment.ConditioningOnly -> if (item in current.equipment) {
                current.copy(equipment = emptySet())
            } else {
                current.copy(equipment = setOf(item), bells = emptySet(), pairs = emptySet())
            }

            item in current.equipment -> {
                val next = current.equipment - item
                if (item == SetupEquipment.Kettlebells) {
                    current.copy(equipment = next, bells = emptySet(), pairs = emptySet())
                } else {
                    current.copy(equipment = next)
                }
            }

            else -> current.copy(equipment = (current.equipment - SetupEquipment.ConditioningOnly) + item)
        }
    }

    /** Ticks or unticks the bell of [kg]; one unticked is no longer a pair either. */
    fun toggleBell(kg: Double) = answer {
        if (kg in it.bells) {
            it.copy(bells = it.bells - kg, pairs = it.pairs - kg)
        } else {
            it.copy(
                equipment = (it.equipment - SetupEquipment.ConditioningOnly) + SetupEquipment.Kettlebells,
                bells = it.bells + kg,
            )
        }
    }

    /** Ticks or unticks owning two of the bell of [kg], one of those owned. */
    fun togglePair(kg: Double) = answer {
        when {
            kg in it.pairs -> it.copy(pairs = it.pairs - kg)
            kg in it.bells -> it.copy(pairs = it.pairs + kg)
            else -> it
        }
    }

    /** Sets the empty barbell weight when [SetupEquipment.Barbell] is picked. */
    fun setBar(kg: Double) {
        if (kg in bars) {
            answer {
                it.copy(
                    equipment = (it.equipment - SetupEquipment.ConditioningOnly) + SetupEquipment.Barbell,
                    barKg = kg,
                )
            }
        }
    }

    /** Switches the weight unit shown for bells/barbell (and saved for the app on build). */
    fun setUnit(unit: WeightUnit) = answer { it.copy(unit = unit) }

    /** How long a session is, one of [AppSettings.SESSION_MINUTES]. */
    fun setMinutes(minutes: Int) {
        if (minutes in AppSettings.SESSION_MINUTES) answer { it.copy(minutes = minutes) }
    }

    /**
     * Builds the week as [state] shows it; true if it did, false with nothing to build or once built.
     */
    fun build(): Boolean {
        val state = state.value
        if (built || !state.canBuild) return false
        built = true
        val week = state.week
        week.remove.forEach(planStore::removePlanned)
        week.sport?.let { planStore.planWeekly(PlanWorkout.SportDay(it), week.sportDays) }
        if (week.liftDays.isNotEmpty()) planStore.planWeekly(PlanWorkout.Lift.ProgramDay, week.liftDays)
        week.conditioning?.takeIf { week.conditioningDays.isNotEmpty() }?.let { preset ->
            planStore.planWeekly(preset.asPlan(), week.conditioningDays)
        }
        week.program?.takeIf { !week.keepsProgram }?.let { programStore.follow(it.key) }
        syncGear(state)
        settingsStore.updateAppSettings {
            it.copy(
                weeklyGoal = week.goal ?: it.weeklyGoal,
                sessionMinutes = state.minutes,
                setupOffered = true,
                body = it.body.copy(unit = state.unit),
            )
        }
        return true
    }

    private fun syncGear(state: WeekSetupUiState) {
        val effectiveBells = if (state.hasKettlebells) state.bells else emptySet()
        val effectivePairs = if (state.hasKettlebells) state.pairs else emptySet()
        val owned = gearStore.bells.value.toSet()
        chips.filter { (it in owned) != (it in effectiveBells) }
            .forEach { gearStore.setOwned(it, it in effectiveBells) }
        val pairs = gearStore.pairs.value
        effectiveBells.filter { (it in pairs) != (it in effectivePairs) }
            .forEach { gearStore.setPair(it, it in effectivePairs) }
        if (state.hasBarbell) {
            if (gearStore.bar.value != state.barKg) gearStore.setBar(state.barKg)
        } else if (hadBar) {
            gearStore.setBar(null)
        }
    }

    private fun answer(change: (Answers) -> Answers) {
        val next = change(answers.value)
        answers.value = next
        savedState[KEY_SPORT] = next.sport
        savedState[KEY_NAME] = next.sportName
        savedState[KEY_DAYS] = next.weekdays
        savedState[KEY_EQUIPMENT] = next.equipment.joinToString(ITEM_SEPARATOR) { it.name }
        savedState[KEY_BELLS] = next.bells.sorted().joinToString(ITEM_SEPARATOR)
        savedState[KEY_PAIRS] = next.pairs.sorted().joinToString(ITEM_SEPARATOR)
        savedState[KEY_BAR] = next.barKg
        savedState[KEY_UNIT] = next.unit.name
        savedState[KEY_MINUTES] = next.minutes
    }

    /** The answers as they were left, for a page brought back; null on its first opening. */
    private fun saved(): Answers? {
        val sport = savedState.get<Int>(KEY_SPORT) ?: return null
        val savedUnit = savedState.get<String>(KEY_UNIT)
            ?.let { name -> WeightUnit.entries.firstOrNull { it.name == name } }
            ?: settingsStore.appSettings.value.body.unit
        val savedEquipment = savedState.get<String>(KEY_EQUIPMENT).equipmentSaved()
        return Answers(
            sport = sport,
            sportName = savedState.get<String>(KEY_NAME).orEmpty(),
            weekdays = savedState.get<Int>(KEY_DAYS) ?: 0,
            equipment = savedEquipment,
            bells = savedState.get<String>(KEY_BELLS).bellsSaved(),
            pairs = savedState.get<String>(KEY_PAIRS).bellsSaved(),
            barKg = savedState.get<Double>(KEY_BAR) ?: DEFAULT_BAR_KG,
            unit = savedUnit,
            minutes = savedState.get<Int>(KEY_MINUTES) ?: DEFAULT_SESSION_MINUTES,
        )
    }

    /** The answers as the app stands: what Set up built before, or was told elsewhere. */
    private fun standing(): Answers {
        val named = Sport.named.indexOf(was)
        val days = planStore.planned.value
            .filter { (it.workout as? PlanWorkout.SportDay)?.sport == was }
            .mapNotNull { (it.schedule as? PlanSchedule.Weekly)?.weekday }
        val ownedBells = gearStore.bells.value.toSet()
        val ownedBar = gearStore.bar.value
        val followed = programStore.following.value
        val hasWeeklyInterval = planStore.planned.value.any {
            it.schedule is PlanSchedule.Weekly && it.workout is PlanWorkout.Interval
        }
        val equipment = buildSet {
            if (ownedBells.isNotEmpty()) add(SetupEquipment.Kettlebells)
            if (ownedBar != null) add(SetupEquipment.Barbell)
            if (followed != null) {
                val blocks = followed.days.flatten()
                if (blocks.any { it.exercise.equipment == Equipment.Band }) add(SetupEquipment.Bands)
                when ((followed.key as? ProgramKey.BuiltIn)?.id) {
                    ProgramId.BodyweightBasics -> add(SetupEquipment.PullUpBar)
                    ProgramId.BodyweightFloor -> add(SetupEquipment.Bodyweight)
                    else -> Unit
                }
            }
            if (isEmpty() && followed == null && hasWeeklyInterval) {
                add(SetupEquipment.ConditioningOnly)
            }
        }
        return Answers(
            sport = when {
                was == null -> SPORT_UNANSWERED
                named >= 0 -> named
                else -> Sport.OTHER
            },
            sportName = (was as? Sport.Custom)?.name.orEmpty(),
            weekdays = if (was == null) 0 else weekdayMask(days.toSet()),
            equipment = equipment,
            bells = ownedBells,
            pairs = gearStore.pairs.value intersect ownedBells,
            barKg = ownedBar ?: DEFAULT_BAR_KG,
            unit = settingsStore.appSettings.value.body.unit,
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
            equipment = answers.equipment,
            bellChoices = chips,
            bells = answers.bells,
            pairs = answers.pairs,
            barKg = answers.barKg,
            unit = answers.unit,
            minutes = answers.minutes,
        )
        val asked = WeekAnswers(
            sport = state.answeredSport,
            sportDays = weekdaysOf(answers.weekdays),
            bells = answers.bells,
            minutes = answers.minutes,
            pairs = answers.pairs,
            equipment = answers.equipment,
        )
        val week = buildWeek(asked, planned, following, restSeconds, was)
        return state.copy(week = week, programMinutes = week.program?.dayMinutes(restSeconds))
    }
}

private const val ITEM_SEPARATOR = ","

/** Bells saved as their kg joined by [ITEM_SEPARATOR]. */
private fun String?.bellsSaved(): Set<Double> =
    orEmpty().split(ITEM_SEPARATOR).mapNotNull { it.toDoubleOrNull() }.toSet()

private fun String?.equipmentSaved(): Set<SetupEquipment> =
    orEmpty().split(ITEM_SEPARATOR)
        .mapNotNull { name -> SetupEquipment.entries.firstOrNull { it.name == name } }
        .toSet()

/** The answers as given, in the shapes they are saved in. */
private data class Answers(
    val sport: Int,
    val sportName: String,
    val weekdays: Int,
    val equipment: Set<SetupEquipment>,
    val bells: Set<Double>,
    val minutes: Int,
    val pairs: Set<Double> = emptySet(),
    val barKg: Double = DEFAULT_BAR_KG,
    val unit: WeightUnit = WeightUnit.Kg,
)

/** The sport planned every week, the first of them when there are several; null with none. */
private fun List<PlannedWorkout>.weeklySport(): Sport? =
    firstNotNullOfOrNull { plan ->
        (plan.workout as? PlanWorkout.SportDay)?.sport.takeIf { plan.schedule is PlanSchedule.Weekly }
    }
