package dev.justinsmith.interval.ui

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.ShortDuration
import dev.justinsmith.interval.timer.shortDuration
import dev.justinsmith.interval.ui.resources.Res
import dev.justinsmith.interval.ui.resources.common_custom
import dev.justinsmith.interval.ui.resources.common_joined
import dev.justinsmith.interval.ui.resources.config_description
import dev.justinsmith.interval.ui.resources.duration_short_minutes
import dev.justinsmith.interval.ui.resources.duration_short_seconds
import org.jetbrains.compose.resources.stringResource

/** A workout's shape in one line: "20s on · 10s off · ×8". */
@Composable
fun configDescription(config: IntervalConfig): String = stringResource(
    Res.string.config_description,
    shortDurationText(config.workSeconds),
    shortDurationText(config.restSeconds),
    config.rounds,
)

/** The shape, then the activity unless it is General: "20s on · 10s off · ×8 · Bike". */
@Composable
fun workoutDescription(config: IntervalConfig): String {
    val shape = configDescription(config)
    if (config.activity == Activity.General) return shape
    return stringResource(Res.string.common_joined, shape, stringResource(config.activity.labelRes))
}

/** [parts] in one line, each joined to the next as [Res.string.common_joined] joins two: "Planned · 7:05 AM". */
@Composable
fun joined(parts: List<String>): String =
    parts.reduce { all, part -> stringResource(Res.string.common_joined, all, part) }

/** "45s", or "2m" for whole minutes. */
@Composable
fun shortDurationText(seconds: Int): String = when (val duration = shortDuration(seconds)) {
    is ShortDuration.Seconds -> stringResource(Res.string.duration_short_seconds, duration.seconds)
    is ShortDuration.Minutes -> stringResource(Res.string.duration_short_minutes, duration.minutes)
}

/** The matching preset's [name], or "Custom" for a shape no preset has. */
@Composable
fun presetNameOrCustom(name: String?): String = name ?: stringResource(Res.string.common_custom)
