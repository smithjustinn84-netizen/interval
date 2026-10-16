package dev.justinsmith.interval.timer

internal const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60

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

/**
 * A named configuration. Built-ins ship with the app; custom ones are saved by the user and carry the
 * [id] they are stored under.
 */
data class Preset(val name: String, val config: IntervalConfig, val id: Long? = null)

/** A preset as the lists show it: whether it is one of the user's own, and so deletable. */
data class PresetEntry(val preset: Preset, val custom: Boolean)

/** Built-ins first, then the user's own in the order they were saved. */
fun presetEntries(custom: List<Preset>): List<PresetEntry> =
    builtInPresets.map { PresetEntry(it, custom = false) } + custom.map { PresetEntry(it, custom = true) }

/** The first preset with [config]'s shape, if any. */
fun List<PresetEntry>.matching(config: IntervalConfig): Preset? =
    firstOrNull { it.preset.config.sameShape(config) }?.preset

val builtInPresets: List<Preset> = listOf(
    Preset("Tabata", IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 8)),
    Preset("HIIT 30/30", IntervalConfig(prepSeconds = 10, workSeconds = 30, restSeconds = 30, rounds = 10)),
    Preset("Hangboard", IntervalConfig(prepSeconds = 10, workSeconds = 10, restSeconds = 50, rounds = 6)),
    Preset("Long efforts", IntervalConfig(prepSeconds = 5, workSeconds = 45, restSeconds = 15, rounds = 12)),
)

/** "0:30", "12:05". Negative input reads as zero. */
fun formatClock(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / SECONDS_PER_MINUTE}:${(s % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
}

/** Whole seconds left in [millis], rounded up, so a display never shows 0:00 while time remains. */
fun ceilSeconds(millis: Long): Int =
    if (millis <= 0) 0 else ((millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()

/** "45s", or "2m" for whole minutes. */
fun shortDuration(seconds: Int): String {
    val wholeMinutes = seconds >= SECONDS_PER_MINUTE && seconds % SECONDS_PER_MINUTE == 0
    return if (wholeMinutes) "${seconds / SECONDS_PER_MINUTE}m" else "${seconds}s"
}
