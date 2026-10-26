package dev.justinsmith.interval.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import dev.justinsmith.interval.platform.prefersReducedMotion
import kotlinx.coroutines.delay

/**
 * A line of feedback after something is saved, added or removed ("Saved as Tabata", "Removed Push-up
 * from Monday"), with an [action] to take it back where there is one. The [text] is already in words:
 * the screen that knows what happened words it, in its own strings.
 */
data class Notice(val id: Int, val text: String, val action: NoticeAction? = null)

/** What a notice offers to do about itself, nearly always Undo. */
class NoticeAction(val label: String, val onClick: () -> Unit)

/**
 * The one notice on show, app-wide. It lives above the back stack (see `LocalNotices`), so a notice
 * outlives the page that raised it: "Workout deleted · Undo" stays as the page pops.
 */
@Stable
class Notices {
    var current: Notice? by mutableStateOf(null)
        private set
    private var serial = 0

    /** Shows [text], replacing a notice already on show. */
    fun show(text: String, action: NoticeAction? = null) {
        current = Notice(++serial, text, action)
    }

    /** Takes [notice] down, if it is still the one on show. */
    fun dismiss(notice: Notice) {
        if (current?.id == notice.id) current = null
    }
}

@Composable
fun rememberNotices(): Notices = remember { Notices() }

/**
 * The app's [Notices], for any screen to show one through. The default is a holder nothing draws, so
 * a screen composed alone (a preview, a test) can still call it.
 */
@Suppress("detekt.CompositionLocalAllowlist")
val LocalNotices = staticCompositionLocalOf { Notices() }

/**
 * Draws [notices]' current one as a pill at the bottom of whatever holds it: the text, its action at
 * the end, read out as it appears. It rises in, fades out, and goes by itself after
 * [Motion.UNDO_MILLIS] with an action to offer or [Motion.NOTICE_MILLIS] without. A new notice takes
 * the old one's place at once.
 */
@Composable
fun NoticeHost(notices: Notices, modifier: Modifier = Modifier) {
    val current = notices.current
    val accessibilityManager = LocalAccessibilityManager.current
    val reducedMotion = prefersReducedMotion()
    LaunchedEffect(current?.id, accessibilityManager) {
        if (current != null) {
            val baseMillis = if (current.action != null) Motion.UNDO_MILLIS else Motion.NOTICE_MILLIS
            val timeoutMillis = accessibilityManager?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = baseMillis,
                containsIcons = false,
                containsText = true,
                containsControls = current.action != null,
            ) ?: baseMillis
            delay(timeoutMillis)
            notices.dismiss(current)
        }
    }
    AnimatedContent(
        targetState = current,
        modifier = modifier,
        transitionSpec = { enterRising(reducedMotion = reducedMotion) togetherWith fadeOut(tween(Motion.EXIT_MILLIS)) },
        contentKey = { it?.id },
        contentAlignment = Alignment.BottomCenter,
        label = "notice",
    ) { notice ->
        if (notice != null) NoticePill(notice, onAction = { notices.dismiss(notice) })
    }
}

@Composable
private fun NoticePill(notice: Notice, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Row(
        modifier
            .widthIn(max = Sizes.content)
            .heightIn(min = Sizes.listRow)
            .clip(Corners.radius12)
            .background(palette.surfaceVariant)
            .padding(start = Spacing.space16, end = Spacing.space4)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(Spacing.space8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(notice.text, style = IntervalTheme.type.label, color = palette.text, modifier = Modifier.weight(1f))
        notice.action?.let { action ->
            val interaction = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .heightIn(min = Sizes.pillHeight)
                    .surfaceButton(
                        interaction,
                        Corners.radius12,
                        Color.Transparent,
                        onClick = {
                            action.onClick()
                            onAction()
                        },
                        haptic = HapticFeedbackType.Confirm,
                    )
                    .padding(horizontal = Spacing.space12),
                contentAlignment = Alignment.Center,
            ) {
                Text(action.label, style = IntervalTheme.type.control, color = palette.accent)
            }
        }
    }
}
