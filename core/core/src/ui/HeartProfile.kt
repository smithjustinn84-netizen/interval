package dev.justinsmith.interval.ui

import androidx.compose.runtime.compositionLocalOf

/**
 * The resting heart rate set in Settings, null for none: with it everything that draws a heart-rate zone (the
 * scale, the badge, the charts' bands, time in zone) cuts the zones from the reserve between it and the max
 * each is handed, as `heartRateZone` does. The app provides it once, over everything; a preview or a test with
 * none reads the zones by the max alone.
 */
val LocalRestingHeartRate = compositionLocalOf<Int?> { null }
