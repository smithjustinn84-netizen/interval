package dev.justinsmith.interval.timer

/**
 * A named configuration. Built-ins ship with the app; custom ones are saved by the user and carry the
 * [id] they are stored under.
 */
data class Preset(val name: String, val config: IntervalConfig, val id: Long? = null) {
    /** One of the user's own, which can be edited and deleted; built-ins have no [id]. */
    val custom: Boolean get() = id != null

    companion object {
        /** The longest name a preset can be saved under. */
        const val MAX_NAME_LENGTH = 24
    }
}

/** A preset as the lists show it: whether it is one of the user's own, and so deletable. */
data class PresetEntry(val preset: Preset, val custom: Boolean)

/** Built-ins first, then the user's own in the order they were saved. */
fun presetEntries(custom: List<Preset>): List<PresetEntry> =
    builtInPresets.map { PresetEntry(it, custom = false) } + custom.map { PresetEntry(it, custom = true) }

/** The first preset with [config]'s shape, if any. */
fun List<PresetEntry>.matching(config: IntervalConfig): Preset? =
    firstOrNull { it.preset.config.sameShape(config) }?.preset

/**
 * The name a preset gets when the user gives it none: its shape, "40/20 ×6", with a whole number of
 * minutes as "1m". A stored name rather than a label, so it is built here, like a clock string.
 */
fun IntervalConfig.defaultPresetName(): String = "${nameLabel(workSeconds)}/${nameLabel(restSeconds)} ×$rounds"

private fun nameLabel(seconds: Int): String = when (val duration = shortDuration(seconds)) {
    is ShortDuration.Seconds -> duration.seconds.toString()
    is ShortDuration.Minutes -> "${duration.minutes}m"
}

/**
 * [name] as it can be stored beside [taken]: itself when no other preset has it, else with " 2", " 3"…
 * appended, the name cut to keep the whole within [Preset.MAX_NAME_LENGTH].
 */
fun uniquePresetName(name: String, taken: Collection<String>): String {
    if (name !in taken) return name
    return generateSequence(2) { it + 1 }
        .map { number ->
            val suffix = " $number"
            name.take(Preset.MAX_NAME_LENGTH - suffix.length).trimEnd() + suffix
        }
        .first { it !in taken }
}

val builtInPresets: List<Preset> = listOf(
    Preset("Tabata", IntervalConfig(prepSeconds = 10, workSeconds = 20, restSeconds = 10, rounds = 8)),
    Preset("HIIT 30/30", IntervalConfig(prepSeconds = 10, workSeconds = 30, restSeconds = 30, rounds = 10)),
    Preset("Hangboard", IntervalConfig(prepSeconds = 10, workSeconds = 10, restSeconds = 50, rounds = 6)),
    Preset("Long efforts", IntervalConfig(prepSeconds = 5, workSeconds = 45, restSeconds = 15, rounds = 12)),
)
