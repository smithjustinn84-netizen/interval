package dev.justinsmith.interval.today

import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.Flow
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.SECONDS_PER_MINUTE
import dev.justinsmith.interval.timer.builtInFlows
import dev.justinsmith.interval.timer.withPreset

/** The lengths Today offers to pick a workout by, in minutes. */
val PICK_MINUTES = listOf(5, 10, 20, 30)

/** The length picked until another is. */
const val DEFAULT_PICK_MINUTES = 10

private const val PICKS_SHOWN = 3

/**
 * A preset that fits the time picked, and how long it runs from the Timer as it stands: [totalSeconds]. A flow is
 * one too, under its own name and shape, though it starts as itself rather than from the Timer.
 */
data class QuickPick(val preset: Preset, val totalSeconds: Int) {
    /** Mobility rather than conditioning, which its row is marked as. */
    val mobility: Boolean get() = preset.config.activity == Activity.Mobility
}

/**
 * The presets that fit [minutes], the longest first and at most three: a few minutes are best spent whole. Each
 * is measured as it would run from the Timer [onTimer], whose get-ready time a preset keeps, as the Presets page
 * measures it. One fits by its whole minutes, so 20:10 is a twenty-minute workout. Of two the same length the
 * user's own comes first. After them comes the longest of [flows] that fits, measured after the same get-ready:
 * the time may be for mobility.
 */
fun List<Preset>.fitting(
    minutes: Int,
    onTimer: IntervalConfig,
    flows: List<Flow> = builtInFlows,
): List<QuickPick> {
    fun QuickPick.fits() = totalSeconds / SECONDS_PER_MINUTE <= minutes
    val presets = map { QuickPick(it, onTimer.withPreset(it).totalSeconds) }
        .filter { it.fits() }
        .sortedWith(compareByDescending<QuickPick> { it.totalSeconds }.thenByDescending { it.preset.custom })
        .take(PICKS_SHOWN)
    val flow = flows
        .map { QuickPick(Preset(it.name, it.config), it.config(onTimer.prepSeconds).totalSeconds) }
        .filter { it.fits() }
        .maxByOrNull { it.totalSeconds }
    return presets + listOfNotNull(flow)
}
