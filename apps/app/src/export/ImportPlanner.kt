package dev.justinsmith.interval.export

import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.BodySettings
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.model.SnatchCount
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.settings.ImportCounts
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.timer.toKg
import dev.justinsmith.lift.data.LiftImport
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.BandColour
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.BlockKind
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Focus
import dev.justinsmith.lift.domain.Followed
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.RepRange
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.UnknownExercise
import dev.justinsmith.lift.domain.bandOf
import dev.justinsmith.lift.domain.builtInPrograms
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import dev.justinsmith.interval.timer.Block as IntervalBlock
import dev.justinsmith.lift.domain.PerceivedEffort as LiftEffort

/** What both sides hold now, as an import is weighed against it. */
data class Holdings(
    val history: List<HistoryEntry> = emptyList(),
    /** Every preset, the built-ins too: a file's preset of a name already here is not added. */
    val presets: List<Preset> = emptyList(),
    /** Every flow, the shipped ones too: a file's flow of a name already here is not added. */
    val flows: List<Flow> = emptyList(),
    val planned: List<PlannedWorkout> = emptyList(),
    val sportDays: List<LoggedSport> = emptyList(),
    /** Lift's finished sessions. */
    val sessions: List<LiftSession> = emptyList(),
    val exercises: List<CustomExercise> = emptyList(),
    val programs: List<Program> = emptyList(),
    /** Every band, the ones every user starts with too. */
    val bands: List<Band> = emptyList(),
    val bellsKg: List<Double> = emptyList(),
    val barKg: Double? = null,
    val followed: Followed? = null,
)

/** The settings a file carries, for "Also settings": the app's, the cues, the Timer's and Lift's rest. */
data class ImportedSettings(
    val app: AppSettings,
    val cues: CueSettings,
    val timer: IntervalConfig,
    val restSeconds: Int,
)

/**
 * What importing a file comes to, worked out before anything is written: what each side adds, its [settings],
 * and the counts the review sheet shows, [added] and [alreadyHere]. Importing it writes exactly this.
 */
data class ImportPlan(
    val exportedAtEpochMillis: Long,
    val version: String?,
    val history: List<HistoryEntry>,
    val presets: List<Preset>,
    val planned: List<PlannedWorkout>,
    /** The sport days logged, which the sheet counts among the workouts. */
    val sportDays: List<LoggedSport>,
    val lift: LiftImport,
    /** The flows the user built, of names not here yet. */
    val flows: List<Flow> = emptyList(),
    val settings: ImportedSettings,
    val added: ImportCounts,
    val alreadyHere: Int,
)

/** A file as the importer reads it. */
sealed interface ImportReading {
    data class Ready(val plan: ImportPlan) : ImportReading

    /** Written by a newer build, [version] when it says, in a shape this one can't be sure of. */
    data class TooNew(val version: String?) : ImportReading

    /** Not an export of this app's, or a damaged one. */
    data object Unreadable : ImportReading
}

private val json = Json { ignoreUnknownKeys = true }

/**
 * Reads an export file ([content]) and weighs it against [holdings]: pure, so the sheet's counts and the import
 * come from one plan. The schema is checked before the rest is decoded, so a newer file says so rather than
 * failing as damaged. Every value goes through the domain's own ranges; one out of them makes the file
 * unreadable, as nothing in a file this app wrote could be. Nothing is matched by the file's ids, which are
 * the other phone's: workouts by when and what they were, sport days by day and sport, presets by name, plans by
 * day and what they hold,
 * Lift's sessions, exercises and programs by uid, bands by id, bells by weight.
 */
fun readImport(content: String, holdings: Holdings): ImportReading {
    val tree = runCatching { json.parseToJsonElement(content) as? JsonObject }.getOrNull()
        ?: return ImportReading.Unreadable
    if (tree["app"]?.jsonPrimitive?.content != ExportBundle.APP) return ImportReading.Unreadable
    val schema = runCatching { tree["schema"]?.jsonPrimitive?.intOrNull }.getOrNull() ?: return ImportReading.Unreadable
    if (schema > ExportBundle.SCHEMA) {
        return ImportReading.TooNew(runCatching { tree["version"]?.jsonPrimitive?.content }.getOrNull())
    }
    if (schema < 1) return ImportReading.Unreadable
    return runCatching {
        ImportReading.Ready(plan(json.decodeFromJsonElement(ExportBundle.serializer(), tree), holdings))
    }
        .getOrElse { ImportReading.Unreadable }
}

