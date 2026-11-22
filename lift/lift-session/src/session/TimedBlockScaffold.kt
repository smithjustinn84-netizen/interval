package dev.justinsmith.lift.session

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import dev.justinsmith.interval.platform.KeepScreenOn
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.CountdownClock
import dev.justinsmith.interval.ui.ExerciseDemoColors
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LARGE_FONT_SCALE
import dev.justinsmith.interval.ui.Motion
import dev.justinsmith.interval.ui.ProgressRing
import dev.justinsmith.interval.ui.RingDial
import dev.justinsmith.interval.ui.RingZone
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Shockwave
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TextScaleAtMost
import dev.justinsmith.interval.ui.isLandscape
import dev.justinsmith.interval.ui.ringClockSize
import dev.justinsmith.interval.ui.surfaceButton
import dev.justinsmith.interval.ui.windowIsRoomy
import dev.justinsmith.lift.domain.EmomClock
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.common_cancel
import dev.justinsmith.lift.session.resources.common_joined
import dev.justinsmith.lift.session.resources.emom_next
import dev.justinsmith.lift.session.resources.emom_stop
import dev.justinsmith.lift.session.resources.next_figure_cd
import dev.justinsmith.lift.ui.LiftSizes
import dev.justinsmith.lift.ui.demo.ExerciseGhost
import dev.justinsmith.lift.ui.demo.rep
import dev.justinsmith.lift.ui.liftName
import org.jetbrains.compose.resources.stringResource

/**
 * Where a timed block stands, worded: what [TimedBlockScaffold] draws of an EMOM or a density block. [color] is
 * the phase's, which the page crossfades to; [beat] changes at each moment a haptic marks (a phase, a minute, a
 * round); [clockKey] at each interval the clock counts down afresh. [working] is while [action] is live, [over]
 * once the block is done and Stop no longer asks. As it is [landed] (an EMOM's last minute's set done) the ring
 * is struck: a [Shockwave] out from the clock to the ring, and the beat firmer under the thumb.
 */
@Immutable
internal data class TimedBlock(
    val color: Color,
    val phaseWord: String,
    val heading: String,
    val task: TaskWords,
    val action: String,
    val stopTitle: String,
    val stopText: String,
    val secondsLeft: Int,
    val clockKey: Any,
    val beat: Any,
    val dial: RingDial?,
    val working: Boolean,
    val over: Boolean,
    val largeAction: Boolean = false,
    /** Whether its last set is done with time still on the clock: the block is in the bag. */
    val landed: Boolean = false,
)

/** The set or round to do, as read aloud ([plain]) and as drawn, its load and reps kept on one line ([drawn]). */
@Immutable
internal data class TaskWords(val plain: String, val drawn: String)

/** "Two-hand swing · 24 kg × 10", or with [next] "Next: 24 kg × 10", for the rest before it. */
@Composable
internal fun taskWords(name: String, task: String, next: Boolean = false): TaskWords {
    val kept = task.replace(' ', ' ')
    return if (next) {
        TaskWords(stringResource(Res.string.emom_next, task), stringResource(Res.string.emom_next, kept))
    } else {
        TaskWords(
            stringResource(Res.string.common_joined, name, task),
            stringResource(Res.string.common_joined, name, kept),
        )
    }
}

/**
 * A timed block's page, in the workout's look: the phase colour on the band, the ring (the interval going by)
 * with the clock, the phase word and the task, and one big [TimedBlock.action] at the foot. Space or Enter does
 * the action while it is live, Escape asks to stop, as Stop does until the block is over. A haptic marks each
 * [TimedBlock.beat]; the beeps come from the session's timers. [heart] is the live heart rate, at the end of the
 * top row while a sensor is in use; [tally] is drawn under the ring (beside it, sideways), as a density block's
 * rounds are, and [figure] before it on the same line: the block's lift, done by a ghost ([BlockFigure]). [zone]
 * is the heart rate's zone, which the ring wears as a bezel round its dial.
 */
