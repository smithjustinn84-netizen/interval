package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HeartRateZone
import dev.justinsmith.interval.platform.LiveHeartRateState
import dev.justinsmith.interval.platform.heartRateZone

/**
 * The live heart rate's zone as a [ProgressRing]'s bezel shows it: the zone's [color], and how hard the heart is
 * working as a [strength] (0 with no heart read, then [zoneStrength]). Both are read where the ring is drawn, so a
 * change of zone crossfades without recomposing the ring.
 */
@Stable
class RingZone internal constructor(private val ink: State<Color>, private val level: State<Float>) {
    val color: Color get() = ink.value
    val strength: Float get() = level.value
}

/**
 * The [RingZone] of a live heart rate ([state], zoned by [maxBpm] and the resting rate the app provides) on the
 * workout's ground: the zone's colour as the zone scale has it, fading in as the sensor first reads a heart and out
 * when it stops (off the chest, dropped, none paired), keeping the colour it had while it goes.
 */
@Composable
fun rememberRingZone(state: LiveHeartRateState, maxBpm: Int = DEFAULT_MAX_BPM): RingZone {
    val zone = ringZoneOf(state, maxBpm, LocalRestingHeartRate.current)
    val kept = remember { KeptZone() }
    if (zone != null) kept.zone = zone
    val color = animateColorAsState(
        IntervalTheme.workout.heartZones.of(kept.zone),
        tween(Motion.CONTENT_MILLIS, easing = StandardEasing),
        label = "ringZone",
    )
    val strength = animateFloatAsState(
        zone?.let(::zoneStrength) ?: 0f,
        tween(Motion.CONTENT_MILLIS, easing = StandardEasing),
        label = "ringZoneStrength",
    )
    return remember(color, strength) { RingZone(color, strength) }
}

/** The zone last read, which a bezel fading out keeps the colour of. */
private class KeptZone(var zone: HeartRateZone = HeartRateZone.Zone1)

/** The zone [state] reads in, or null when it reads no heart: no sensor, none connected yet, or one off the chest. */
internal fun ringZoneOf(state: LiveHeartRateState, maxBpm: Int, restingBpm: Int?): HeartRateZone? =
    (state as? LiveHeartRateState.Reading)
        ?.takeIf { it.contact && it.bpm > 0 }
        ?.let { heartRateZone(it.bpm, maxBpm, restingBpm) }

/**
 * How strongly a ring's bezel shows [zone]: a step a zone from [ZONE_STRENGTH_FLOOR], where the easiest zone still
 * shows, to 1 at the top one.
 */
internal fun zoneStrength(zone: HeartRateZone): Float =
    ZONE_STRENGTH_FLOOR + (1f - ZONE_STRENGTH_FLOOR) * zone.ordinal / (HeartRateZone.entries.size - 1)

private const val ZONE_STRENGTH_FLOOR = 0.36f
