package dev.justinsmith.interval.export

import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.LoggedSport
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.timer.dateOfEpochDay
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.displayWeight
import dev.justinsmith.lift.domain.formatWeightNumber
import kotlin.math.roundToInt

// The log as a spreadsheet: a row for each interval workout, sport day, Lift session and set, oldest first, in words
// and in the user's unit, for a spreadsheet or a coach. It reads nothing back: the export file is what restores.

/** The words a spreadsheet is written in, looked up before its rows are laid out. */
interface SheetWords {
    /** The columns' names, one for each of [SheetRow]'s cells in order. */
    val header: List<String>
    val workout: String
    val sportDay: String
    val session: String
    val set: String
    val warmUp: String
    fun side(side: Side): String
    fun sport(sport: Sport): String

    /** A lift's name, with two bells said when [double]. */
    fun lift(exercise: Exercise, double: Boolean): String

    /** A workout that is no preset or flow, by its shape: "8 × 0:30 work, 0:15 rest". */
    fun shape(config: IntervalConfig): String

    fun unit(unit: WeightUnit): String
}

/** One row: when (a local date and time, the time empty for a day), what, and the figures that apply. */
data class SheetRow(
    val at: Long,
    val date: String,
    val time: String = "",
    val kind: String,
    val name: String,
    val exercise: String = "",
    val side: String = "",
    val weight: String = "",
    val unit: String = "",
    val reps: String = "",
    val seconds: String = "",
    val warmUp: String = "",
    val minutes: String = "",
    val effort: String = "",
    val note: String = "",
    val averageBpm: String = "",
    val peakBpm: String = "",
) {
    val cells: List<String>
        get() = listOf(
            date, time, kind, name, exercise, side, weight, unit, reps, seconds, warmUp, minutes, effort, note,
            averageBpm, peakBpm,
        )
}

/**
 * The rows of a spreadsheet of [history] (named by [nameOf], else by its shape), [sportDays] and [sessions] (only
 * finished ones), oldest first, with weights in [unit]. Dates and times are local, by [formatter].
 */
@Suppress("detekt.LongParameterList") // What the log holds, then how to say it.
fun sheetRows(
    history: List<HistoryEntry>,
    sportDays: List<LoggedSport>,
    sessions: List<LiftSession>,
    nameOf: (IntervalConfig) -> String?,
    unit: WeightUnit,
    words: SheetWords,
    formatter: TimestampFormatter,
): List<SheetRow> {
    fun date(millis: Long) = isoDate(formatter.dayNumber(millis))
    fun time(millis: Long) = clockTime(formatter.minuteOfDay(millis))
    val workouts = history.map { entry ->
        val at = entry.startedAt
        SheetRow(
            at = at,
            date = date(at),
            time = time(at),
            kind = words.workout,
            name = nameOf(entry.config) ?: words.shape(entry.config),
            minutes = minutes(entry.elapsedSeconds),
            effort = entry.effort?.let { (it.ordinal + 1).toString() }.orEmpty(),
            note = entry.note.orEmpty(),
            averageBpm = entry.averageBpm?.toString().orEmpty(),
            peakBpm = entry.peakBpm?.toString().orEmpty(),
        )
    }
    val sports = sportDays.map { day ->
        // A sport is logged on its day rather than at a moment: it sorts at the day's midday, its time left empty.
        SheetRow(
            at = formatter.middayOf(day.epochDay),
            date = isoDate(day.epochDay),
            kind = words.sportDay,
            name = words.sport(day.sport),
        )
    }
    val lifts: List<SheetRow> = sessions.filter { it.finishedAtEpochMillis != null }.flatMap { session ->
        val start = session.startedAtEpochMillis
        val head = SheetRow(
            at = start,
            date = date(start),
            time = time(start),
            kind = words.session,
            name = session.title,
            minutes = minutes(session.elapsedSeconds(session.finishedAtEpochMillis ?: start)),
            effort = session.effort?.let { (it.ordinal + 1).toString() }.orEmpty(),
            note = session.note.orEmpty(),
            averageBpm = session.averageBpm?.toString().orEmpty(),
            peakBpm = session.peakBpm?.toString().orEmpty(),
        )
        listOf(head) + session.sets.sortedBy { it.loggedAtEpochMillis }.map { set ->
            val (weight, weighed) = weightOf(set.load, set.exercise, unit)
            SheetRow(
                at = set.loggedAtEpochMillis,
                date = date(set.loggedAtEpochMillis),
                time = time(set.loggedAtEpochMillis),
                kind = words.set,
                name = session.title,
                exercise = words.lift(set.exercise, set.double),
                side = set.side?.let(words::side).orEmpty(),
                weight = weight,
                unit = if (weighed) words.unit(unit) else "",
                reps = if (set.seconds == null) set.reps.toString() else "",
                seconds = set.seconds?.toString().orEmpty(),
                warmUp = if (set.warmUp) words.warmUp else "",
            )
        }
    }
    // A session's own row leads its sets, which follow it in the time they were logged.
    return (workouts + sports + lifts).withIndex()
        .sortedWith(compareBy({ it.value.at }, { it.index }))
        .map { it.value }
}

/** [rows] under [header] as CSV: every cell quoted where it must be, lines ended as spreadsheets expect. */
fun csvOf(header: List<String>, rows: List<SheetRow>): String =
    (listOf(header) + rows.map { it.cells }).joinToString(separator = CRLF, postfix = CRLF) { cells ->
        cells.joinToString(",") { quoted(it) }
    }

/** The spreadsheet's file name on [epochDay]: `interval-log-2026-11-22.csv`. */
fun sheetFileName(epochDay: Long): String = "${ExportBundle.APP}-log-${isoDate(epochDay)}.csv"

const val SHEET_MIME_TYPE = "text/csv"

/** A set's weight in [unit], and whether it is a weight at all: a band says its name, the mat nothing. */
private fun weightOf(load: Load, exercise: Exercise, unit: WeightUnit): Pair<String, Boolean> = when {
    load is Load.Banded -> load.band?.name.orEmpty() to false
    load == Load.Mat || (load is Load.Bodyweight && load.kg <= 0) -> "" to false
    else -> formatWeightNumber(displayWeight(load.kg, exercise.equipment, unit)) to true
}

private fun quoted(cell: String): String =
    if (cell.any { it in NEEDS_QUOTES }) "\"${cell.replace("\"", "\"\"")}\"" else cell

private fun isoDate(epochDay: Long): String {
    val date = dateOfEpochDay(epochDay)
    return "${date.year}-${date.month.toString().padStart(2, '0')}-${date.day.toString().padStart(2, '0')}"
}

private fun clockTime(minuteOfDay: Int): String {
    val hour = (minuteOfDay / MINUTES_PER_HOUR).toString().padStart(2, '0')
    return "$hour:${(minuteOfDay % MINUTES_PER_HOUR).toString().padStart(2, '0')}"
}

/** Seconds as minutes to a tenth: "32.5". */
private fun minutes(seconds: Int): String {
    val tenths = (seconds / SECONDS_PER_TENTH_MINUTE).roundToInt()
    val whole = tenths / TENTHS
    val tenth = tenths % TENTHS
    return if (tenth == 0) whole.toString() else "$whole.$tenth"
}

private const val CRLF = "\r\n"

/** What a cell can't hold unquoted: the separator, the quote, and the end of a line. */
private const val NEEDS_QUOTES = ",\"\n\r"
private const val TENTHS = 10
private const val MINUTES_PER_HOUR = 60
private const val SECONDS_PER_TENTH_MINUTE = 6.0