@Composable
internal fun TimedBlockScaffold(
    block: TimedBlock,
    progress: () -> Float,
    onAction: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    heart: @Composable () -> Unit = {},
    tally: @Composable (Modifier) -> Unit = {},
    figure: @Composable (Modifier) -> Unit = {},
    zone: RingZone? = null,
) {
    val colors = IntervalTheme.workout
    // Read where it is drawn or written, so a crossfade's frames recompose only what shows it.
    val colorState = animateColorAsState(block.color, tween(Motion.PHASE_CROSSFADE_MILLIS))
    val color = remember(colorState) { { colorState.value } }
    var confirmingStop by rememberSaveable { mutableStateOf(false) }
    val requestStop = { if (!block.over) confirmingStop = true }
    val focus = remember { FocusRequester() }
    var rootFocused by remember { mutableStateOf(false) }
    LaunchedEffect(confirmingStop) { if (!confirmingStop) focus.requestFocus() }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(block.beat) {
        val firm = block.over || block.landed
        haptics.performHapticFeedback(if (firm) HapticFeedbackType.LongPress else HapticFeedbackType.Confirm)
    }
    KeepScreenOn()
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(colors.ground)
            .focusRequester(focus)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable()
            .timedBlockKeys(
                enabled = !confirmingStop && !block.over,
                canAct = rootFocused && block.working,
                onAction = onAction,
                onStop = requestStop,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        // A tablet has the height for the readout inside the ring either way up, and room for a bigger ring.
        val roomy = windowIsRoomy()
        if (isLandscape && !roomy) {
            val sideWidth = minOf(Sizes.setupColumnLandscape, maxWidth * LANDSCAPE_PRIMARY_SHARE)
            LandscapeBlock(block, color, progress, sideWidth, onAction, requestStop, heart, tally, figure, zone)
        } else {
            PortraitBlock(block, color, progress, onAction, requestStop, roomy, heart, tally, figure, zone)
        }
    }
    if (confirmingStop && !block.over) {
        ConfirmDialog(
            title = block.stopTitle,
            text = block.stopText,
            confirm = stringResource(Res.string.emom_stop),
            dismiss = stringResource(Res.string.common_cancel),
            onConfirm = {
                confirmingStop = false
                onStop()
            },
            onDismiss = { confirmingStop = false },
            destructive = true,
        )
    }
}

private fun Modifier.timedBlockKeys(
    enabled: Boolean,
    canAct: Boolean,
    onAction: () -> Unit,
    onStop: () -> Unit,
): Modifier = onKeyEvent { event ->
    if (!enabled || event.type != KeyEventType.KeyDown) return@onKeyEvent false
    when (event.key) {
        Key.Spacebar, Key.Enter, Key.NumPadEnter -> if (canAct) {
            onAction()
            true
        } else {
            false
        }

        Key.Escape -> {
            onStop()
            true
        }

        else -> false
    }
}

/**
 * Upright, and on a tablet either way up: the band, Stop and the heading across the top, the ring with the
 * readout inside, the figure and the tally on a line, the action at the foot. On a tablet ([roomy]) the ring
 * grows past a phone's widest, and the clock in it to the workout's size.
 */
