package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.share_as_text
import dev.justinsmith.core.resources.share_card_cd
import dev.justinsmith.core.resources.share_card_cycle_pose
import dev.justinsmith.core.resources.share_card_mark
import dev.justinsmith.core.resources.share_card_toggle_figure
import dev.justinsmith.core.resources.share_card_toggle_hr
import dev.justinsmith.core.resources.share_card_toggle_week
import dev.justinsmith.core.resources.share_picture
import dev.justinsmith.core.resources.share_sheet_title
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.timer.WeekStanding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * A telemetry figure on a [ShareCard]: its [value] in tabular Barlow Condensed, an optional inline [unit]
 * ("kg", "bpm", "kcal"), and its uppercase [label] beneath it ("1,440" + "kg" over "Volume").
 */
@Immutable
data class ShareStat(
    val value: String,
    val label: String,
    val unit: String? = null,
    val accent: Boolean = false,
)

/**
 * Heart-rate telemetry composited onto a [ShareCard]: 5-second [samples] drawn through [HeartRateChart] with
 * its [HEART_RATE_CHART_SHADER] zone banding, keyed to [maxBpm], with optional work-interval [marks].
 */
@Immutable
data class ShareHeartRate(
    val samples: List<Int>,
    val maxBpm: Int = DEFAULT_MAX_BPM,
    val marks: List<ClosedFloatingPointRange<Float>> = emptyList(),
) {
    val validSamples: List<Int> get() = samples.filter { it in HeartRate.BPM_RANGE }
    val hasChart: Boolean get() = validSamples.size >= 2
    val peakBpm: Int? get() = validSamples.maxOrNull()
    val averageBpm: Int? get() = validSamples.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
}

/**
 * Interactive display configuration for a [ShareCard], controlled live in [ShareCardSheet] before PNG capture:
 * toggle individual telemetry layers and scrub [movementProgress] through the primary movement's kinematic rep.
 */
@Immutable
data class ShareCardConfig(
    val showHeartRate: Boolean = true,
    val showWeekBadge: Boolean = true,
    val showWatermark: Boolean = true,
    /** Normalized kinematic phase in `[0, 1]`, or null to freeze at the movement's iconic [ExerciseRep.still]. */
    val movementProgress: Float? = null,
)

/**
 * A finished workout or session as an industrial telemetry readout picture to send: [eyebrow] (what it was and
 * when) over its [title] and [subtitle], an optional [week] standing badge (`WeekRing` / `ProgressRing` dial
 * shader), a subtle watermark silhouette of [movement] rendered via `DemoPainter`, [art] (timeline waveform,
 * records, or lift summary), a [heartRate] telemetry module with [HeartRateChart] and its zone-banding shader,
 * tabular Barlow Condensed [stats], and the app's colophon at its foot.
 *
 * Always dark and always laid out at [Sizes.shareCardWidth] by [Sizes.shareCardHeight] at a fixed density with
 * `fontScale = 1f`, so the exported PNG is 1080 by 1350 pixels (4:5) on every device and text scale.
 */
@Composable
fun ShareCard(
    eyebrow: String,
    title: String,
    stats: List<ShareStat>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    week: WeekStanding? = null,
    heartRate: ShareHeartRate? = null,
    movement: ExerciseRep? = null,
    config: ShareCardConfig = ShareCardConfig(),
    art: @Composable BoxScope.() -> Unit = {},
) {
    CompositionLocalProvider(
        LocalDensity provides Density(SHARE_CARD_DENSITY, fontScale = 1f),
        LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
        IntervalTheme(darkTheme = true) {
            val showHeart = heartRate != null && heartRate.hasChart && config.showHeartRate
            val showWeek = week != null && config.showWeekBadge
            val showWatermark = movement != null && config.showWatermark
            Box(modifier.size(Sizes.shareCardWidth, Sizes.shareCardHeight)) {
                ShareTelemetryBackdrop()
                if (showWatermark) {
                    val watermarkAlign = if (showHeart) Alignment.TopEnd else Alignment.CenterEnd
                    val watermarkTop = if (showHeart) Spacing.space32 + Spacing.space24 else Spacing.space4
                    ShareMovementWatermark(
                        rep = movement,
                        progress = config.movementProgress,
                        modifier = Modifier
                            .align(watermarkAlign)
                            .padding(top = watermarkTop, end = Spacing.space8),
                    )
                }
                ShareCardBody(
                    eyebrow = eyebrow,
                    title = title,
                    subtitle = subtitle,
                    week = week.takeIf { showWeek },
                    heartRate = heartRate.takeIf { showHeart },
                    stats = stats,
                    art = art,
                )
            }
        }
    }
}

