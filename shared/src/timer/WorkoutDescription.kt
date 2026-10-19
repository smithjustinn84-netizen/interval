package dev.justinsmith.interval.timer

import kotlin.math.roundToInt

/**
 * A workout read from words, as it would go onto the Timer: [config] holds every value the words gave
 * and the Timer's own for the rest, [understood] which of the shape's fields the words gave, and
 * [activityFound] whether they named an activity.
 */
data class DescribedWorkout(
    val config: IntervalConfig,
    val understood: Set<ConfigField>,
    val activityFound: Boolean,
)

/**
 * Reads a workout from how someone would say it: "tabata", "40/20 x8", "45 on 15 off, 12 rounds",
 * "work 1:30 rest 0:30", "10 rounds of 30 seconds", "20 min of burpees, short rests". A preset's name
 * starts from that preset; a total length with no rounds gives as many rounds as fit. What the words
 * leave out keeps [current]'s value, and the get-ready countdown is always [current]'s, as it is when
 * a preset goes onto the Timer. Values are clamped to their ranges. Null when nothing is understood.
 *
 * The keywords are English. They read the user's input rather than speak to the user, so they live
 * here, not in strings.xml; the numbers carry most descriptions in any language.
 */
fun describeWorkout(text: String, current: IntervalConfig, presets: List<Preset>): DescribedWorkout? {
    val reading = Reading(normalized(text))
    val preset = reading.takePreset(presets)
    reading.readShape()
    val activity = activityIn(reading.text)
    if (preset == null) reading.vagueRest?.let { reading.values.getOrPut(ConfigField.Rest) { it } }
    if (preset == null && reading.readNothing && activity == null) return null

    var config = preset?.let(current::withPreset) ?: current
    val understood = if (preset != null) ConfigField.shape.toMutableSet() else mutableSetOf()
    reading.values.forEach { (field, value) ->
        config = config.withValue(field, value)
        understood += field
    }
    reading.totalSeconds?.takeIf { ConfigField.Rounds !in reading.values }?.let { total ->
        config = config.withValue(ConfigField.Rounds, config.roundsIn(total))
        understood += ConfigField.Rounds
    }
    if (activity != null) config = config.copy(activity = activity)
    return DescribedWorkout(config, understood, activityFound = activity != null)
}

/** The text being read, each part taken out as a rule reads it so that no later rule reads it again. */
private class Reading(var text: String) {
    val values = mutableMapOf<ConfigField, Int>()
    var totalSeconds: Int? = null

    /** No value, and no length to make rounds of. */
    val readNothing: Boolean get() = values.isEmpty() && totalSeconds == null

    /** The rest a word like "short" gives, for when neither a number nor a preset does. */
    var vagueRest: Int? = null

    fun take(regex: Regex): MatchResult? {
        val match = regex.find(text) ?: return null
        text = text.replaceRange(match.range, " ")
        return match
    }

    /** The longest preset name said as a whole word, the user's own first among names as long. */
    fun takePreset(presets: List<Preset>): Preset? {
        val (preset, at) = presets
            .sortedWith(compareByDescending<Preset> { it.name.length }.thenByDescending { it.custom })
            .firstNotNullOfOrNull { preset -> wholeWordAt(normalized(preset.name))?.let { preset to it } }
            ?: return null
        text = text.replaceRange(at, " ")
        return preset
    }

    private fun wholeWordAt(name: String): IntRange? {
        if (name.isBlank()) return null
        var start = text.indexOf(name)
        while (start >= 0) {
            val end = start + name.length
            val before = text.getOrNull(start - 1)
            val after = text.getOrNull(end)
            if (before?.isLetterOrDigit() != true && after?.isLetterOrDigit() != true) return start until end
            start = text.indexOf(name, start + 1)
        }
        return null
    }

