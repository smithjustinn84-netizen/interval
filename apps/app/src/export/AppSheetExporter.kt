package dev.justinsmith.interval.export

import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.data.SheetExporter
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.platform.ShareableFile
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.sheet_average_bpm
import dev.justinsmith.interval.resources.sheet_date
import dev.justinsmith.interval.resources.sheet_effort
import dev.justinsmith.interval.resources.sheet_exercise
import dev.justinsmith.interval.resources.sheet_kg
import dev.justinsmith.interval.resources.sheet_kind
import dev.justinsmith.interval.resources.sheet_lb
import dev.justinsmith.interval.resources.sheet_left
import dev.justinsmith.interval.resources.sheet_minutes
import dev.justinsmith.interval.resources.sheet_name
import dev.justinsmith.interval.resources.sheet_note
import dev.justinsmith.interval.resources.sheet_peak_bpm
import dev.justinsmith.interval.resources.sheet_reps
import dev.justinsmith.interval.resources.sheet_right
import dev.justinsmith.interval.resources.sheet_seconds
import dev.justinsmith.interval.resources.sheet_session
import dev.justinsmith.interval.resources.sheet_set
import dev.justinsmith.interval.resources.sheet_shape
import dev.justinsmith.interval.resources.sheet_side
import dev.justinsmith.interval.resources.sheet_sport_day
import dev.justinsmith.interval.resources.sheet_time
import dev.justinsmith.interval.resources.sheet_unit
import dev.justinsmith.interval.resources.sheet_warm_up
import dev.justinsmith.interval.resources.sheet_warm_up_yes
import dev.justinsmith.interval.resources.sheet_weight
import dev.justinsmith.interval.resources.sheet_workout
import dev.justinsmith.interval.resources.sport_bjj
import dev.justinsmith.interval.resources.sport_climbing
import dev.justinsmith.interval.resources.sport_running
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.timer.nameOf
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.ui.liftNameOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString

/**
 * The log as a spreadsheet ([sheetRows]), from what both repositories hold right now: workouts named as the Log names
 * them, weights in the unit the app weighs in. Laid out and encoded on [computation].
 */
class AppSheetExporter(
    private val intervals: IntervalRepository,
    private val lift: LiftRepository,
    private val clock: WallClock,
    private val formatter: TimestampFormatter,
    private val computation: CoroutineDispatcher = Dispatchers.Default,
) : SheetExporter {
    override suspend fun export(): ShareableFile {
        val now = clock.nowMillis()
        val history = intervals.history.history.value
        val sessions = lift.sessions.sessions.value
        val words = ResourceSheetWords.load(
            lifts = sessions.flatMap { session -> session.sets.map { it.exercise to it.double } }.distinct(),
            shapes = history.map { it.config }.distinct(),
        )
        val presets = intervals.presets.presets.value
        val flows = intervals.flows.flows.value
        val unit = intervals.settings.appSettings.value.body.unit
        val content = withContext(computation) {
            val rows = sheetRows(
                history,
                intervals.history.sportDays.value,
                sessions,
                nameOf = { presets.nameOf(it, flows) },
                unit,
                words,
                formatter,
            )
            // The byte order mark tells a spreadsheet the file is UTF-8, so "×" and a name's accents read right.
            BYTE_ORDER_MARK + csvOf(words.header, rows)
        }
        return ShareableFile(sheetFileName(formatter.dayNumber(now)), SHEET_MIME_TYPE, content)
    }
}

private const val BYTE_ORDER_MARK = ""

/** [SheetWords] from the app's strings, every name the sheet needs looked up before it is laid out. */
private class ResourceSheetWords(
    override val header: List<String>,
    override val workout: String,
    override val sportDay: String,
    override val session: String,
    override val set: String,
    override val warmUp: String,
    private val sides: Map<Side, String>,
    private val sports: Map<Sport, String>,
    private val lifts: Map<Pair<String, Boolean>, String>,
    private val shapes: Map<IntervalConfig, String>,
    private val units: Map<WeightUnit, String>,
) : SheetWords {
    override fun side(side: Side): String = sides.getValue(side)

    override fun sport(sport: Sport): String = (sport as? Sport.Custom)?.name ?: sports.getValue(sport)

    override fun lift(exercise: Exercise, double: Boolean): String = lifts[exercise.key to double].orEmpty()

    override fun shape(config: IntervalConfig): String = shapes[config].orEmpty()

    override fun unit(unit: WeightUnit): String = units.getValue(unit)

    companion object {
        suspend fun load(lifts: List<Pair<Exercise, Boolean>>, shapes: List<IntervalConfig>) = ResourceSheetWords(
            header = listOf(
                Res.string.sheet_date, Res.string.sheet_time, Res.string.sheet_kind, Res.string.sheet_name,
                Res.string.sheet_exercise, Res.string.sheet_side, Res.string.sheet_weight, Res.string.sheet_unit,
                Res.string.sheet_reps, Res.string.sheet_seconds, Res.string.sheet_warm_up, Res.string.sheet_minutes,
                Res.string.sheet_effort, Res.string.sheet_note, Res.string.sheet_average_bpm, Res.string.sheet_peak_bpm,
            ).map { getString(it) },
            workout = getString(Res.string.sheet_workout),
            sportDay = getString(Res.string.sheet_sport_day),
            session = getString(Res.string.sheet_session),
            set = getString(Res.string.sheet_set),
            warmUp = getString(Res.string.sheet_warm_up_yes),
            sides = mapOf(
                Side.Left to getString(Res.string.sheet_left),
                Side.Right to getString(Res.string.sheet_right),
            ),
            sports = mapOf(
                Sport.Bjj to getString(Res.string.sport_bjj),
                Sport.Climbing to getString(Res.string.sport_climbing),
                Sport.Running to getString(Res.string.sport_running),
            ),
            lifts = lifts.associate { (exercise, double) -> (exercise.key to double) to liftNameOf(exercise, double) },
            shapes = shapes.associateWith { config ->
                getString(
                    Res.string.sheet_shape,
                    config.rounds,
                    formatClock(config.workSeconds),
                    formatClock(config.restSeconds),
                )
            },
            units = mapOf(
                WeightUnit.Kg to getString(Res.string.sheet_kg),
                WeightUnit.Lb to getString(Res.string.sheet_lb),
            ),
        )
    }
}
