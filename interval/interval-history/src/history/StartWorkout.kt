package dev.justinsmith.interval.history

import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.builtInFlows
import dev.justinsmith.interval.timer.matching

/**
 * The key of the flow [config] is, by its shape: a flow done again, or planned, starts as itself, with the Timer
 * left as it is. Null for any other workout, which goes on the Timer first and starts from it.
 */
internal fun flowKeyOf(config: IntervalConfig): String? = builtInFlows.matching(config)?.key