    fun readShape() {
        take(SLASH_PAIR)?.let { match ->
            match.secondsAt(1)?.let { values[ConfigField.Work] = it }
            match.secondsAt(DURATION_GROUPS + 1)?.let { values[ConfigField.Rest] = it }
        }
        ROUNDS.firstNotNullOfOrNull(::take)?.let {
            // Too many digits for an Int is still "as many as can be", which the range then clamps.
            values[ConfigField.Rounds] = it.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
        }
        // Any other count ("3 sets of 10 reps") is no duration either.
        while (take(ROUNDS.first()) != null) continue
        vagueRest = VAGUE_RESTS.firstOrNull { (regex, _) -> take(regex) != null }?.second
        readLabelled()
        readUnlabelled()
    }

    /**
     * "40 on 20 off" or "work 40 rest 20": whichever way the text says more of, read first, then the
     * other way for what is left ("30 seconds work, rest 15").
     */
    private fun readLabelled() {
        val after = LABEL_AFTER.count { (regex, _) -> regex.containsMatchIn(text) }
        val before = LABEL_BEFORE.count { (regex, _) -> regex.containsMatchIn(text) }
        val ways = if (before > after) LABEL_BEFORE + LABEL_AFTER else LABEL_AFTER + LABEL_BEFORE
        for ((regex, field) in ways) {
            if (field in values) continue
            take(regex)?.secondsAt(1)?.let { values[field] = it }
        }
    }

    /**
     * Durations no word names, in order: a long one ("20 min") is the whole workout's length while
     * the rounds are not given, any other the work, then the rest.
     */
    private fun readUnlabelled() {
        generateSequence { take(ANY_DURATION) }.forEach { match ->
            val seconds = match.secondsAt(1) ?: return@forEach
            val long = isMinutes(match.groupValues[2]) || seconds >= LONG_SECONDS
            when {
                long && ConfigField.Rounds !in values && totalSeconds == null -> totalSeconds = seconds
                ConfigField.Work !in values -> values[ConfigField.Work] = seconds
                ConfigField.Rest !in values -> values[ConfigField.Rest] = seconds
            }
        }
    }
}

/** Lower case, with the other ways of writing "x" and dashes as the plain ones. */
private fun normalized(text: String): String =
    text.lowercase().replace('×', 'x').replace('–', '-').replace('—', '-')

/** How many rounds of this work and rest fill [totalSeconds], the last rest left out as the workout leaves it. */
private fun IntervalConfig.roundsIn(totalSeconds: Int): Int =
    ((totalSeconds + restSeconds).toDouble() / (workSeconds + restSeconds)).roundToInt().coerceAtLeast(1)

/** The seconds of the [DURATION] whose number is group [index] and unit the group after it, or null for metres. */
private fun MatchResult.secondsAt(index: Int): Int? {
    val number = groupValues[index]
    val unit = groupValues[index + 1]
    if (':' in number) {
        val (minutes, seconds) = number.split(':').map(String::toInt)
        return minutes * SECONDS_PER_MINUTE + seconds
    }
    val value = number.toDoubleOrNull() ?: return null
    // "400m" is a distance, not 400 minutes.
    if (unit == "m" && value > MAX_BARE_M_MINUTES) return null
    return (if (isMinutes(unit)) value * SECONDS_PER_MINUTE else value).roundToInt()
}

private fun isMinutes(unit: String): Boolean = unit == "m" || unit.startsWith("min")

/** The activity named first in [text], if any. */
private fun activityIn(text: String): Activity? =
    ACTIVITY_WORDS
        .mapNotNull { (regex, activity) -> regex.find(text)?.let { it.range.first to activity } }
        .minByOrNull { it.first }
        ?.second

private const val SECONDS_PER_MINUTE = 60

/** The most minutes a bare "m" can mean; more is metres. */
private const val MAX_BARE_M_MINUTES = 60

/** A duration no word names and this long or longer is the whole workout's, not an interval's. */
private const val LONG_SECONDS = 600

