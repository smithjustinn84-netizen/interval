package dev.justinsmith.interval.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.share_as_text
import dev.justinsmith.core.resources.share_card_cd
import dev.justinsmith.core.resources.share_card_mark
import dev.justinsmith.core.resources.share_picture
import dev.justinsmith.core.resources.share_sheet_title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** A figure on a [ShareCard]: its [value] large, its [label] small under it ("4:00" over "Time"). */
@Immutable
data class ShareStat(val value: String, val label: String)

/**
 * A finished workout or session as a picture to send: [eyebrow] (what it was and when) over its [title] and
 * [subtitle], then [art] filling the middle (a side's ring, trace or records), its [stats] in a row, and the
 * app's name at its foot. It is always dark, whatever the phone's theme, and always laid out at
 * [Sizes.shareCardWidth] by [Sizes.shareCardHeight] at a fixed density with the text at its own size, so the
 * picture is the same 1080 by 1350 pixels (Instagram's 4:5) on every phone and at the system's large text.
 * Shown and captured by [ShareCardSheet].
 */
@Composable
fun ShareCard(
    eyebrow: String,
    title: String,
    stats: List<ShareStat>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    art: @Composable BoxScope.() -> Unit = {},
) {
    CompositionLocalProvider(
        LocalDensity provides Density(SHARE_CARD_DENSITY, fontScale = 1f),
        LocalLayoutDirection provides LayoutDirection.Ltr,
    ) {
        IntervalTheme(darkTheme = true) {
            val palette = IntervalTheme.palette
            val type = IntervalTheme.type
            Column(
                modifier
                    .size(Sizes.shareCardWidth, Sizes.shareCardHeight)
                    .background(palette.background)
                    .padding(Spacing.space24),
            ) {
                Text(eyebrow.uppercase(), style = type.caption, color = palette.accent, maxLines = 1)
                Text(
                    title,
                    Modifier.padding(top = Spacing.space8),
                    style = type.title,
                    color = palette.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.let {
                    JoinedText(it, type.meta, palette.textDim, Modifier.padding(top = Spacing.space4))
                }
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(vertical = Spacing.space16),
                    contentAlignment = Alignment.Center,
                    content = art,
                )
                if (stats.isNotEmpty()) ShareStats(stats)
                ShareMark()
            }
        }
    }
}

@Composable
private fun ShareStats(stats: List<ShareStat>) {
    val palette = IntervalTheme.palette
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.space12)) {
        stats.forEach { stat ->
            Column(Modifier.weight(1f)) {
                Text(stat.value, style = IntervalTheme.type.stat, color = palette.text, maxLines = 1)
                Text(stat.label, style = IntervalTheme.type.caption, color = palette.textDim, maxLines = 1)
            }
        }
    }
}

/** The foot: a hairline, then the app's name in the accent. */
@Composable
private fun ShareMark() {
    val palette = IntervalTheme.palette
    Column(Modifier.fillMaxWidth()) {
        Spacer(
            Modifier.padding(top = Spacing.space16).fillMaxWidth().height(Sizes.hairline).background(palette.line),
        )
        Text(
            stringResource(Res.string.share_card_mark).uppercase(),
            Modifier.padding(top = Spacing.space12),
            style = IntervalTheme.type.control,
            color = palette.accent,
        )
    }
}

/**
 * Share's sheet: the [card] as it will be sent, scaled to fit, then Share picture, which captures it and hands
 * [onSharePicture] the PNG, and Share as text, which hands [onShareText] the [text] that Share sent before.
 */
@Composable
fun ShareCardSheet(
    text: String,
    onShareText: (String) -> Unit,
    onSharePicture: suspend (png: ByteArray) -> Unit,
    onDismiss: () -> Unit,
    card: @Composable () -> Unit,
) {
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    IntervalSheet(stringResource(Res.string.share_sheet_title), onDismiss) { hide ->
        val description = stringResource(Res.string.share_card_cd)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.space16)
                .semantics { contentDescription = description },
            contentAlignment = Alignment.Center,
        ) {
            FittedShareCard(capture = layer, card = card)
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

/**
 * A [ShareCard] (the [card]) at the width there is, and no taller than [SHARE_CARD_WINDOW_SHARE] of the window,
 * scaled down whole, never up, with rounded corners: it is laid out in its own density, so it is wider than most
 * phones' sheets in theirs, and a phone held sideways would otherwise push the sheet's buttons off the screen. With
 * [capture] the card is recorded into it at its full size, to be taken as a picture. Previews show a card through
 * this.
 */
@Composable
fun FittedShareCard(modifier: Modifier = Modifier, capture: GraphicsLayer? = null, card: @Composable () -> Unit) {
    val tallest = (LocalWindowInfo.current.containerSize.height * SHARE_CARD_WINDOW_SHARE).roundToInt()
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

/** The card's density: its dp are three pixels each, so 360 dp come to 1080 pixels. */
private const val SHARE_CARD_DENSITY = 3f