@Composable
private fun ShareCardBody(
    eyebrow: String,
    title: String,
    subtitle: String?,
    week: WeekStanding?,
    heartRate: ShareHeartRate?,
    stats: List<ShareStat>,
    art: @Composable BoxScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(
                start = Spacing.space20,
                end = Spacing.space20,
                top = Spacing.space20,
                bottom = Spacing.space16,
            ),
    ) {
        ShareHeader(eyebrow, title, subtitle, week)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8, Alignment.CenterVertically),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = heartRate == null),
                contentAlignment = Alignment.Center,
                content = art,
            )
            heartRate?.let { ShareHeartRatePanel(it) }
        }
        if (stats.isNotEmpty()) ShareStats(stats)
        ShareMark()
    }
}

@Composable
private fun ShareHeader(eyebrow: String, title: String, subtitle: String?, week: WeekStanding?) {
    val palette = IntervalTheme.palette
    val type = IntervalTheme.type
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                eyebrow.uppercase(),
                style = type.caption.copy(
                    letterSpacing = EYEBROW_TRACKING_SP.sp,
                    fontFeatureSettings = TABULAR_NUMERALS,
                ),
                color = palette.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                title,
                Modifier.padding(top = Spacing.space4),
                style = type.title.copy(fontSize = CARD_TITLE_SP.sp, lineHeight = CARD_TITLE_LINE_SP.sp),
                color = palette.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                JoinedText(
                    it,
                    type.meta.copy(fontFeatureSettings = TABULAR_NUMERALS),
                    palette.textDim,
                    Modifier.padding(top = Spacing.space4),
                )
            }
        }
        week?.let { ShareWeekBadge(it) }
    }
}

