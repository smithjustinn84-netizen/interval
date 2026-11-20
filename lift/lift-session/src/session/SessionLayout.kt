package dev.justinsmith.lift.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.HeartCard
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.isLandscape
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.density_start
import dev.justinsmith.lift.session.resources.kg_value
import dev.justinsmith.lift.session.resources.next_emom
import dev.justinsmith.lift.session.resources.session_extra_set
import dev.justinsmith.lift.session.resources.session_finish
import dev.justinsmith.lift.session.resources.session_log
import dev.justinsmith.lift.session.resources.session_moved
import dev.justinsmith.lift.session.resources.session_since_last
import dev.justinsmith.lift.session.resources.session_time
import dev.justinsmith.lift.ui.StatTile
import org.jetbrains.compose.resources.stringResource

/** Maximum share of window width the primary column takes on compact landscape phones. */
internal const val LANDSCAPE_PRIMARY_SHARE = 0.45f

/**
 * Whether a session's screens (the session, its summary) are upright on a window wider than a phone, as a tablet
 * held upright is: they keep one column, the width of the Timer's choices rather than a phone's. Two columns
 * there left the fixed one mostly empty between its numbers and its buttons.
 */
internal val BoxWithConstraintsScope.isWideUpright: Boolean
    get() = !isLandscape && maxWidth >= Sizes.setupColumnLandscape + Sizes.listColumn

/** The session screen's parts, which [SessionLayout] lays out in one column or two. */
internal class SessionParts(
    val bar: @Composable () -> Unit,
    /** The session's numbers, in one row when [wide] and two otherwise. */
    val tiles: @Composable (wide: Boolean) -> Unit,
    val plan: @Composable ColumnScope.() -> Unit,
    /** The rest counting down, while one does, over Finish and Log set. */
    val foot: @Composable () -> Unit,
)

/**
 * Upright, one column: the title, then the clock, what is next and what is done scrolling, then the rest and Log
 * set; on a tablet ([isWideUpright]) the column widens and the numbers sit in one row. On its side, the title,
 * the clock, the rest and Log set on the left, and what is next and what is done on the right.
 */
@Composable
internal fun SessionLayout(parts: SessionParts, modifier: Modifier = Modifier) {
    val scrolling = Arrangement.spacedBy(Spacing.space8)
    BoxWithConstraints(
        modifier.fillMaxSize().background(IntervalTheme.palette.background),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (isLandscape) {
            val sideWidth = minOf(Sizes.setupColumnLandscape, maxWidth * LANDSCAPE_PRIMARY_SHARE)
            Row(Modifier.fillMaxSize().safeDrawingPadding(), horizontalArrangement = Arrangement.Center) {
                Column(Modifier.width(sideWidth).fillMaxHeight()) {
                    parts.bar()
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.space16),
                    ) { parts.tiles(false) }
                    parts.foot()
                }
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .widthIn(max = Sizes.contentWide)
                        .fillMaxHeight()
                        .padding(end = Spacing.space16),
                ) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(vertical = Spacing.space16),
                        verticalArrangement = scrolling,
                        content = parts.plan,
                    )
                }
            }
        } else {
            val wide = isWideUpright
            Column(
                Modifier.widthIn(
                    max = if (wide) Sizes.contentWide else Sizes.content,
                ).fillMaxSize().safeDrawingPadding(),
            ) {
                parts.bar()
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.space16),
                    verticalArrangement = scrolling,
                ) {
                    parts.tiles(wide)
                    parts.plan(this)
                }
                parts.foot()
            }
        }
    }
}

/**
 * The session's time and weight moved, and from the first set on how long since the last: in one row when
 * [wide]; under them the live heart rate on its card ([HeartCard]) while a sensor is in use. The rest counts
 * down at the foot ([SessionFoot]), so the tiles keep one shape as it comes and goes.
 */
@Composable
internal fun SessionTiles(state: SessionUiState, clocks: () -> SessionClocks, wide: Boolean) {
    val moved = state.session?.tonnageKg ?: 0.0
    val now = clocks()
    val time = @Composable { modifier: Modifier ->
        StatTile(stringResource(Res.string.session_time), now.elapsed, modifier)
    }
    val weight = @Composable { modifier: Modifier ->
        StatTile(
            stringResource(Res.string.session_moved),
            stringResource(Res.string.kg_value, formatKg(moved)),
            modifier,
            rank = moved.toInt(),
        )
    }
    val third = now.sinceLast?.let { since ->
        @Composable { modifier: Modifier ->
            StatTile(stringResource(Res.string.session_since_last), since, modifier)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                time(Modifier.weight(1f))
                weight(Modifier.weight(1f))
                third?.invoke(Modifier.weight(1f))
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                time(Modifier.weight(1f))
                weight(Modifier.weight(1f))
            }
            third?.invoke(Modifier.fillMaxWidth())
        }
        // The heart, while a sensor is in use: the number, its zone and the session so far.
        HeartCard(now.liveHeartRate, now.liveSamples, maxBpm = now.maxHeartRate)
    }
}

/**
 * The session's foot: the rest counting down while one does ([RestBand]), read from [clocks] here so each tick
 * redraws only the band's bar, over the buttons ([SessionButtons]).
 */
@Composable
internal fun SessionFoot(
    state: SessionUiState,
    clocks: () -> SessionClocks,
    actions: SessionActions,
    onFinish: () -> Unit,
    onOpenSheet: () -> Unit,
) {
    // What the band says changes once a second, and only while a rest runs; its bar moves every tick, read where
    // it is drawn.
    val rest by remember(clocks) { computedStateOf(structuralEqualityPolicy()) { clocks().rest?.copy(progress = 0f) } }
    Column {
        RestBand(
            rest,
            actions.onStepRest,
            actions.onSkipRest,
            progress = remember(clocks) { heldShare { clocks().rest?.progress } },
            modifier = Modifier.padding(start = Spacing.space16, end = Spacing.space16, top = Spacing.space8),
            heart = clocks().restHeart,
        )
        state.partRoundOf?.let { round ->
            PartRound(
                round,
                actions.onLogPartRound,
                Modifier.padding(start = Spacing.space16, end = Spacing.space16, top = Spacing.space8),
            )
        }
        SessionButtons(state, actions, onFinish, onOpenSheet)
    }
}

/**
 * Finish beside Log set (or Start EMOM when an EMOM block is up next, Start the clock when a density block is); once a
 * program day's sets are done, Extra set beside Finish in the accent.
 */
@Composable
private fun SessionButtons(
    state: SessionUiState,
    actions: SessionActions,
    onFinish: () -> Unit,
    onOpenSheet: () -> Unit,
) {
    val next = state.upNext
    Row(
        Modifier.padding(Spacing.space16),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        if (state.dayDone) {
            SecondaryButton(stringResource(Res.string.session_extra_set), onOpenSheet, Modifier.weight(1f))
            AccentButton(stringResource(Res.string.session_finish), onFinish, Modifier.weight(2f))
        } else {
            SecondaryButton(stringResource(Res.string.session_finish), onFinish, Modifier.weight(1f))
            val emom = next?.emomMinutes != null
            val density = next?.densityMinutes != null
            AccentButton(
                stringResource(
                    when {
                        emom -> Res.string.next_emom
                        density -> Res.string.density_start
                        else -> Res.string.session_log
                    },
                ),
                onClick = when {
                    emom || density -> actions.onStartPlannedClock
                    next != null -> actions.onLogNext
                    else -> actions.onLog
                },
                modifier = Modifier.weight(2f),
            )
        }
    }
}