@Composable
private fun PortraitBlock(
    block: TimedBlock,
    color: () -> Color,
    progress: () -> Float,
    onAction: () -> Unit,
    onStop: () -> Unit,
    roomy: Boolean,
    heart: @Composable () -> Unit,
    tally: @Composable (Modifier) -> Unit,
    figure: @Composable (Modifier) -> Unit,
    zone: RingZone?,
) {
    Column(
        Modifier.widthIn(max = Sizes.contentWide).fillMaxSize().safeDrawingPadding()
            .padding(horizontal = Spacing.space16),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.fillMaxWidth().height(Sizes.phaseBand).clip(Corners.full).drawBehind { drawRect(color()) })
        TopRow(block, onStop, heart, Modifier.fillMaxWidth().padding(top = Spacing.space8))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val diameter = minOf(maxWidth, maxHeight, if (roomy) LiftSizes.ringMaxRoomy else Sizes.ringMax)
            Ring(block, color, progress, diameter, zone) { heat ->
                // The ring's words keep the size it is drawn for, as the clock does, sized by the ring alone:
                // larger, the word sits on the stroke and the line under the clock is cut ("24 kg × 10").
                TextScaleAtMost(1f) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PhaseWord(block, color)
                        val type = IntervalTheme.type
                        ClockText(block, color, diameter, if (roomy) type.clock else type.display, heat)
                        TaskText(
                            block.task,
                            Modifier.padding(horizontal = Sizes.ring + Spacing.space32),
                            centered = true,
                        )
                    }
                }
            }
        }
        FigureAndTally(figure, tally, clearBelow = true)
        ActionButton(block, color, onAction, Modifier.fillMaxWidth().padding(bottom = Spacing.space16))
    }
}

/**
 * Sideways, as the workout lies: the band down the start edge, the clock alone in the ring, and beside it a
 * column of Stop and the heading, the phase word, the task, the figure and the tally, and the action at its
 * foot. A phone on
 * its side is too short for the readout inside the ring.
 */
@Composable
private fun LandscapeBlock(
    block: TimedBlock,
    color: () -> Color,
    progress: () -> Float,
    sideWidth: Dp,
    onAction: () -> Unit,
    onStop: () -> Unit,
    heart: @Composable () -> Unit,
    tally: @Composable (Modifier) -> Unit,
    figure: @Composable (Modifier) -> Unit,
    zone: RingZone?,
) {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .safeDrawingPadding()
                .fillMaxHeight()
                .width(Sizes.phaseBand)
                .clip(Corners.full)
                .drawBehind { drawRect(color()) },
        )
        Row(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = Sizes.phaseBand + Spacing.space8, end = Spacing.space20)
                .padding(vertical = Spacing.space16),
            horizontalArrangement = Arrangement.spacedBy(Spacing.space16),
        ) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                val diameter = minOf(maxWidth, maxHeight, Sizes.ringMax)
                Ring(block, color, progress, diameter, zone) { heat ->
                    ClockText(block, color, diameter, IntervalTheme.type.clock, heat)
                }
            }
            Column(
                Modifier.width(sideWidth).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                TopRow(block, onStop, heart)
                Spacer(Modifier.weight(1f))
                PhaseWord(block, color)
                TaskText(block.task)
                FigureAndTally(figure, tally)
                Spacer(Modifier.weight(1f))
                ActionButton(block, color, onAction, Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * The block's figure and its tally on one line: the ghost beside the rounds, or alone. With [clearBelow] each
 * keeps room under itself, over the action, so the line takes none with neither on it.
 */
@Composable
private fun FigureAndTally(
    figure: @Composable (Modifier) -> Unit,
    tally: @Composable (Modifier) -> Unit,
    clearBelow: Boolean = false,
) {
    val each = if (clearBelow) Modifier.padding(bottom = Spacing.space16) else Modifier
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space16),
    ) {
        figure(each)
        tally(each)
    }
}

/**
 * A timed block's lift done by a ghost ([ExerciseGhost]) in the workout's colours, [TimedBlockScaffold]'s figure:
 * the movement in the corner of the eye through the count-in, the work and the rest, [still] once the block is
 * over. Nothing for a lift with no figure.
 */
@Composable
internal fun BlockFigure(exercise: Exercise, double: Boolean, still: Boolean, modifier: Modifier = Modifier) {
    val rep = exercise.rep ?: return
    ExerciseGhost(
        rep,
        modifier,
        contentDescription = stringResource(Res.string.next_figure_cd, liftName(exercise, double)),
        paused = still,
        colors = ExerciseDemoColors.workout(),
    )
}

