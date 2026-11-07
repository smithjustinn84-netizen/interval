package dev.justinsmith.interval.history

import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.asksSnatches
import dev.justinsmith.interval.timer.matching
import dev.justinsmith.interval.timer.nameOf

/**
 * What a finished or planned workout is named by: the user's [presets] and the built-ins, then the [flows], shipped
 * and the user's own, as the workout screen names it.
 */
internal class WorkoutNames(private val presets: List<Preset>, private val flows: List<Flow>) {
    /** The name [config] goes by, or null for one that is none of them. */
    fun of(config: IntervalConfig): String? = presets.nameOf(config, flows)

    /** Whether a workout of [config] is one of kettlebell snatches, asked how many a set: [asksSnatches]. */
    fun asksSnatches(config: IntervalConfig): Boolean = presets.asksSnatches(config)
}

/**
 * The key of the one of [flows] that [config] is, by its shape: a flow done again, or planned, starts as itself, with
 * the Timer left as it is. Null for any other workout, which goes on the Timer first and starts from it.
 */
internal fun flowKeyOf(config: IntervalConfig, flows: List<Flow>): String? = flows.matching(config)?.key
