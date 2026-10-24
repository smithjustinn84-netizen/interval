package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.width
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.window.core.layout.WindowSizeClass

/**
 * Whether an [IntervalSheet] opened now rises over a window wider than it is tall: a phone on its side. Its
 * content has the width for two columns and not the height for one, so a long form splits in two and keeps
 * its last button in view. Read it before opening the sheet, as the sheet reads the window; it follows the
 * phone turned with the sheet open.
 */
@Composable
fun sheetIsSideways(): Boolean {
    val window = LocalWindowInfo.current.containerSize
    return !windowIsRoomy() && window.width > window.height
}

/**
 * Whether the window is at least Material's medium width and height: a tablet either way up, an unfolded
 * foldable, a desktop. Where a sheet opens beside rather than rising, and where a screen laid out for a phone
 * has room to spare. A phone on its side is too short to be. Read live, so a screen follows a resize.
 */
@Composable
fun windowIsRoomy(): Boolean = opensBeside(currentWindowAdaptiveInfoV2().windowSizeClass)

/** A window at least Material's medium width and height gets a side sheet. */
internal fun opensBeside(windowSize: WindowSizeClass) =
    windowSize.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) &&
        windowSize.isHeightAtLeastBreakpoint(WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
