package dev.justinsmith.interval.ui

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.config_description
import dev.justinsmith.interval.resources.duration_short_minutes
import dev.justinsmith.interval.resources.duration_short_seconds
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.ShortDuration
import dev.justinsmith.interval.timer.shortDuration
import org.jetbrains.compose.resources.stringResource

/** A workout's shape in one line: "20s on · 10s off · ×8". */
@Composable
fun configDescription(config: IntervalConfig): String = stringResource(
    Res.string.config_description,
    shortDurationText(config.workSeconds),
    shortDurationText(config.restSeconds),
    config.rounds,
)

/** "45s", or "2m" for whole minutes. */
@Composable
fun shortDurationText(seconds: Int): String = when (val duration = shortDuration(seconds)) {
    is ShortDuration.Seconds -> stringResource(Res.string.duration_short_seconds, duration.seconds)
    is ShortDuration.Minutes -> stringResource(Res.string.duration_short_minutes, duration.minutes)
}
