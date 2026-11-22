package dev.justinsmith.lift.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.computedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.HeartCard
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.RollingText
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.isLandscape
import dev.justinsmith.interval.ui.isLargeText
import dev.justinsmith.interval.ui.rememberRising
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
    /** The session's numbers, in the form the layout has room for. */
    val tiles: @Composable (form: TilesForm) -> Unit,
    val plan: @Composable ColumnScope.() -> Unit,
    /** The rest counting down, while one does, over Finish and Log set. */
    val foot: @Composable () -> Unit,
)

/**
 * Upright, one column: the title, then the clock, what is next and what is done scrolling, then the rest and Log
 * set, the numbers one strip; on a tablet ([isWideUpright]) the column widens and they are a row of tiles. On
 * its side, the title, the clock, the rest and Log set on the left, and what is next and what is done on the
 * right.
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
                    ) { parts.tiles(TilesForm.Stacked) }
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
                    parts.tiles(if (wide) TilesForm.Row else TilesForm.Strip)
                    parts.plan(this)
                }
                parts.foot()
            }
        }
    }
}

/** How the session's numbers are laid out: see [SessionTiles]. */
internal enum class TilesForm { Strip, Stacked, Row }

/**
 * The session's time and weight moved, and from the first set on how long since the last. On a phone upright
 * they are one strip ([TilesForm.Strip]), so the set up next and its steppers stay over the rest band; a tablet
 * upright has a tile each in a row, and a window on its side, whose column holds nothing else, two rows of
 * tiles. Under them the live heart rate on its card ([HeartCard]) while a sensor is in use. The rest counts
 * down at the foot ([SessionFoot]), so the numbers keep one shape as it comes and goes.
 */
@Composable
internal fun SessionTiles(state: SessionUiState, clocks: () -> SessionClocks, form: TilesForm) {
    val moved = state.session?.tonnageKg ?: 0.0
    val now = clocks()
    val stats = listOfNotNull(
        SessionStat(stringResource(Res.string.session_time), now.elapsed),
        SessionStat(
            stringResource(Res.string.session_moved),
            stringResource(Res.string.kg_value, formatKg(moved)),
            rank = moved.toInt(),
        ),
        now.sinceLast?.let { SessionStat(stringResource(Res.string.session_since_last), it) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        when (form) {
            TilesForm.Strip -> StatStrip(stats)

            TilesForm.Row -> TileRow(stats)

            TilesForm.Stacked -> {
                TileRow(stats.take(2))
                stats.getOrNull(2)?.let { StatTile(it.label, it.value, Modifier.fillMaxWidth(), it.rank) }
            }
        }
        // The heart, while a sensor is in use: the number, its zone and the session so far.
        HeartCard(now.liveHeartRate, now.liveSamples, maxBpm = now.maxHeartRate)
    }
}

/** One of the session's numbers under its caption; [rank] as [StatTile] takes it. */
private class SessionStat(val label: String, val value: String, val rank: Int? = null)

@Composable
private fun TileRow(stats: List<SessionStat>) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        stats.forEach { StatTile(it.label, it.value, Modifier.weight(1f), it.rank) }
    }
}

/**
 * The session's numbers on one card, a column each: a third of the height of a tile apiece. With large text a row
 * each, the caption at the start and the value at the end: a third of the width breaks both ("1280 / kg").
 */
@Composable
private fun StatStrip(stats: List<SessionStat>) {
    if (isLargeText()) {
        StatRows(stats)
        return
    }
    val palette = IntervalTheme.palette
    Row(
        Modifier
            .fillMaxWidth()
            // As tall as the tallest column, so a caption that wraps (large text) leaves the values level.
            .height(IntrinsicSize.Min)
            .clip(Corners.radius18)
            .background(palette.surface)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        stats.forEach { stat ->
            Column(
                Modifier.weight(1f).fillMaxHeight().semantics(mergeDescendants = true) {},
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(stat.label, style = IntervalTheme.type.caption, color = palette.textDim)
                if (stat.rank != null) {
                    val rising = rememberRising(stat.rank)
                    RollingText(stat.value, rising = rising, style = IntervalTheme.type.value, color = palette.text)
                } else {
                    Text(stat.value, style = IntervalTheme.type.value, color = palette.text)
                }
            }
        }
    }
}

/** [StatStrip] with large text: a row a number on one card, its caption at the start and its value at the end. */
@Composable
private fun StatRows(stats: List<SessionStat>) {
    val palette = IntervalTheme.palette
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Corners.radius18)
            .background(palette.surface)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        stats.forEach { stat ->
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Text(stat.label, Modifier.weight(1f), style = IntervalTheme.type.caption, color = palette.textDim)
                if (stat.rank != null) {
                    RollingText(
                        stat.value,
                        rising = rememberRising(stat.rank),
                        style = IntervalTheme.type.value,
                        color = palette.text,
                    )
                } else {
                    Text(stat.value, style = IntervalTheme.type.value, color = palette.text)
                }
            }
        }
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
    BoxWithConstraints(Modifier.padding(Spacing.space16)) {
        // The quiet button takes a third, or its word's width if that is more (large text), so it never breaks
        // inside its word ("Finis / h"); the accent one the rest.
        val quiet = Modifier.widthIn(min = (maxWidth - Spacing.space8) / QUIET_SHARE)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            if (state.dayDone) {
                SecondaryButton(stringResource(Res.string.session_extra_set), onOpenSheet, quiet)
                AccentButton(stringResource(Res.string.session_finish), onFinish, Modifier.weight(1f))
            } else {
                SecondaryButton(stringResource(Res.string.session_finish), onFinish, quiet)
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
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** The quiet button's share of the foot's row at an ordinary size: a third. */
private const val QUIET_SHARE = 3
