package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.justinsmith.interval.nav.HistoryTab
import dev.justinsmith.interval.nav.Tab
import dev.justinsmith.interval.nav.TodayTab
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.nav_history
import dev.justinsmith.interval.resources.nav_today
import dev.justinsmith.interval.resources.nav_train
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** A destination of the bar: one tab, or several the bar shows as one (Train's pillars). */
private data class TabSpec(val tabs: List<Tab>, val label: StringResource, val icon: ImageVector) {
    constructor(tab: Tab, label: StringResource, icon: ImageVector) : this(listOf(tab), label, icon)
}

private val Tabs = listOf(
    TabSpec(TodayTab, Res.string.nav_today, IntervalIcons.Today),
    TabSpec(TrainPillars, Res.string.nav_train, IntervalIcons.Bell),
    TabSpec(HistoryTab, Res.string.nav_history, IntervalIcons.History),
)

/**
 * The tabs as navigation suite items, for whichever bar or rail [type] is showing. Train opens on [pillar], the
 * one of its pillars last shown.
 */
@Suppress("detekt.MultipleEmitters", "detekt.ModifierMissing") // The scaffold's item slot: one item per tab.
@Composable
fun NavigationItems(current: Tab, type: NavigationSuiteType, pillar: Tab, onSelect: (Tab) -> Unit) {
    val palette = IntervalTheme.palette
    val colors = ShortNavigationBarItemDefaults.colors(
        selectedIconColor = palette.onAccent,
        selectedTextColor = palette.text,
        selectedIndicatorColor = palette.accent,
        unselectedIconColor = palette.textDim,
        unselectedTextColor = palette.textDim,
    )
    val haptics = LocalHapticFeedback.current
    Tabs.forEach { spec ->
        val selected = current in spec.tabs
        NavigationSuiteItem(
            selected = selected,
            onClick = {
                if (!selected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                onSelect(spec.tabs.singleOrNull() ?: pillar)
            },
            icon = { Icon(spec.icon, contentDescription = null, modifier = Modifier.size(Sizes.icon)) },
            label = { Text(stringResource(spec.label), style = IntervalTheme.type.caption) },
            navigationSuiteType = type,
            colors = colors,
        )
    }
}