/** Stop, the heading ("Minute 3 of 10", "20 min cap") and the live heart rate. */
@Composable
private fun TopRow(
    block: TimedBlock,
    onStop: () -> Unit,
    heart: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton(stringResource(Res.string.emom_stop), onStop, Modifier.widthIn(min = Sizes.pillWidth))
        Text(
            block.heading,
            style = IntervalTheme.type.label,
            color = IntervalTheme.workout.onGround,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.space8),
        )
        heart()
    }
}

/**
 * The ring, a [diameter] across, filling as [progress] goes, with [content] inside handed the dial's heat; with a
 * [zone] its dial is a bezel of the live heart rate, as the interval workout's is.
 */
@Composable
private fun Ring(
    block: TimedBlock,
    color: () -> Color,
    progress: () -> Float,
    diameter: Dp,
    zone: RingZone?,
    content: @Composable (heat: () -> Float) -> Unit,
) {
    // A block already landed as its page opens (one come back to) is not struck again.
    val landedAtOpen = remember { block.landed }
    Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
        val dial = block.dial
        ProgressRing(
            progress,
            color,
            Modifier.fillMaxSize(),
            glideMillis = Motion.EMOM_RING_GLIDE_MILLIS,
            dial = dial,
            zone = zone,
        )
        // Out from the clock and spent as it meets the ring, under the readout.
        if (block.landed && !landedAtOpen) Shockwave(block.color, Modifier.fillMaxSize(), reach = diameter / 2)
        content { dial?.heat(progress()) ?: 0f }
    }
}

/** The action (Set done, Round done), in the phase colour and live only while working. */
@Composable
private fun ActionButton(block: TimedBlock, color: () -> Color, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val colors = IntervalTheme.workout
    val fill = color()
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .heightIn(min = if (block.largeAction) LiftSizes.roundDone else Sizes.control)
            .surfaceButton(
                interaction,
                Corners.radius18,
                if (block.working) fill else colors.control,
                onClick = onAction,
                enabled = block.working,
            )
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        contentAlignment = Alignment.Center,
    ) {
        val textColor = if (block.working) colors.onFill(fill) else colors.onGroundDim
        if (block.largeAction) {
            // Its word grows no further than large text begins, on one line: past that it takes two, the button
            // half the screen.
            TextScaleAtMost(LARGE_FONT_SCALE) {
                Text(block.action, style = IntervalTheme.type.stat, color = textColor, maxLines = 1)
            }
        } else {
            Text(block.action, style = IntervalTheme.type.value, color = textColor)
        }
    }
}

/** The phase word, in the phase colour. */
@Composable
private fun PhaseWord(block: TimedBlock, color: () -> Color) {
    Text(
        block.phaseWord,
        style = IntervalTheme.type.stat,
        color = color(),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * The seconds left in the interval (or the count-in), popping on its last three as the beeps go. It fills the
 * share of a ring [diameter] across its digits allow, up to the size of its [style]: on a phone the display
 * style between the phase word and the task, the clock style alone in the ring or in a tablet's.
 */
@Composable
private fun ClockText(block: TimedBlock, color: () -> Color, diameter: Dp, style: TextStyle, heat: () -> Float) {
    val text = formatClock(block.secondsLeft)
    val size = ringClockSize(diameter, text.length, style.fontSize)
    CountdownClock(
        text,
        intervalKey = block.clockKey,
        countingDown = !block.over && block.secondsLeft in 1..EmomClock.COUNTDOWN,
        accent = color(),
        style = style.copy(fontSize = size, lineHeight = size),
        heat = heat,
    )
}

/** The task, read as its plain words. */
@Composable
private fun TaskText(task: TaskWords, modifier: Modifier = Modifier, centered: Boolean = false) {
    Text(
        task.drawn,
        style = IntervalTheme.type.label,
        color = IntervalTheme.workout.onGroundDim,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = modifier.clearAndSetSemantics { this.text = AnnotatedString(task.plain) },
    )
}
