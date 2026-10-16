package dev.justinsmith.interval.timer

/** One workout's shape. Every duration is in whole seconds. */
data class IntervalConfig(
    val prepSeconds: Int = 10,
    val workSeconds: Int = 30,
    val restSeconds: Int = 15,
    val rounds: Int = 8,
) {
    /** Wall-clock length of the whole session: prep, every work interval, and the rests between them. */
    val totalSeconds: Int get() = prepSeconds + rounds * workSeconds + (rounds - 1) * restSeconds

    /** "20s on · 10s off · ×8". */
    val description: String get() = "${shortDuration(workSeconds)} on · ${shortDuration(restSeconds)} off · ×$rounds"

    companion object {
        val PREP_RANGE = 0..600
        val WORK_RANGE = 5..3600
        val REST_RANGE = 0..3600
        val ROUNDS_RANGE = 1..99

        // The one-tap values the edit sheet offers for each field.
        val PREP_PICKS = listOf(0, 5, 10, 15, 20, 30, 45, 60)
        val WORK_PICKS = listOf(10, 20, 30, 45, 60, 90, 120, 180)
        val REST_PICKS = listOf(0, 10, 15, 20, 30, 45, 60, 90)
        val ROUNDS_PICKS = listOf(4, 6, 8, 10, 12, 15, 20, 30)
    }
}

/** Which value a stepper or edit sheet changes. [quickPicks] are the common values offered as one-tap choices. */
enum class ConfigField(val step: Int, val range: IntRange, val quickPicks: List<Int>) {
    Prep(step = 5, range = IntervalConfig.PREP_RANGE, quickPicks = IntervalConfig.PREP_PICKS),
    Work(step = 5, range = IntervalConfig.WORK_RANGE, quickPicks = IntervalConfig.WORK_PICKS),
    Rest(step = 5, range = IntervalConfig.REST_RANGE, quickPicks = IntervalConfig.REST_PICKS),
    Rounds(step = 1, range = IntervalConfig.ROUNDS_RANGE, quickPicks = IntervalConfig.ROUNDS_PICKS),
}

/** The current value of [field]. */
fun IntervalConfig.valueOf(field: ConfigField): Int = when (field) {
    ConfigField.Prep -> prepSeconds
    ConfigField.Work -> workSeconds
    ConfigField.Rest -> restSeconds
    ConfigField.Rounds -> rounds
}

/** Sets [field] to [value], clamped to the field's range. */
fun IntervalConfig.withValue(field: ConfigField, value: Int): IntervalConfig {
    val clamped = value.coerceIn(field.range)
    return when (field) {
        ConfigField.Prep -> copy(prepSeconds = clamped)
        ConfigField.Work -> copy(workSeconds = clamped)
        ConfigField.Rest -> copy(restSeconds = clamped)
        ConfigField.Rounds -> copy(rounds = clamped)
    }
}

/** "0:30" for durations, "8" for rounds. */
fun ConfigField.format(value: Int): String = if (this == ConfigField.Rounds) value.toString() else formatClock(value)

/**
 * Same work, rest and rounds. The get-ready countdown is an app setting rather than part of a
 * workout's shape, so presets match and apply regardless of it.
 */
fun IntervalConfig.sameShape(other: IntervalConfig): Boolean =
    workSeconds == other.workSeconds && restSeconds == other.restSeconds && rounds == other.rounds

/** Moves [field] one step in [direction] (+1 or -1), clamped to the field's range. */
fun IntervalConfig.stepped(field: ConfigField, direction: Int): IntervalConfig =
    withValue(field, valueOf(field) + direction * field.step)
