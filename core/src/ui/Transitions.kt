package dev.justinsmith.interval.ui

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn

// The transitions between two things that take the same place, for `AnimatedContent`: a page for
// another in Navigation.kt, or a card's contents for the next on one screen.

/**
 * Material's fade through, over [millis]: the old content fades out quickly, then the new fades in a
 * touch larger, from [Motion.FADE_THROUGH_SCALE]. The two never show at once, half-seen, as a plain
 * crossfade would.
 */
fun fadeThrough(millis: Int): ContentTransform = ContentTransform(
    targetContentEnter = lateFadeIn(millis) +
        scaleIn(
            tween(millis - Motion.FADE_THROUGH_GAP_MILLIS, Motion.FADE_THROUGH_GAP_MILLIS, EnterEasing),
            initialScale = Motion.FADE_THROUGH_SCALE,
        ),
    initialContentExit = quickFadeOut(),
)

/** [fadeThrough] at the pace of content changing in place: a card's figures, a button's label. */
fun fadeThroughContent(): ContentTransform = fadeThrough(Motion.CONTENT_MILLIS)

/** The leaving content's fade: over in [Motion.FADE_THROUGH_GAP_MILLIS], before the arriving content shows. */
fun quickFadeOut(): ExitTransition = fadeOut(tween(Motion.FADE_THROUGH_GAP_MILLIS))

/** The arriving content's fade, held back until the leaving content has gone, done by [millis]. */
fun lateFadeIn(millis: Int): EnterTransition =
    fadeIn(tween(millis - Motion.FADE_THROUGH_GAP_MILLIS, Motion.FADE_THROUGH_GAP_MILLIS))
