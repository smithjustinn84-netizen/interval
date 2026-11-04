package dev.justinsmith.interval.ui

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.ShortDuration
import dev.justinsmith.interval.timer.shortDuration
import dev.justinsmith.interval.ui.resources.Res
import dev.justinsmith.interval.ui.resources.common_custom
import dev.justinsmith.interval.ui.resources.common_joined
import dev.justinsmith.interval.ui.resources.config_cool_down
import dev.justinsmith.interval.ui.resources.config_description
import dev.justinsmith.interval.ui.resources.config_set_rest
import dev.justinsmith.interval.ui.resources.config_sets
import dev.justinsmith.interval.ui.resources.config_warm_up
import dev.justinsmith.interval.ui.resources.duration_short_minutes
import dev.justinsmith.interval.ui.resources.duration_short_seconds
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A workout's shape in one line: "20s on · 10s off · ×8", with its sets when it has more than one ("20s on ·
 * 10s off · ×8 · 4 sets · 1m between") and a warm-up and cool-down either side when it has them: "5m warm-up ·
 * 20s on · 10s off · ×8 · 3m cool-down".
 */
@Composable
fun configDescription(config: IntervalConfig): String {
    val shape = stringResource(
        Res.string.config_description,
        shortDurationText(config.workSeconds),
        shortDurationText(config.restSeconds),
        config.rounds,
    )
    val rounds = if (config.sets > 1) joined(listOfNotNull(shape, setsText(config))) else shape
    if (config.warmUpSeconds == 0 && config.coolDownSeconds == 0) return rounds
    return joined(
        listOfNotNull(
            config.warmUpSeconds.takeIf { it > 0 }?.let {
                stringResource(Res.string.config_warm_up, shortDurationText(it))
            },
            rounds,
            config.coolDownSeconds.takeIf { it > 0 }?.let {
                stringResource(Res.string.config_cool_down, shortDurationText(it))
            },
        ),
    )
}

/** "4 sets · 1m between", or "4 sets" with no rest of their own between them. */
@Composable
private fun setsText(config: IntervalConfig): String {
    val sets = pluralStringResource(Res.plurals.config_sets, config.sets, config.sets)
    if (config.setRestSeconds == 0) return sets
    return stringResource(
        Res.string.common_joined,
        sets,
        stringResource(Res.string.config_set_rest, shortDurationText(config.setRestSeconds)),
    )
}

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
