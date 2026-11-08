package dev.justinsmith.interval.plan

import dev.justinsmith.interval.model.AppSettings
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.asPlan
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.defaultPresetName
import dev.justinsmith.interval.timer.describeWorkout
import dev.justinsmith.interval.timer.sameShape
import dev.justinsmith.interval.timer.weekdayOf

/** A routine read from words: the workouts to plan, and the weekly goal if the words gave one. */
data class DescribedRoutine(val plans: List<DescribedPlan>, val goal: Int?)

/**
 * One workout of a [DescribedRoutine]: [workout], an interval workout under its preset's name or its shape's or
 * a session on Lift, planned every week on [weekdays] (1 for Monday) and once on each of [days] (epoch days).
 * Neither means the words gave it no day, and it is not planned.
 */
data class DescribedPlan(val workout: PlanWorkout, val weekdays: Set<Int>, val days: Set<Long>) {
    val scheduled: Boolean get() = weekdays.isNotEmpty() || days.isNotEmpty()
}

/**
 * Reads a routine from how someone would say it: "tabata mon, wed and fri, 20 min bike saturday, 4 a
 * week". Each workout is read as [describeWorkout] reads one, over [current], and goes on the days said
 * with it: a weekday ("mondays", "on tue"), a run of them ("mon-fri", "weekdays", "weekends", "every
 * day") every week, or once on "today", "tomorrow" or "this"/"next" friday, counted from [today].
 * Days said before a workout ("mon wed fri: tabata") or after it in their own clause ("tabata mon, wed")
 * are that workout's. "Then" (or "followed by") starts another workout only when the words on both sides of it
 * name their own days ("tabata mon then 40/20 x8 wed"); otherwise it chains blocks into one sequence, as
 * [describeWorkout] reads it ("8 x 20/10 then 5 x 1:00/0:30 mon"). A clause that names Lift ("lift",
 * "kettlebells", "strength") and no interval shape is a session on Lift, the next day of the program followed.
 * "N a week" is the goal. Null when the words give neither a workout nor a goal.
 *
 * The keywords are English, as [describeWorkout]'s are, and live here rather than in strings.xml.
 */
fun describeRoutine(text: String, current: IntervalConfig, presets: List<Preset>, today: Long): DescribedRoutine? {
    var rest = text.lowercase().replace('–', '-').replace('—', '-')
    val goal = GOAL.find(rest)?.let { match ->
        rest = rest.replaceRange(match.range, " ")
        match.groupValues[1].toInt().coerceIn(AppSettings.WEEKLY_GOAL_RANGE)
    }
    val plans = clauses(rest, today)
        .map { Clause(it, today, current, presets) }
        .fold(mutableListOf<MutableList<Clause>>()) { groups, clause ->
            val group = groups.lastOrNull()
            val startsAnother = clause.workout != null && group != null &&
                group.any { it.workout != null } && group.any { it.schedule.said }
            if (group == null || startsAnother) groups += mutableListOf(clause) else group += clause
            groups
        }
        .mapNotNull { group -> plan(group, today, current, presets) }
    if (plans.isEmpty() && goal == null) return null
    return DescribedRoutine(plans, goal)
}

/** The [group]'s workout, read from all its words at once, on all the days they name. */
private fun plan(group: List<Clause>, today: Long, current: IntervalConfig, presets: List<Preset>): DescribedPlan? {
    val schedule = Schedule(group.joinToString(" ") { it.text }, today)
    val workout = readWorkout(schedule.rest, current, presets) ?: return null
    return DescribedPlan(workout, schedule.weekdays, schedule.days)
}

/**
 * The workout [text] names: a session on Lift when it names one and no interval shape ("kettlebells", but not
 * "kettlebell 30/30 x10"), else the interval workout [describeWorkout] reads, under its preset's name when its
 * shape is that preset's.
 */
private fun readWorkout(text: String, current: IntervalConfig, presets: List<Preset>): PlanWorkout? {
    val described = describeWorkout(text, current, presets)
    if (LIFT_WORDS.containsMatchIn(text) && described?.understood.isNullOrEmpty()) return PlanWorkout.Lift.ProgramDay
    described ?: return null
    val config = described.config
    val preset = described.preset?.takeIf { it.config.sameShape(config) }
    return (preset?.let { Preset(it.name, config, it.id) } ?: Preset(config.defaultPresetName(), config)).asPlan()
}

/** A clause of the text, what it says of a workout once its days are out, and its days. */
private class Clause(val text: String, today: Long, current: IntervalConfig, presets: List<Preset>) {
    val schedule = Schedule(text, today)
    val workout = readWorkout(schedule.rest, current, presets)
}

/**
 * Commas, semicolons, new lines, a full stop ending a sentence, and the words that chain clauses; then a "then"
 * between two pieces that each name their days. A "then" with a piece of no day beside it stays, so the clause is
 * read as a sequence of blocks.
 */
