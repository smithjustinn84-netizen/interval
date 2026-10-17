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

/**
 * The preset [config] counts as, if any: the first of the user's own with its shape, else the first
 * built-in. One rule, so the Timer, the summary, History and the Presets list all name the same one,
 * and only one card on the list says it is on the Timer.
 */
fun List<Preset>.matching(config: IntervalConfig): Preset? {
    val same = filter { it.config.sameShape(config) }
    return same.firstOrNull { it.custom } ?: same.firstOrNull()
}

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
 * The name this preset is stored under beside [presets]: its own trimmed, a blank one becoming its shape's
 * ("40/20 ×6"), and one another preset has numbered ("Tabata 2"). The preset its [Preset.id] names is not
 * "another": keeping its own name is no clash. One rule for the editor's hint and the stored name.
 */
fun Preset.storedName(presets: List<Preset>): String {
    val wanted = name.trim().ifEmpty { config.defaultPresetName() }.take(Preset.MAX_NAME_LENGTH)
    val taken = presets.filterNot { id != null && it.id == id }.map { it.name }
    return uniquePresetName(wanted, taken)
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
