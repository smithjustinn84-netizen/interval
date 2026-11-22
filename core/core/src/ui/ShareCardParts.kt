package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.heart_rate_zone_short
import dev.justinsmith.core.resources.share_card_heart_rate
import dev.justinsmith.core.resources.share_card_hr_avg_peak
import dev.justinsmith.core.resources.share_card_hr_peak
import dev.justinsmith.core.resources.share_card_week
import dev.justinsmith.core.resources.share_card_week_goal_met
import dev.justinsmith.core.resources.share_card_week_standing
import dev.justinsmith.core.resources.share_card_week_streak
import dev.justinsmith.interval.platform.HeartRateZone
import dev.justinsmith.interval.platform.heartRateZone
import dev.justinsmith.interval.platform.zoneSeconds
import dev.justinsmith.interval.timer.WeekStanding
import org.jetbrains.compose.resources.stringResource

/**
 * Printed industrial telemetry backdrop for [ShareCard]: a dark pine stock surface with a top accent registration
 * bar, subtle calibration dot grid, corner crop brackets, and fine right-margin ruler ticks.
 */
@Composable
internal fun ShareTelemetryBackdrop(modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Spacer(
        modifier
            .fillMaxSize()
            .background(palette.background)
            .drawWithCache {
                val ruleHeight = Sizes.shareHeaderRule.toPx()
                val inset = Spacing.space12.toPx()
                val tick = Sizes.shareCornerTick.toPx()
                val hair = Sizes.hairline.toPx()
                val gridStep = GRID_STEP_DP.dp.toPx()
                val dotRadius = GRID_DOT_RADIUS_DP.dp.toPx()
                val gridColor = palette.line.copy(alpha = Opacity.SHARE_GRID)
                val bracketColor = palette.line.copy(alpha = BRACKET_ALPHA)
                val accentColor = palette.accent
                val rulerTickShort = RULER_TICK_SHORT_DP.dp.toPx()
                val rulerTickMajor = RULER_TICK_MAJOR_DP.dp.toPx()
                onDrawBehind {
                    // Subtle telemetry calibration dot matrix across the print area.
                    var y = inset + gridStep
                    while (y < size.height - inset) {
                        var x = inset + gridStep
                        while (x < size.width - inset) {
                            drawCircle(gridColor, radius = dotRadius, center = Offset(x, y))
                            x += gridStep
                        }
                        y += gridStep
                    }
                    // Top industrial registration accent strip.
                    drawRect(accentColor, topLeft = Offset.Zero, size = Size(size.width, ruleHeight))
                    // Printed corner crop / registration brackets.
                    drawCornerBrackets(inset, tick, hair, bracketColor)
                    // Fine vertical calibration ticks along the right margin.
                    val rulerX = size.width - inset
                    val rulerTop = inset + tick * RULER_MARGIN_FACTOR
                    val rulerBottom = size.height - inset - tick * RULER_MARGIN_FACTOR
                    val span = (rulerBottom - rulerTop).coerceAtLeast(1f)
                    for (i in 0..RULER_TICK_COUNT) {
                        val ty = rulerTop + span * (i.toFloat() / RULER_TICK_COUNT)
                        val len = if (i % RULER_MAJOR_EVERY == 0) rulerTickMajor else rulerTickShort
                        drawLine(gridColor, Offset(rulerX - len, ty), Offset(rulerX, ty), strokeWidth = hair)
                    }
                }
            },
    )
}

private fun DrawScope.drawCornerBrackets(inset: Float, tick: Float, stroke: Float, color: Color) {
    val right = size.width - inset
    val bottom = size.height - inset
    // Top-left
    drawLine(color, Offset(inset, inset), Offset(inset + tick, inset), stroke)
    drawLine(color, Offset(inset, inset), Offset(inset, inset + tick), stroke)
    // Top-right
    drawLine(color, Offset(right - tick, inset), Offset(right, inset), stroke)
    drawLine(color, Offset(right, inset), Offset(right, inset + tick), stroke)
    // Bottom-left
    drawLine(color, Offset(inset, bottom - tick), Offset(inset, bottom), stroke)
    drawLine(color, Offset(inset, bottom), Offset(inset + tick, bottom), stroke)
    // Bottom-right
    drawLine(color, Offset(right - tick, bottom), Offset(right, bottom), stroke)
    drawLine(color, Offset(right, bottom - tick), Offset(right, bottom), stroke)
}