/** A number of seconds, "1:30" or "1.5", then its unit if it has one. Two groups. */
private const val DURATION = """(\d{1,3}:\d{2}|\d+(?:\.\d+)?)(?:\s*(seconds?|secs?|s|minutes?|mins?|m)\b)?"""
private const val DURATION_GROUPS = 2
private const val UNIT_NEXT = """\s*(?:seconds?|secs?|s|minutes?|mins?|m)\b"""

private val SLASH_PAIR = Regex("""$DURATION\s*/\s*$DURATION""")

// "8 rounds", then "x8" (but not the "x 30s" of "8 x 30s"), then "8x".
private val ROUNDS = listOf(
    Regex("""\b(\d+)\s*(?:rounds?|sets?|reps?|cycles?|intervals?|times)\b"""),
    Regex("""(?:^|[^a-z])x\s*(\d+)\b(?!$UNIT_NEXT|\s*[:/.])"""),
    Regex("""\b(\d+)\s*x(?![a-z])"""),
)

// "20 min on the bike" is how long, not 20 minutes of work.
private const val WORK_WORDS = """on(?!\s+(?:the|a|an|my)\b)|work|working"""
private const val REST_WORDS = "off|rest|resting|recovery|break"

private val LABEL_AFTER = listOf(
    Regex("""\b$DURATION\s*(?:of\s+)?(?:$WORK_WORDS)\b""") to ConfigField.Work,
    Regex("""\b$DURATION\s*(?:of\s+)?(?:$REST_WORDS)\b""") to ConfigField.Rest,
)

private val LABEL_BEFORE = listOf(
    Regex("""\b(?:work|working)\s*(?:of\s+|for\s+)?:?\s*$DURATION""") to ConfigField.Work,
    Regex("""\b(?:rest|resting|recovery|break)\s*(?:of\s+|for\s+)?:?\s*$DURATION""") to ConfigField.Rest,
)

private val ANY_DURATION = Regex("""\b$DURATION""")

private const val SHORT_REST_SECONDS = 15
private const val LONG_REST_SECONDS = 60

// Only where no number follows: "short rest of 20s" is 20 seconds.
private const val NO_NUMBER_NEXT = """(?!\s*(?:of\s+|for\s+|:)?\s*\d)"""
private val VAGUE_RESTS = listOf(
    Regex("""\bno\s+(?:rests?|breaks?)\b$NO_NUMBER_NEXT""") to 0,
    Regex("""\b(?:short|quick|brief|little)\s+(?:rests?|breaks?|recovery)\b$NO_NUMBER_NEXT""") to SHORT_REST_SECONDS,
    Regex("""\b(?:long|full|big)\s+(?:rests?|breaks?|recovery)\b$NO_NUMBER_NEXT""") to LONG_REST_SECONDS,
)

// The first named wins, so "mountain climbers" is bodyweight work rather than climbing.
private val ACTIVITY_WORDS = listOf(
    """ski\s*-?\s*erg|skierg|skiing|ski""" to Activity.SkiErg,
    """bikes?|biking|cycl(?:e|ing)|spin|assault|airbike""" to Activity.Bike,
    """rows?|rowing|rower|erg""" to Activity.Rower,
    """runs?|running|sprints?|treadmill|jog(?:s|ging)?""" to Activity.Running,
    """jump\s*-?\s*rope|rope|skipping|double\s*-?\s*unders?""" to Activity.JumpRope,
    """burpees?|push\s*-?\s*ups?|pull\s*-?\s*ups?|air\s+squats?|squats?|jumping\s+jacks?|mountain\s+climbers?|""" +
        """bodyweight|body\s+weight|calisthenics|planks?""" to Activity.Bodyweight,
    """kettlebells?|kbs?|swings?""" to Activity.Kettlebell,
    """barbells?|deadlifts?|cleans?|snatch(?:es)?|thrusters?|olympic""" to Activity.Barbell,
    """climb(?:s|ing)?|hangboard(?:ing)?|hangs?|boulder(?:s|ing)?""" to Activity.Climbing,
).map { (words, activity) -> Regex("""\b(?:$words)\b""") to activity }
