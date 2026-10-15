package dev.justinsmith.interval.timer

import kotlinx.serialization.Serializable

internal const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60

/** One workout's shape. Every duration is in whole seconds. */
@Serializable
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
    }
}

/** Which value a setup stepper changes. */
enum class ConfigField(val step: Int, val range: IntRange) {
    Prep(step = 5, range = IntervalConfig.PREP_RANGE),
    Work(step = 5, range = IntervalConfig.WORK_RANGE),
    Rest(step = 5, range = IntervalConfig.REST_RANGE),
    Rounds(step = 1, range = IntervalConfig.ROUNDS_RANGE),
}

/** Moves [field] one step in [direction] (+1 or -1), clamped to the field's range. */
fun IntervalConfig.stepped(field: ConfigField, direction: Int): IntervalConfig {
    fun next(current: Int) = (current + direction * field.step).coerceIn(field.range)
    return when (field) {
        ConfigField.Prep -> copy(prepSeconds = next(prepSeconds))
        ConfigField.Work -> copy(workSeconds = next(workSeconds))
        ConfigField.Rest -> copy(restSeconds = next(restSeconds))
        ConfigField.Rounds -> copy(rounds = next(rounds))
    }
}

/** A named configuration. Built-ins ship with the app; custom ones are saved by the user. */
@Serializable
data class Preset(val name: String, val config: IntervalConfig)

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