private fun clauses(text: String, today: Long): List<String> =
    text.split(CLAUSE_BREAK).filter { it.isNotBlank() }.flatMap { clause -> chains(clause, today) }

/** The [clause] split at each "then" whose pieces before and after both name days, the rest joined by "then". */
private fun chains(clause: String, today: Long): List<String> {
    val chains = mutableListOf<MutableList<String>>()
    clause.split(BLOCK_BREAK).filter { it.isNotBlank() }.forEach { piece ->
        val chain = chains.lastOrNull()
        val bothDated = chain != null && Schedule(chain.joinToString(" "), today).said && Schedule(piece, today).said
        if (chain == null || bothDated) chains += mutableListOf(piece) else chain += piece
    }
    return chains.map { it.joinToString(" then ") }
}

/** The days [text] names, taken out of it so the rest can be read as a workout, which is [rest]. */
private class Schedule(text: String, today: Long) {
    val weekdays = mutableSetOf<Int>()
    val days = mutableSetOf<Long>()
    var rest: String = text
        private set

    val said: Boolean get() = weekdays.isNotEmpty() || days.isNotEmpty()

    init {
        takeAll(TODAY_WORDS) { days += today }
        takeAll(TOMORROW_WORDS) { days += today + 1 }
        takeAll(COMING_DAY) { match ->
            val weekday = weekdayNamed(match.groupValues[2])
            val ahead = (weekday - weekdayOf(today)).mod(DAYS_PER_WEEK)
            days += today + if (ahead == 0 && match.groupValues[1] == "next") DAYS_PER_WEEK else ahead
        }
        takeAll(DAY_RANGE) { match ->
            val from = weekdayNamed(match.groupValues[1])
            val to = weekdayNamed(match.groupValues[2])
            val length = (to - from).mod(DAYS_PER_WEEK)
            weekdays += (0..length).map { (from - 1 + it) % DAYS_PER_WEEK + 1 }
        }
        DAY_GROUPS.forEach { (regex, group) -> takeAll(regex) { weekdays += group } }
        takeAll(ONE_DAY) { weekdays += weekdayNamed(it.groupValues[1]) }
    }

    private fun takeAll(regex: Regex, read: (MatchResult) -> Unit) {
        regex.findAll(rest).toList().forEach(read)
        rest = rest.replace(regex, " ")
    }
}

/** The weekday, 1 for Monday, a name [ONE_DAY] matched is. */
private fun weekdayNamed(name: String): Int = WEEKDAY_NAMES.indexOfFirst { it.matches(name) } + 1

// What names a session on Lift rather than an interval workout, when no shape comes with it.
private val LIFT_WORDS = Regex("""\b(?:lift(?:s|ing)?|strength|kettlebells?|kbs?|bells?)\b""")

private val CLAUSE_BREAK = Regex("""[,;\n&+]|\.(?=\s|$)|\b(?:and|plus|also)\b""")

// What chains blocks in describeWorkout, and here also what can start another workout.
private val BLOCK_BREAK = Regex("""\bthen\b|\bfollowed\s+by\b""")

// "4 a week", "3 times per week", "5x/week", "4 days a wk".
private val GOAL =
    Regex("""\b(\d{1,2})\s*(?:x|times?|days?|workouts?|sessions?)?\s*(?:(?:a|per|each|every)\s+|/\s*)(?:week|wk)\b""")

// Each weekday's names, Monday first; a trailing "s" ("mondays") is read by the patterns that use them.
private val WEEKDAY_NAMES = listOf(
    "mon(?:day)?",
    "tue(?:s|sday)?",
    "wed(?:s|nesday)?",
    "thu(?:r(?:s(?:day)?)?)?",
    "fri(?:day)?",
    "sat(?:urday)?",
    "sun(?:day)?",
).map(::Regex)

private val DAY_NAME = WEEKDAY_NAMES.joinToString("|", "(", ")") { "(?:${it.pattern})" }

// "On" goes with its day, so "40 on 20 off on mondays" leaves no stray "on" for the work to take.
private const val LEAD = """(?:(?:on|every|each|from)\s+)?"""

private val TODAY_WORDS = Regex("""\b(?:today|tonight)\b""")
private val TOMORROW_WORDS = Regex("""\b(?:tomorrow|tmrw)\b""")
private val COMING_DAY = Regex("""\b(?:on\s+)?(this|next|coming)\s+$DAY_NAME\b""")
private val DAY_RANGE =
    Regex("""\b$LEAD${DAY_NAME}s?\s*(?:-|to|through|thru|till|until)\s*${DAY_NAME}s?\b""")
private val ONE_DAY = Regex("""\b$LEAD${DAY_NAME}s?\b""")

private val DAY_GROUPS = listOf(
    Regex("""\b$LEAD(?:week\s*days?)\b""") to (1..5).toSet(),
    Regex("""\b$LEAD(?:week\s*ends?)\b""") to setOf(6, 7),
    Regex("""\b(?:every\s*day|daily)\b""") to (1..DAYS_PER_WEEK).toSet(),
)
