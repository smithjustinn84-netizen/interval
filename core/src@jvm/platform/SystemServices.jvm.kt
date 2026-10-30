package dev.justinsmith.interval.platform

import androidx.compose.runtime.Composable

/** Desktop displays follow the OS power settings; nothing to hold here. */
@Composable
actual fun KeepScreenOn() = Unit

/** Desktop windows have no system bars to restyle. */
@Composable
actual fun SystemBarIcons(lightIcons: Boolean) = Unit

/** Desktop has no system-wide reduce-motion setting that Compose can read. */
@Composable
actual fun prefersReducedMotion(): Boolean = false

actual val materialBackPreview: Boolean = false

actual val platformPageTransitions: Boolean = false