@Composable
private fun ShareStats(stats: List<ShareStat>) {
    val palette = IntervalTheme.palette
    val compact = stats.size >= COMPACT_STAT_COUNT
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.fillMaxWidth().height(Sizes.hairline).background(palette.line))
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.space12),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            stats.forEachIndexed { index, stat ->
                if (index > 0) {
                    Spacer(
                        Modifier
                            .padding(horizontal = if (compact) Spacing.space4 else Spacing.space8)
                            .width(Sizes.hairline)
                            .height(Sizes.shareStatDividerHeight)
                            .background(palette.line.copy(alpha = STAT_DIVIDER_ALPHA)),
                    )
                }
                ShareStatCell(stat, compact, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ShareStatCell(stat: ShareStat, compact: Boolean, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val type = IntervalTheme.type
    val valueSize = if (compact) STAT_VALUE_COMPACT_SP.sp else STAT_VALUE_SP.sp
    val unitSize = if (compact) STAT_UNIT_COMPACT_SP.sp else STAT_UNIT_SP.sp
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(if (compact) Spacing.space4 / 2 else Spacing.space4),
        ) {
            Text(
                stat.value,
                style = type.stat.copy(
                    fontSize = valueSize,
                    lineHeight = valueSize,
                    fontFeatureSettings = TABULAR_NUMERALS,
                ),
                color = if (stat.accent) palette.accent else palette.text,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            stat.unit?.let { unit ->
                Text(
                    unit.uppercase(),
                    Modifier.padding(bottom = if (compact) Spacing.space4 / 2 else Spacing.space4),
                    style = type.section.copy(
                        fontSize = unitSize,
                        lineHeight = unitSize,
                        fontFeatureSettings = TABULAR_NUMERALS,
                    ),
                    color = palette.textDim,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        Text(
            stat.label.uppercase(),
            Modifier.padding(top = Spacing.space4),
            style = type.caption.copy(
                fontSize = STAT_LABEL_SP.sp,
                lineHeight = STAT_LABEL_LINE_SP.sp,
                letterSpacing = STAT_LABEL_TRACKING_SP.sp,
            ),
            color = palette.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** The foot: a hairline, then the app's name in varsity gold beside the three-phase telemetry mark. */
@Composable
private fun ShareMark() {
    val palette = IntervalTheme.palette
    Column(Modifier.fillMaxWidth()) {
        Spacer(
            Modifier.padding(top = Spacing.space12).fillMaxWidth().height(Sizes.hairline).background(palette.line),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = Spacing.space8),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(Res.string.share_card_mark).uppercase(),
                style = IntervalTheme.type.control.copy(
                    fontSize = FOOTER_MARK_SP.sp,
                    lineHeight = FOOTER_MARK_SP.sp,
                    letterSpacing = FOOTER_TRACKING_SP.sp,
                ),
                color = palette.accent,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space4)) {
                val swatch = Modifier.size(Sizes.coachDot, Sizes.shareHeaderRule).clip(Corners.full)
                Box(swatch.background(palette.prepMark))
                Box(swatch.background(palette.workMark))
                Box(swatch.background(palette.restMark))
            }
        }
    }
}

/**
 * Share's interactive sheet: the [card] as it will be sent, scaled to fit, optional telemetry layer toggles and
 * horizontal kinematic pose scrubbing when [hasHeartRate], [hasWeekBadge], or [hasMovement] are available, then
 * Share picture (capturing the configured 1080×1350 PNG) and Share as text.
 */
@Composable
fun ShareCardSheet(
    text: String,
    onShareText: (String) -> Unit,
    onSharePicture: suspend (png: ByteArray) -> Unit,
    onDismiss: () -> Unit,
    hasHeartRate: Boolean = false,
    hasWeekBadge: Boolean = false,
    hasMovement: Boolean = false,
    card: @Composable (ShareCardConfig) -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var config by remember { mutableStateOf(ShareCardConfig()) }
    val hasControls = hasHeartRate || hasWeekBadge || hasMovement
    IntervalSheet(stringResource(Res.string.share_sheet_title), onDismiss) { hide ->
        val description = stringResource(Res.string.share_card_cd)
        val scrubModifier = if (hasMovement && config.showWatermark) {
            Modifier.pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    val current = config.movementProgress ?: DEFAULT_SCRUB_START
                    val width = size.width.coerceAtLeast(1).toFloat()
                    val next = (current + dragAmount / width).let { it - kotlin.math.floor(it) }
                    config = config.copy(movementProgress = next)
                }
            }
        } else {
            Modifier
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = if (hasControls) Spacing.space12 else Spacing.space16)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            FittedShareCard(
                modifier = scrubModifier,
                capture = layer,
                windowShare = if (hasControls) SHARE_CARD_WINDOW_SHARE_INTERACTIVE else SHARE_CARD_WINDOW_SHARE,
            ) {
                card(config)
            }
        }
        if (hasControls) {
            ShareLayerControls(
                config = config,
                hasHeartRate = hasHeartRate,
                hasWeekBadge = hasWeekBadge,
                hasMovement = hasMovement,
                onConfigChange = { config = it },
                modifier = Modifier.padding(bottom = Spacing.space12),
            )
        }
        AccentButton(
            stringResource(Res.string.share_picture),
            onClick = {
                scope.launch {
                    val png = layer.png()
                    onSharePicture(png)
                    hide()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        SecondaryButton(
            stringResource(Res.string.share_as_text),
            onClick = {
                onShareText(text)
                hide()
            },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.space12),
        )
    }
}

@Composable
private fun ShareLayerControls(
    config: ShareCardConfig,
    hasHeartRate: Boolean,
    hasWeekBadge: Boolean,
    hasMovement: Boolean,
    onConfigChange: (ShareCardConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (hasHeartRate) {
            ShareToggleChip(
                label = stringResource(Res.string.share_card_toggle_hr),
                active = config.showHeartRate,
                onClick = { onConfigChange(config.copy(showHeartRate = !config.showHeartRate)) },
            )
        }
        if (hasWeekBadge) {
            ShareToggleChip(
                label = stringResource(Res.string.share_card_toggle_week),
                active = config.showWeekBadge,
                onClick = { onConfigChange(config.copy(showWeekBadge = !config.showWeekBadge)) },
            )
        }
        if (hasMovement) {
            ShareToggleChip(
                label = stringResource(Res.string.share_card_toggle_figure),
                active = config.showWatermark,
                onClick = { onConfigChange(config.copy(showWatermark = !config.showWatermark)) },
            )
            if (config.showWatermark) {
                ShareToggleChip(
                    label = stringResource(Res.string.share_card_cycle_pose),
                    active = config.movementProgress != null,
                    onClick = {
                        onConfigChange(config.copy(movementProgress = nextPoseProgress(config.movementProgress)))
                    },
                )
            }
        }
    }
}

@Composable
private fun ShareToggleChip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    val fill = if (active) palette.accentTint else palette.surfaceVariant
    val border = if (active) palette.accent else palette.line
    val ink = if (active) palette.accent else palette.textDim
    Box(
        modifier
            .height(Sizes.shareToggleHeight)
            .clip(Corners.full)
            .background(fill)
            .border(Sizes.hairline, border, Corners.full)
            .semantics { selected = active }
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Spacing.space12),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = IntervalTheme.type.caption, color = ink, maxLines = 1)
    }
}

/** Steps cleanly through iconic kinematic pose stops (null = rep's default peak `still` frame). */
internal fun nextPoseProgress(current: Float?): Float? = when {
    current == null -> POSE_STOP_START
    current < POSE_STOP_MID - POSE_EPSILON -> POSE_STOP_MID
    current < POSE_STOP_LATE - POSE_EPSILON -> POSE_STOP_LATE
    else -> null
}