/**
 * Subtle watermark silhouette of [rep] rendered directly via [DemoStage] and [drawCanvasScene] (`DemoPainter.kt`),
 * composited into an offscreen layer at [Opacity.SHARE_WATERMARK] so cutaway halos stay crisp over the card's
 * dark telemetry stock. Freezes at [progress] (or [ExerciseRep.still] when null).
 */
@Composable
internal fun ShareMovementWatermark(
    rep: ExerciseRep,
    progress: Float?,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val colors = remember(palette) {
        ExerciseDemoColors(
            body = palette.text,
            far = palette.textDim,
            joint = palette.background,
            reference = palette.line,
            bell = palette.accent,
            shadow = Color.Transparent,
            set = palette.accent,
            drive = palette.accent,
            ease = palette.accent,
        )
    }
    val at = (progress ?: rep.still).coerceIn(rep.earliest, 1f)
    Spacer(
        modifier
            .size(Sizes.shareWatermarkWidth, Sizes.shareWatermarkHeight)
            .graphicsLayer {
                alpha = Opacity.SHARE_WATERMARK
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithCache {
                if (size.minDimension <= 0f) return@drawWithCache onDrawBehind {}
                val stage = DemoStage(rep, size, DemoDisplayMode.Detail, colors.reference, this)
                val faintBell = colors.bell.copy(alpha = WATERMARK_TRAJECTORY_ALPHA)
                onDrawBehind {
                    stage.frameFor(at)
                    val joints = stage.jointsAt(at)
                    val clip = stage.clipRect
                    if (clip != null) {
                        clipRect(clip.left, clip.top, clip.right, clip.bottom) {
                            drawCanvasScene(
                                rep,
                                stage,
                                joints,
                                at,
                                colors.bell,
                                faintBell,
                                colors,
                                DemoDisplayMode.Detail,
                            )
                        }
                    } else {
                        drawCanvasScene(
                            rep,
                            stage,
                            joints,
                            at,
                            colors.bell,
                            faintBell,
                            colors,
                            DemoDisplayMode.Detail,
                        )
                    }
                }
            },
    )
}

/**
 * Compact circular week standing badge combining `WeekRing`'s standing readout with [ProgressRing]'s
 * [RING_SKSL] dial shader: etched goal ticks round the band, graded gold sweep from 12 o'clock, outer
 * dashed track lane, tabular Barlow Condensed standing count, and a subtle streak or week caption.
 */
@Composable
internal fun ShareWeekBadge(standing: WeekStanding, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val goal = standing.goal?.takeIf { it > 0 }
    val sweep = if (goal != null) (standing.count.toFloat() / goal).coerceIn(0f, 1f) else 0f
    val figure = if (goal != null) {
        stringResource(Res.string.share_card_week_standing, standing.count, goal)
    } else {
        standing.count.toString()
    }
    val caption = when {
        standing.streak > 0 -> stringResource(Res.string.share_card_week_streak, standing.streak)
        standing.goalMet -> stringResource(Res.string.share_card_week_goal_met)
        else -> stringResource(Res.string.share_card_week)
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(Sizes.shareWeekRing), contentAlignment = Alignment.Center) {
            ShareWeekRingDial(sweep = sweep, goal = goal, goalMet = standing.goalMet)
            Text(
                figure,
                style = IntervalTheme.type.control.copy(
                    fontSize = BADGE_FIGURE_SP.sp,
                    lineHeight = BADGE_FIGURE_SP.sp,
                    fontFeatureSettings = TABULAR_NUMERALS,
                ),
                color = palette.text,
                maxLines = 1,
            )
        }
        Text(
            caption.uppercase(),
            Modifier.padding(top = Spacing.space4),
            style = IntervalTheme.type.caption.copy(
                fontSize = MICRO_LABEL_SP.sp,
                lineHeight = MICRO_LABEL_LINE_SP.sp,
                letterSpacing = TRACKING_WIDE_SP.sp,
                fontFeatureSettings = TABULAR_NUMERALS,
            ),
            color = if (standing.goalMet || standing.streak > 0) palette.accent else palette.textDim,
            maxLines = 1,
        )
    }
}

