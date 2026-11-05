package dev.justinsmith.interval.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import dev.justinsmith.interval.timer.ConfigField
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.resources.Res
import dev.justinsmith.interval.ui.resources.common_ellipsis
import dev.justinsmith.interval.ui.resources.ladder_rounds_work
import org.jetbrains.compose.resources.stringResource

/**
 * A second value on another's edit sheet, where it changes that one (the ladder on the work's, the rest between
 * sets on the sets'): its name and what it is for, over its value between − and + (hold either to repeat), dim
 * while it is off.
 */
@Composable
fun SheetField(field: ConfigField, value: Int, onStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val rising = rememberRising(value)
    val color by animateColorAsState(
        if (field.isOff(value)) palette.textDim else field.valueColor(),
        tween<Color>(Motion.STATE_MILLIS, easing = StandardEasing),
        label = "value",
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Column {
            Text(stringResource(field.labelRes), style = IntervalTheme.type.label, color = palette.text)
            Text(stringResource(field.hintRes), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FieldStepButton(field, value, more = false, onStep = onStep)
            Box(
                Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
                contentAlignment = Alignment.Center,
            ) {
                RollingText(
                    field.valueText(value),
                    rising,
                    style = IntervalTheme.type.stat.copy(textAlign = TextAlign.Center),
                    color = color,
                )
            }
            FieldStepButton(field, value, more = true, onStep = onStep)
        }
    }
}

/**
 * The ladder on the work's sheet: its step and, while it is on, what it makes of the rounds: the workout's shape
 * as a bar, each round's work a step wider or narrower than the last, over their lengths in words ("Rounds work
 * 0:30 · 0:40 · 0:50"; past [LISTED_ROUNDS] rounds the first two and the last).
 */
@Composable
fun LadderField(config: IntervalConfig, onStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        SheetField(ConfigField.WorkStep, config.workStepSeconds, onStep)
        if (config.isLadder) {
            Timeline(config)
            Text(
                stringResource(Res.string.ladder_rounds_work, joined(roundsWork(config))),
                style = IntervalTheme.type.meta,
                color = IntervalTheme.palette.textDim,
            )
        }
    }
}

/** Each round's work as a clock, or with more than [LISTED_ROUNDS] the first two, an ellipsis and the last. */
@Composable
private fun roundsWork(config: IntervalConfig): List<String> {
    val works = (1..config.rounds).map { formatClock(config.workOf(it)) }
    if (works.size <= LISTED_ROUNDS) return works
    return works.take(2) + stringResource(Res.string.common_ellipsis) + works.last()
}

private const val LISTED_ROUNDS = 4