/**
 * A [ShareCard] (the [card]) at the width there is, and no taller than [windowShare] of the window,
 * scaled down whole, never up, with rounded corners: it is laid out in its own density, so it is wider than most
 * phones' sheets in theirs, and a phone held sideways would otherwise push the sheet's buttons off the screen. With
 * [capture] the card is recorded into it at its full size, to be taken as a picture. Previews show a card through
 * this.
 */
@Composable
fun FittedShareCard(
    modifier: Modifier = Modifier,
    capture: GraphicsLayer? = null,
    windowShare: Float = SHARE_CARD_WINDOW_SHARE,
    card: @Composable () -> Unit,
) {
    val tallest = (LocalWindowInfo.current.containerSize.height * windowShare).roundToInt()
    Box(modifier.clip(Corners.radius18).fitWithin(tallest)) {
        Box(if (capture != null) Modifier.capturedBy(capture) else Modifier) { card() }
    }
}

/** What the layer last drew, as a PNG, encoded off the main thread. */
internal suspend fun GraphicsLayer.png(): ByteArray {
    val bitmap = toImageBitmap()
    return withContext(Dispatchers.Default) { bitmap.encodePng() }
}

/** Draws the content into [layer] as well as on screen, at its own size, so it can be captured whole. */
internal fun Modifier.capturedBy(layer: GraphicsLayer): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    drawLayer(layer)
}

/**
 * Lays the content out at its own size and draws it scaled down to the width there is and to [tallest] pixels
 * high (none when that is 0, as before a window is measured), never up.
 */
private fun Modifier.fitWithin(tallest: Int): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(Constraints())
    val byWidth = if (constraints.hasBoundedWidth) constraints.maxWidth / placeable.width.toFloat() else 1f
    val byHeight = if (tallest > 0) tallest / placeable.height.toFloat() else 1f
    val scale = minOf(1f, byWidth, byHeight)
    layout((placeable.width * scale).roundToInt(), (placeable.height * scale).roundToInt()) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0f)
        }
    }
}

/** The most of the window's height a card is drawn over in its sheet, leaving the title and buttons room. */
private const val SHARE_CARD_WINDOW_SHARE = 0.45f

/** Compact height share when interactive telemetry toggle pills sit beneath the preview card. */
private const val SHARE_CARD_WINDOW_SHARE_INTERACTIVE = 0.35f

/** The card's density: its dp are three pixels each, so 360 dp come to 1080 pixels. */
private const val SHARE_CARD_DENSITY = 3f

private const val EYEBROW_TRACKING_SP = 0.8f
private const val CARD_TITLE_SP = 32f
private const val CARD_TITLE_LINE_SP = 35f
private const val COMPACT_STAT_COUNT = 4
private const val STAT_DIVIDER_ALPHA = 0.6f
private const val STAT_VALUE_SP = 30f
private const val STAT_VALUE_COMPACT_SP = 22f
private const val STAT_UNIT_SP = 13f
private const val STAT_UNIT_COMPACT_SP = 10f
private const val STAT_LABEL_SP = 9.5f
private const val STAT_LABEL_LINE_SP = 11.5f
private const val STAT_LABEL_TRACKING_SP = 0.5f
private const val FOOTER_MARK_SP = 18f
private const val FOOTER_TRACKING_SP = 1.6f
private const val DEFAULT_SCRUB_START = 0.5f
private const val POSE_STOP_START = 0.12f
private const val POSE_STOP_MID = 0.45f
private const val POSE_STOP_LATE = 0.78f
private const val POSE_EPSILON = 0.05f

private val previewTelemetrySamples = listOf(
    104, 114, 128, 142, 154, 162, 171, 168, 148, 134, 146, 160, 174, 179, 166, 149, 136, 152, 168, 175, 154, 132,
)
private val previewTelemetryMarks = listOf(0.15f..0.38f, 0.52f..0.78f)

@PreviewFormFactors
@Composable
private fun ShareCardTelemetryPreview() = FittedShareCard {
    ShareCard(
        eyebrow = "Sunday, Nov 22 · 7:05 AM",
        title = "Kettlebell Snatch",
        subtitle = "15s on · 15s off · ×20",
        week = WeekStanding(count = 4, goal = 5, streak = 3),
        heartRate = ShareHeartRate(previewTelemetrySamples, marks = previewTelemetryMarks),
        movement = KettlebellReps.Snatch,
        stats = listOf(
            ShareStat("3,200", "Volume", unit = "kg"),
            ShareStat("179", "Peak BPM", unit = "bpm", accent = true),
            ShareStat("5:00", "Under tension"),
        ),
    )
}