@Composable
private fun ShareWeekRingDial(sweep: Float, goal: Int?, goalMet: Boolean) {
    val palette = IntervalTheme.palette
    val workout = IntervalTheme.workout
    val program = remember { shaderProgram(RING_SKSL) }
    val ticks = remember(goal) {
        if (goal != null && goal in 2..MAX_WEEK_TICKS) DialTicks(count = goal.toFloat(), majorEvery = 1) else null
    }
    val heat = if (goalMet) GOAL_MET_HEAT else 0f
    val accent = palette.accent
    val track = workout.upcoming
    val lane = palette.line.copy(alpha = BADGE_LANE_ALPHA)
    Spacer(
        Modifier
            .fillMaxSize()
            .drawWithCache {
                val strokePx = Sizes.shareWeekRingStroke.toPx()
                val outerGapPx = Sizes.shareWeekRingOuterGap.toPx()
                val hairPx = Sizes.hairline.toPx()
                val dashed = Stroke(
                    width = hairPx,
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(Sizes.dayDash.toPx(), Sizes.dayDashGap.toPx()),
                    ),
                )
                val shader = program?.let { RingShaderPaint(it, hairPx) }
                shader?.dial(ticks)
                onDrawBehind {
                    val outerDiameter = size.minDimension - hairPx
                    if (outerDiameter > 0f) {
                        val topLeft = Offset((size.width - outerDiameter) / 2f, (size.height - outerDiameter) / 2f)
                        drawArc(
                            lane,
                            0f,
                            Angles.FULL_TURN,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(outerDiameter, outerDiameter),
                            style = dashed,
                        )
                    }
                    val bandDiameter = (size.minDimension - strokePx - outerGapPx * 2f).coerceAtLeast(1f)
                    if (shader != null) {
                        shader.urgency(heat, beat = 0f)
                        shader.colors(accent, track, etch = workout.onGround, mark = workout.onFill(accent))
                        with(shader) { drawBand(center, bandDiameter / 2f, strokePx, sweep) }
                    } else {
                        drawWeekBadgeFallback(bandDiameter, strokePx, sweep, accent, track)
                    }
                }
            },
    )
}

private fun DrawScope.drawWeekBadgeFallback(
    diameter: Float,
    strokePx: Float,
    sweep: Float,
    accent: Color,
    track: Color,
) {
    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
    val arcSize = Size(diameter, diameter)
    drawArc(track, 0f, Angles.FULL_TURN, false, topLeft, arcSize, style = Stroke(strokePx))
    if (sweep > 0f) {
        drawArc(
            accent,
            Angles.TWELVE_O_CLOCK,
            Angles.FULL_TURN * sweep,
            false,
            topLeft,
            arcSize,
            style = Stroke(strokePx, cap = StrokeCap.Round),
        )
    }
}

/**
 * Recessed industrial telemetry panel compositing [HeartRateChart] (with [HEART_RATE_CHART_SHADER] zone banding),
 * a peak-zone header with tabular BPM readouts, and a proportional 5-zone spectrum bar at its base.
 */
