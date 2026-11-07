package dev.justinsmith.interval.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** The haptics a screen performs, in order, for a test to assert: see [WithHaptics]. */
class RecordingHaptics : HapticFeedback {
    val performed = mutableListOf<HapticFeedbackType>()

    override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
        performed += hapticFeedbackType
    }
}

/** [content] with [haptics] standing in for the device's, as `LocalHapticFeedback`. */
@Composable
fun WithHaptics(haptics: RecordingHaptics, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHapticFeedback provides haptics, content = content)
}
