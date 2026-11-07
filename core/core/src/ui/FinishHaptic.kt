package dev.justinsmith.interval.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.justinsmith.interval.platform.prefersReducedMotion
import kotlinx.coroutines.delay

/**
 * A finish landing under the thumb once, [delayMillis] after the summary arrives (as its ring closes, or as
 * its heading settles; at once under reduced motion): a Confirm, or a LongPress when [firm], for a record
 * fallen or the week brought to its goal. Saved, so a summary composed again from saved state (a rotation)
 * never plays it twice.
 */
@Composable
fun FinishHaptic(firm: Boolean, delayMillis: Int = Motion.SUMMARY_RING_MILLIS) {
    val haptics = LocalHapticFeedback.current
    val reducedMotion = prefersReducedMotion()
    var played by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (played) return@LaunchedEffect
        if (!reducedMotion) delay(delayMillis.toLong())
        haptics.performHapticFeedback(if (firm) HapticFeedbackType.LongPress else HapticFeedbackType.Confirm)
        played = true
    }
}
