package dev.justinsmith.interval.export

import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.domain.Band
import dev.justinsmith.bells.domain.Block
import dev.justinsmith.bells.domain.CustomExercise
import dev.justinsmith.bells.domain.Followed
import dev.justinsmith.bells.domain.LiftSession
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.SetLog
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.CueSettings
import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.platform.AppInfo
import dev.justinsmith.interval.platform.ShareableFile
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.settings.DataExporter
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** The export file, from what both repositories hold right now, mapped and encoded on [computation]. */
class AppDataExporter(
    private val intervals: IntervalRepository,
    private val lift: BellsRepository,
    private val appInfo: AppInfo,
    private val clock: WallClock,
    private val formatter: TimestampFormatter,
    private val computation: CoroutineDispatcher = Dispatchers.Default,
) : DataExporter {
    // Defaults are written too: a reader must find the schema in the file, not assume it.
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }

    override suspend fun export(): ShareableFile {
        val now = clock.nowMillis()
        // Both the mapping and the encoding grow with the history, so neither runs on the caller's main thread.
        val content = withContext(computation) { json.encodeToString(bundle(now)) }
        return ShareableFile(ExportBundle.fileName(formatter.dayNumber(now)), ExportBundle.MIME_TYPE, content)
    }

    /** What the file will hold, as written at [now]. */
    fun bundle(now: Long): ExportBundle = ExportBundle(
        version = appInfo.version,
        exportedAtEpochMillis = now,
        settings = intervals.appSettings.value.export(intervals.cueSettings.value),
        timer = intervals.config.value.export(),
        presets = intervals.customPresets.value.map { it.export() },
        history = intervals.history.value.map { it.export() },
        plan = intervals.planned.value.map { it.export() },
        lift = LiftExport(
            bellsKg = lift.bells.value,
            barKg = lift.bar.value,
            restSeconds = lift.restSeconds.value,
            bands = lift.bands.value.map { it.export() },
            exercises = lift.customExercises.value.map { it.export() },
            programs = lift.customPrograms.value.map { it.export() },
            following = lift.followed.value?.export(),
            sessions = lift.sessions.value.mapNotNull { it.export() },
        ),
    )
}

private fun AppSettings.export(cues: CueSettings) = SettingsExport(
    theme = theme.name,
    keepScreenOn = keepScreenOn,
    weightKg = body.weightKg,
    weightUnit = body.unit.name,
    effort = body.effort.name,
    weeklyGoal = weeklyGoal,
    reminderMinuteOfDay = reminderMinuteOfDay,
    cues = CuesExport(cues.sound, cues.voice, cues.halfway, cues.vibrate),
)

private fun IntervalConfig.export() = ConfigExport(prepSeconds, workSeconds, restSeconds, rounds, activity.name)

private fun Preset.export() = PresetExport(id, name, config.export())

private fun HistoryEntry.export() =
    WorkoutExport(id, finishedAtEpochMillis, config.export(), elapsedSeconds, endedEarly, workActiveSeconds)

private fun PlannedWorkout.export() = PlannedExport(
    id = id,
    name = name,
    config = config.export(),
    weekday = (schedule as? PlanSchedule.Weekly)?.weekday,
    epochDay = (schedule as? PlanSchedule.Once)?.epochDay,
    presetId = presetId,
    createdAtEpochMillis = createdAtEpochMillis,
)

private fun Band.export() = BandExport(id, name, colour.name, position, archived)

private fun CustomExercise.export() =
    ExerciseExport(uid, name, equipment.name, measure.name, perSide, focus.name, cues, archived)

private fun Program.export() =
    ProgramExport(key.stored, name, daysPerWeek, weeks, days.map { day -> day.map { it.export() } })

private fun Block.export() =
    BlockExport(exercise.key, rounds, reps, kind.name, rungs, double, kg, band, range?.bottom, range?.top)

private fun Followed.export() = FollowedExport(program.key.stored, startedAtEpochMillis, daysBefore)

/** A finished session; one still in progress is not a record yet. */
private fun LiftSession.export(): SessionExport? {
    val finishedAt = finishedAtEpochMillis ?: return null
    return SessionExport(uid, startedAtEpochMillis, finishedAt, title, program?.stored, day, sets.map { it.export() })
}

private fun SetLog.export() = SetExport(
    id = id,
    exercise = exercise.key,
    equipment = equipment.name,
    bellKg = bellKg,
    reps = reps,
    side = side?.name,
    double = double,
    seconds = seconds,
    band = band?.id,
    loggedAtEpochMillis = loggedAtEpochMillis,
)
