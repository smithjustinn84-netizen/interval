package dev.justinsmith.interval.plan

import dev.justinsmith.interval.data.AppSettings
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
 * One workout of a [DescribedRoutine]: [workout] under its preset's name or its shape's, planned every
 * week on [weekdays] (1 for Monday) and once on each of [days] (epoch days). Neither means the words gave
 * it no day, and it is not planned.
 */
data class DescribedPlan(val workout: Preset, val weekdays: Set<Int>, val days: Set<Long>) {
    val scheduled: Boolean get() = weekdays.isNotEmpty() || days.isNotEmpty()
}

/**
 * Reads a routine from how someone would say it: "tabata mon, wed and fri, 20 min bike saturday, 4 a
 * week". Each workout is read as [describeWorkout] reads one, over [current], and goes on the days said
 * with it: a weekday ("mondays", "on tue"), a run of them ("mon-fri", "weekdays", "weekends", "every
 * day") every week, or once on "today", "tomorrow" or "this"/"next" friday, counted from [today].
 * Days said before a workout ("mon wed fri: tabata") or after it in their own clause ("tabata mon, wed")
 * are that workout's. "N a week" is the goal. Null when the words give neither a workout nor a goal.
 *
 * The keywords are English, as [describeWorkout]'s are, and live here rather than in strings.xml.
 */
fun describeRoutine(text: String, current: IntervalConfig, presets: List<Preset>, today: Long): DescribedRoutine? {
    var rest = text.lowercase().replace('–', '-').replace('—', '-')
    val goal = GOAL.find(rest)?.let { match ->
        rest = rest.replaceRange(match.range, " ")
        match.groupValues[1].toInt().coerceIn(AppSettings.WEEKLY_GOAL_RANGE)
    }
    val plans = clauses(rest)
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
    val described = describeWorkout(schedule.rest, current, presets) ?: return null
    val config = described.config
    val preset = described.preset?.takeIf { it.config.sameShape(config) }
    val workout = preset?.let { Preset(it.name, config, it.id) } ?: Preset(config.defaultPresetName(), config)
    return DescribedPlan(workout, schedule.weekdays, schedule.days)
}

/** A clause of the text, what it says of a workout once its days are out, and its days. */
private class Clause(val text: String, today: Long, current: IntervalConfig, presets: List<Preset>) {
    val schedule = Schedule(text, today)
    val workout = describeWorkout(schedule.rest, current, presets)
}

/** Commas, semicolons, new lines, a full stop ending a sentence, and the words that chain clauses. */
private fun clauses(text: String): List<String> =
    text.split(CLAUSE_BREAK).filter { it.isNotBlank() }

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

private val CLAUSE_BREAK = Regex("""[,;\n&+]|\.(?=\s|$)|\b(?:and|then|plus|also)\b""")

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