private fun plan(bundle: ExportBundle, holdings: Holdings): ImportPlan {
    val history = bundle.history.map { it.toDomain() }
    val presets = bundle.presets.map { it.toDomain() }
    val planned = bundle.plan.map { it.toDomain(holdings.presets) }
    val sportDays = bundle.sportDays.map { it.toDomain() }
    val lift = LiftPlan(bundle.lift, holdings)

    val historyHere = holdings.history.map { it.key }.toMutableSet()
    val newHistory = history.filter { historyHere.add(it.key) }
    val presetNames = holdings.presets.map { it.name }.toMutableSet()
    val newPresets = presets.filter { presetNames.add(it.name) }
    val plansHere = holdings.planned.map { it.key }.toMutableSet()
    val newPlans = planned.filter { plansHere.add(it.key) }
    val sportsHere = holdings.sportDays.map { it.key }.toMutableSet()
    val newSports = sportDays.filter { sportsHere.add(it.key) }
    val flows = bundle.flows.map { it.toDomain() }
    val flowNames = holdings.flows.map { it.name }.toMutableSet()
    val newFlows = flows.filter { flowNames.add(it.name) }

    val added = ImportCounts(
        workouts = newHistory.size + newSports.size,
        presets = newPresets.size,
        plans = newPlans.size,
        sessions = lift.sessions.size,
        exercises = lift.exercises.size,
        programs = lift.programs.size,
        flows = newFlows.size,
    )
    val inFile = history.size + sportDays.size + presets.size + planned.size + bundle.lift.sessions.size +
        bundle.lift.exercises.size + lift.customProgramsInFile + flows.size
    return ImportPlan(
        exportedAtEpochMillis = bundle.exportedAtEpochMillis,
        version = bundle.version,
        history = newHistory,
        presets = newPresets,
        planned = newPlans,
        sportDays = newSports,
        lift = lift.import,
        flows = newFlows,
        settings = ImportedSettings(
            bundle.settings.toDomain(),
            bundle.settings.cues.let { CueSettings(it.sound, it.voice, it.halfway, it.vibrate) },
            bundle.timer.toDomain(),
            bundle.lift.restSeconds.also {
                require(it in RestClock.MIN_SECONDS..RestClock.MAX_SECONDS) { "rest" }
            },
        ),
        added = added,
        alreadyHere = (inFile - added.total).coerceAtLeast(0),
    )
}

/** Lift's side of a file, matched against [holdings]: only what is not here yet. */
private class LiftPlan(file: LiftExport, holdings: Holdings) {
    private val fileExercises = file.exercises.map { it.toDomain() }
    private val exerciseUids = holdings.exercises.map { it.uid }.toSet()
    val exercises = fileExercises.filter { it.uid !in exerciseUids }.distinctBy { it.uid }

    /** Every exercise a set or block may name: those here and those coming. */
    private val known = holdings.exercises + exercises

    private val bandIds = holdings.bands.map { it.id }.toSet()
    private val fileBands = file.bands.map { it.toDomain() }
    private val bands = fileBands.filter { it.id !in bandIds }.distinctBy { it.id }
    private val allBands = holdings.bands + bands

    private val filePrograms = file.programs.mapNotNull { it.toDomain(known, allBands) }
    val customProgramsInFile = filePrograms.size
    private val programKeys = holdings.programs.map { it.key }.toSet()
    val programs = filePrograms.filter { it.key !in programKeys }.distinctBy { it.key }

    private val sessionUids = holdings.sessions.map { it.uid }.toSet()
    val sessions = file.sessions.map {
        it.toDomain(known, allBands)
    }.filter { it.uid !in sessionUids }.distinctBy { it.uid }

    private val following: Followed? = file.following?.takeIf { holdings.followed == null }?.let { followed ->
        val key = ProgramKey.parse(followed.program)
        val program = (builtInPrograms + holdings.programs + programs).firstOrNull { it.key == key }
        program?.let { Followed(it, followed.startedAtEpochMillis, followed.daysBefore) }
    }

    val import = LiftImport(
        sessions = sessions,
        exercises = exercises,
        programs = programs,
        bands = bands,
        bellsKg = file.bellsKg.filter { it > 0 && it !in holdings.bellsKg }.distinct(),
        pairsKg = file.pairsKg.toSet(),
        barKg = file.barKg?.takeIf { holdings.barKg == null && it > 0 },
        following = following,
    )
}

/** What makes two workouts the same one: when it finished and what it was. */
private val HistoryEntry.key get() = listOf(finishedAtEpochMillis, config, elapsedSeconds, endedEarly)

/** What makes two plans the same one: the day and what it holds, an interval plan by its name and shape. */
private val PlannedWorkout.key: Any
    get() = schedule to when (val held = workout) {
        is PlanWorkout.Interval -> held.name to held.config
        else -> held
    }

