package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.heart_rate_badge_cd
import dev.justinsmith.core.resources.heart_rate_bpm
import dev.justinsmith.core.resources.heart_rate_connecting
import dev.justinsmith.core.resources.heart_rate_disconnected
import dev.justinsmith.core.resources.heart_rate_zone_1
import dev.justinsmith.core.resources.heart_rate_zone_2
import dev.justinsmith.core.resources.heart_rate_zone_3
import dev.justinsmith.core.resources.heart_rate_zone_4
import dev.justinsmith.core.resources.heart_rate_zone_5
import dev.justinsmith.core.resources.heart_rate_zone_short
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HeartRateZone
import dev.justinsmith.interval.platform.LiveHeartRateState
import dev.justinsmith.interval.platform.heartRateZone
import dev.justinsmith.interval.platform.prefersReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.jetbrains.compose.resources.stringResource

/**
 * Compact live heart-rate pill: a heart icon pulsing at the live BPM rate, the BPM value, and the active
 * zone (`Z1`–`Z5`) tinted in the zone's colour. Draws nothing when [state] is [LiveHeartRateState.Unavailable]
 * or [LiveHeartRateState.Idle].
 */
@Composable
fun HeartBeatBadge(
    state: LiveHeartRateState,
    modifier: Modifier = Modifier,
    maxBpm: Int = DEFAULT_MAX_BPM,
    onWorkoutGround: Boolean = true,
) {
    when (state) {
        LiveHeartRateState.Unavailable, LiveHeartRateState.Idle -> return

        LiveHeartRateState.Connecting -> StatusPill(
            text = stringResource(Res.string.heart_rate_connecting),
            onWorkoutGround = onWorkoutGround,
            modifier = modifier,
        )

        LiveHeartRateState.Disconnected -> StatusPill(
            text = stringResource(Res.string.heart_rate_disconnected),
            onWorkoutGround = onWorkoutGround,
            modifier = modifier,
        )

        is LiveHeartRateState.Reading -> ReadingPill(
            bpm = state.bpm,
            maxBpm = maxBpm,
            onWorkoutGround = onWorkoutGround,
            modifier = modifier,
        )
    }
}

@Composable
private fun StatusPill(text: String, onWorkoutGround: Boolean, modifier: Modifier = Modifier) {
    val bg = if (onWorkoutGround) IntervalTheme.workout.control else IntervalTheme.palette.surfaceVariant
    val fg = if (onWorkoutGround) IntervalTheme.workout.onGroundDim else IntervalTheme.palette.textDim
    Row(
        modifier
            .clip(Corners.full)
            .background(bg)
            .padding(horizontal = Spacing.space12, vertical = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Icon(IntervalIcons.Heart, contentDescription = null, tint = fg, modifier = Modifier.size(Sizes.chipIcon))
        Text(text, style = IntervalTheme.type.caption, color = fg)
    }
}

@Composable
private fun ReadingPill(bpm: Int, maxBpm: Int, onWorkoutGround: Boolean, modifier: Modifier = Modifier) {
    val zone = heartRateZone(bpm, maxBpm)
    val zones = if (onWorkoutGround) IntervalTheme.workout.heartZones else IntervalTheme.palette.heartZones
    val zoneColor by animateColorAsState(
        zones.of(zone),
        tween(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "hrZone",
    )
    val bg = if (onWorkoutGround) IntervalTheme.workout.control else IntervalTheme.palette.surfaceVariant
    val text = if (onWorkoutGround) IntervalTheme.workout.onGround else IntervalTheme.palette.text
    val scale = rememberHeartPulseScale(bpm)
    val description = stringResource(Res.string.heart_rate_badge_cd, bpm, zone.number)

    Row(
        modifier
            .clearAndSetSemantics { contentDescription = description }
            .clip(Corners.full)
            .background(bg)
            .padding(horizontal = Spacing.space12, vertical = Spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Icon(
            IntervalIcons.Heart,
            contentDescription = null,
            tint = zoneColor,
            modifier = Modifier
                .size(Sizes.chipIcon)
                .graphicsLayer {
                    val s = scale()
                    scaleX = s
                    scaleY = s
                },
        )
        Text(
            stringResource(Res.string.heart_rate_bpm, bpm),
            style = IntervalTheme.type.label,
            color = text,
        )
        ZoneTag(zone = zone, color = zoneColor, onWorkoutGround = onWorkoutGround)
    }
}

@Composable
private fun ZoneTag(zone: HeartRateZone, color: Color, onWorkoutGround: Boolean) {
    val onFill = if (onWorkoutGround) IntervalTheme.workout.onFill(color) else Color.White
    Text(
        stringResource(Res.string.heart_rate_zone_short, zone.number),
        style = IntervalTheme.type.caption,
        color = onFill,
        modifier = Modifier
            .clip(Corners.full)
            .background(color)
            .padding(horizontal = Spacing.space8, vertical = Spacing.space4 / 2),
    )
}

/**
 * Scales the heart icon in a gentle "lub-dub" pulse synced to [bpm]. Returns a lambda read inside
 * `graphicsLayer` so the animation never triggers recomposition; returns `{ 1f }` under reduced motion.
 */
@Composable
internal fun rememberHeartPulseScale(bpm: Int): () -> Float {
    val reducedMotion = prefersReducedMotion()
    val scale = remember { Animatable(1f) }
    LaunchedEffect(bpm, reducedMotion) {
        if (reducedMotion) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        val cycleMillis = (MILLIS_PER_MINUTE / bpm.coerceIn(MIN_ANIMATED_BPM, MAX_ANIMATED_BPM)).toLong()
        while (isActive) {
            scale.animateTo(Motion.HEART_PULSE_SCALE, tween(PULSE_UP_MILLIS, easing = EnterEasing))
            scale.animateTo(1f, tween(PULSE_DOWN_MILLIS, easing = StandardEasing))
            val rest = (cycleMillis - PULSE_UP_MILLIS - PULSE_DOWN_MILLIS).coerceAtLeast(MIN_PAUSE_MILLIS)
            delay(rest)
        }
    }
    return remember(scale) { { scale.value } }
}

/** Human-readable label for a [HeartRateZone] ("Easy" through "Peak"). */
@Composable
fun heartRateZoneName(zone: HeartRateZone): String = stringResource(
    when (zone) {
        HeartRateZone.Zone1 -> Res.string.heart_rate_zone_1
        HeartRateZone.Zone2 -> Res.string.heart_rate_zone_2
        HeartRateZone.Zone3 -> Res.string.heart_rate_zone_3
        HeartRateZone.Zone4 -> Res.string.heart_rate_zone_4
        HeartRateZone.Zone5 -> Res.string.heart_rate_zone_5
    },
)

private const val MILLIS_PER_MINUTE = 60_000
private const val MIN_ANIMATED_BPM = 40
private const val MAX_ANIMATED_BPM = 220
private const val PULSE_UP_MILLIS = 110
private const val PULSE_DOWN_MILLIS = 190
private const val MIN_PAUSE_MILLIS = 120L

@PreviewFormFactors
@Composable
private fun HeartBeatBadgePreview() {
    IntervalTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            HeartBeatBadge(LiveHeartRateState.Reading(bpm = 158))
            HeartBeatBadge(LiveHeartRateState.Connecting)
        }
    }
}