@Composable
internal fun ShareHeartRatePanel(heartRate: ShareHeartRate, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val resting = LocalRestingHeartRate.current
    val peak = heartRate.peakBpm
    val average = heartRate.averageBpm
    val peakZone = peak?.let { heartRateZone(it, heartRate.maxBpm, resting) }
    val zoneColor = peakZone?.let { palette.heartZones.of(it) } ?: palette.accent
    val panelSurface = palette.surface.copy(alpha = Opacity.SHARE_TELEMETRY_PANEL)
    Column(
        modifier
            .fillMaxWidth()
            .clip(Corners.radius12)
            .background(panelSurface)
            .border(Sizes.hairline, palette.line, Corners.radius12)
            .padding(horizontal = Spacing.space12, vertical = Spacing.space8),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
            ) {
                Box(Modifier.size(Sizes.heartBadgeDot).clip(Corners.full).background(zoneColor))
                Text(
                    stringResource(Res.string.share_card_heart_rate).uppercase(),
                    style = IntervalTheme.type.caption.copy(
                        fontSize = MICRO_LABEL_SP.sp,
                        lineHeight = MICRO_LABEL_LINE_SP.sp,
                        letterSpacing = TRACKING_WIDE_SP.sp,
                    ),
                    color = palette.textDim,
                    maxLines = 1,
                )
                peakZone?.let { zone ->
                    Text(
                        stringResource(Res.string.heart_rate_zone_short, zone.number),
                        style = IntervalTheme.type.caption.copy(
                            fontSize = MICRO_LABEL_SP.sp,
                            lineHeight = MICRO_LABEL_LINE_SP.sp,
                            fontFeatureSettings = TABULAR_NUMERALS,
                        ),
                        color = zoneColor,
                        maxLines = 1,
                    )
                }
            }
            if (peak != null) {
                val summary = if (average != null) {
                    stringResource(Res.string.share_card_hr_avg_peak, average, peak)
                } else {
                    stringResource(Res.string.share_card_hr_peak, peak)
                }
                Text(
                    summary.uppercase(),
                    style = IntervalTheme.type.section.copy(
                        fontSize = HR_SUMMARY_SP.sp,
                        lineHeight = HR_SUMMARY_SP.sp,
                        fontFeatureSettings = TABULAR_NUMERALS,
                    ),
                    color = palette.text,
                    maxLines = 1,
                )
            }
        }
        HeartRateChart(
            samples = heartRate.samples,
            maxBpm = heartRate.maxBpm,
            marks = heartRate.marks,
            height = Sizes.shareHeartChart,
            surfaceColor = palette.surface,
        )
        ShareZoneSpectrum(heartRate.samples, heartRate.maxBpm)
    }
}

/** Proportional 5-zone color strip beneath [HeartRateChart] showing the workout's zone distribution at a glance. */
@Composable
private fun ShareZoneSpectrum(samples: List<Int>, maxBpm: Int, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val resting = LocalRestingHeartRate.current
    val totals = remember(samples, maxBpm, resting) { zoneSeconds(samples, maxBpm, restingBpm = resting) }
    val totalSeconds = totals.values.sum()
    if (totalSeconds <= 0) return
    Row(
        modifier.fillMaxWidth().height(Sizes.shareZoneStrip).clip(Corners.full),
        horizontalArrangement = Arrangement.spacedBy(Sizes.lapGap),
    ) {
        for (zone in HeartRateZone.entries) {
            val seconds = totals[zone] ?: 0
            if (seconds > 0) {
                Box(
                    Modifier
                        .weight(seconds.toFloat() / totalSeconds)
                        .height(Sizes.shareZoneStrip)
                        .background(palette.heartZones.of(zone)),
                )
            }
        }
    }
}

internal const val TABULAR_NUMERALS = "tnum"
private const val GRID_STEP_DP = 18f
private const val GRID_DOT_RADIUS_DP = 0.7f
private const val BRACKET_ALPHA = 0.45f
private const val RULER_MARGIN_FACTOR = 2.4f
private const val RULER_TICK_COUNT = 20
private const val RULER_MAJOR_EVERY = 5
private const val RULER_TICK_SHORT_DP = 3f
private const val RULER_TICK_MAJOR_DP = 6f
private const val WATERMARK_TRAJECTORY_ALPHA = 0.55f
private const val MAX_WEEK_TICKS = 7
private const val GOAL_MET_HEAT = 0.28f
private const val BADGE_LANE_ALPHA = 0.65f
private const val BADGE_FIGURE_SP = 17f
private const val MICRO_LABEL_SP = 10f
private const val MICRO_LABEL_LINE_SP = 12f
private const val TRACKING_WIDE_SP = 0.8f
private const val HR_SUMMARY_SP = 13f