private fun ConfigExport.toDomain(): IntervalConfig {
    require(prepSeconds in IntervalConfig.PREP_RANGE) { "prep" }
    require(workSeconds in IntervalConfig.WORK_RANGE) { "work" }
    require(restSeconds in IntervalConfig.REST_RANGE) { "rest" }
    require(rounds in IntervalConfig.ROUNDS_RANGE) { "rounds" }
    require(warmUpSeconds in IntervalConfig.WARM_UP_RANGE) { "warm-up" }
    require(coolDownSeconds in IntervalConfig.COOL_DOWN_RANGE) { "cool-down" }
    require(sets in IntervalConfig.SETS_RANGE) { "sets" }
    require(setRestSeconds in IntervalConfig.SET_REST_RANGE) { "set rest" }
    require(workStepSeconds in IntervalConfig.WORK_STEP_RANGE) { "ladder" }
    require(workSeconds + (rounds - 1) * workStepSeconds in IntervalConfig.WORK_RANGE) { "ladder's last round" }
    require(then.size < IntervalConfig.MAX_BLOCKS) { "blocks" }
    require(blockRestSeconds in IntervalConfig.BLOCK_REST_RANGE) { "block rest" }
    return IntervalConfig(
        prepSeconds = prepSeconds,
        workSeconds = workSeconds,
        restSeconds = restSeconds,
        rounds = rounds,
        warmUpSeconds = warmUpSeconds,
        coolDownSeconds = coolDownSeconds,
        activity = Activity.named(activity),
        sets = sets,
        setRestSeconds = setRestSeconds,
        workStepSeconds = workStepSeconds,
        // Each block refuses a value out of its range, as the fields above do.
        then = then.map {
            IntervalBlock(it.workSeconds, it.restSeconds, it.rounds, it.sets, it.setRestSeconds, it.workStepSeconds)
        },
        blockRestSeconds = blockRestSeconds,
    )
}

private fun PresetExport.toDomain(): Preset {
    require(name.isNotBlank()) { "name" }
    return Preset(name, config.toDomain(), alternateHands = alternateHands)
}

private fun WorkoutExport.toDomain(): HistoryEntry {
    require(elapsedSeconds >= 0 && (workActiveSeconds ?: 0) >= 0) { "time" }
    requireHeartRate(averageBpm, peakBpm)
    val snatches = SnatchCount.of(snatchesPerSet, snatchBellKg)
    require((snatchesPerSet == null && snatchBellKg == null) || snatches != null) { "snatches" }
    return HistoryEntry(
        finishedAtEpochMillis,
        config.toDomain(),
        elapsedSeconds,
        endedEarly,
        workActiveSeconds,
        effort = PerceivedEffort.of(effort),
        note = note?.take(PerceivedEffort.NOTE_MAX_LENGTH)?.ifBlank { null },
        averageBpm = averageBpm,
        peakBpm = peakBpm,
        snatches = snatches,
    )
}

/** A heart rate is both figures or neither, each one a heart could beat at. */
private fun requireHeartRate(averageBpm: Int?, peakBpm: Int?) {
    require((averageBpm == null) == (peakBpm == null)) { "heart rate" }
    if (averageBpm != null && peakBpm != null) {
        require(averageBpm in HeartRate.BPM_RANGE && peakBpm in HeartRate.BPM_RANGE) { "heart rate" }
    }
}

/** A plan, linked to the user's own preset of its name when one is here (one coming is linked as it lands). */
private fun PlannedExport.toDomain(presets: List<Preset>): PlannedWorkout {
    val schedule = when {
        weekday != null -> PlanSchedule.Weekly(weekday.also { require(it in WEEKDAYS) { "weekday" } })
        epochDay != null -> PlanSchedule.Once(epochDay)
        else -> throw IllegalArgumentException("schedule")
    }
    val workout = when {
        sport != null -> PlanWorkout.SportDay(sportNamed(sport, name))

        lift == null -> {
            val name = requireNotNull(name) { "name" }
            PlanWorkout.Interval(
                name,
                requireNotNull(config) { "config" }.toDomain(),
                presets.firstOrNull {
                    it.name ==
                        name
                }?.id,
            )
        }

        lift == "ProgramDay" -> PlanWorkout.Lift.ProgramDay

        lift == "AnySession" -> PlanWorkout.Lift.AnySession

        else -> throw IllegalArgumentException("lift")
    }
    return PlannedWorkout(0, workout, schedule, createdAtEpochMillis)
}

private fun SettingsExport.toDomain(): AppSettings {
    val unit = enumNamed<WeightUnit>(weightUnit)
    weightKg?.let { require(it in unit.toKg(unit.range.first)..unit.toKg(unit.range.last)) { "weight" } }
    weeklyGoal?.let { require(it in AppSettings.WEEKLY_GOAL_RANGE) { "goal" } }
    reminderMinuteOfDay?.let { require(it in AppSettings.REMINDER_RANGE) { "reminder" } }
    sessionMinutes?.let { require(it in AppSettings.SESSION_MINUTES) { "session" } }
    return AppSettings(
        theme = enumNamed<ThemeMode>(theme),
        keepScreenOn = keepScreenOn,
        body = BodySettings(weightKg, unit, enumNamed<Effort>(effort)),
        weeklyGoal = weeklyGoal,
        reminderMinuteOfDay = reminderMinuteOfDay,
        sessionMinutes = sessionMinutes,
    )
}

private fun ExerciseExport.toDomain(): CustomExercise {
    require(uid.isNotBlank() && name.isNotBlank()) { "exercise" }
    return CustomExercise(
        uid,
        name,
        enumNamed<Equipment>(equipment),
        enumNamed<Measure>(measure),
        perSide,
        enumNamed<Focus>(focus),
        cues,
        archived,
    )
}

private fun BandExport.toDomain() =
    Band(id, name, BandColour.entries.firstOrNull { it.name == colour } ?: BandColour.Grey, position, archived)

/** One of the user's own programs; a built-in needs nothing imported, so null. */
private fun ProgramExport.toDomain(exercises: List<CustomExercise>, bands: List<Band>): Program? {
    val key = ProgramKey.parse(key) as? ProgramKey.Custom ?: return null
    return Program(key, days.map { day -> day.map { it.toDomain(exercises, bands) } }, daysPerWeek, weeks, name)
}

/**
 * A block, its load as its lift takes it: a band among [bands] by id, one neither here nor in the file by its
 * id alone.
 */
private fun BlockExport.toDomain(exercises: List<CustomExercise>, bands: List<Band>): Block {
    require(rounds > 0 && reps >= 0) { "block" }
    val lift = Exercise.parse(exercise, exercises) ?: UnknownExercise(exercise, Equipment.Bodyweight, Measure.Reps)
    return Block(
        exercise = lift,
        rounds = rounds,
        reps = reps,
        kind = enumNamed<BlockKind>(kind),
        rungs = rungs,
        double = double,
        load = Load.named(lift.equipment, kg, double, band?.let { bandOf(it, bands) }),
        range = if (rangeBottom != null && rangeTop != null) RepRange(rangeBottom, rangeTop) else null,
        rest = rest?.takeIf { it in RestClock.MIN_SECONDS..RestClock.MAX_SECONDS },
        origin = origin?.toDomain(exercises, bands)?.copy(origin = null),
    )
}

private fun SessionExport.toDomain(exercises: List<CustomExercise>, bands: List<Band>): LiftSession {
    require(uid.isNotBlank() && finishedAtEpochMillis >= startedAtEpochMillis) { "session" }
    requireHeartRate(averageBpm, peakBpm)
    return LiftSession(
        uid = uid,
        startedAtEpochMillis = startedAtEpochMillis,
        finishedAtEpochMillis = finishedAtEpochMillis,
        title = title,
        sets = sets.map { it.toDomain(exercises, bands) },
        program = program?.let(ProgramKey::parse),
        day = day,
        effort = LiftEffort.of(effort),
        note = note?.take(LiftEffort.NOTE_MAX_LENGTH)?.ifBlank { null },
        averageBpm = averageBpm,
        peakBpm = peakBpm,
    )
}

/** A set; one of an exercise neither here nor in the file keeps its key, as a set of one gone does. */
private fun SetExport.toDomain(exercises: List<CustomExercise>, bands: List<Band>): SetLog {
    require(reps >= 0 && bellKg >= 0) { "set" }
    val equipment = enumNamed<Equipment>(equipment)
    val measure = if (seconds != null) Measure.Time else Measure.Reps
    return SetLog.of(
        id = 0,
        exercise = Exercise.parse(exercise, exercises) ?: UnknownExercise(exercise, equipment, measure),
        kg = bellKg,
        reps = reps,
        side = side?.let { enumNamed<Side>(it) },
        loggedAtEpochMillis = loggedAtEpochMillis,
        double = double,
        equipment = equipment,
        seconds = seconds,
        band = band?.let { id -> bands.firstOrNull { it.id == id } ?: Band(id, "", BandColour.Grey, 0) },
    )
}

/** The entry of [E] named [name]; a name this build doesn't know makes the file unreadable. */
private inline fun <reified E : Enum<E>> enumNamed(name: String): E =
    requireNotNull(enumValues<E>().firstOrNull { it.name == name }) { "unknown ${E::class.simpleName} $name" }

private val WEEKDAYS = 1..7
