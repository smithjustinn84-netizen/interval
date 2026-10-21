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
import dev.justinsmith.interval.nav.HistoryTab
import dev.justinsmith.interval.nav.LiftTab
import dev.justinsmith.interval.nav.Tab
import dev.justinsmith.interval.nav.TimerTab
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.nav_history
import dev.justinsmith.interval.resources.nav_lift
import dev.justinsmith.interval.resources.nav_timer
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

private data class TabSpec(val tab: Tab, val label: StringResource, val icon: ImageVector)

private val Tabs = listOf(
    TabSpec(TimerTab, Res.string.nav_timer, IntervalIcons.Timer),
    TabSpec(LiftTab, Res.string.nav_lift, IntervalIcons.Bell),
    TabSpec(HistoryTab, Res.string.nav_history, IntervalIcons.History),
)

/** The tabs as navigation suite items, for whichever bar or rail [type] is showing. */
@Suppress("detekt.MultipleEmitters", "detekt.ModifierMissing") // The scaffold's item slot: one item per tab.
@Composable
fun NavigationItems(current: Tab, type: NavigationSuiteType, onSelect: (Tab) -> Unit) {
    val palette = IntervalTheme.palette
    val colors = ShortNavigationBarItemDefaults.colors(
        selectedIconColor = palette.onAccent,
        selectedTextColor = palette.text,
        selectedIndicatorColor = palette.accent,
        unselectedIconColor = palette.textDim,
        unselectedTextColor = palette.textDim,
    )
    Tabs.forEach { spec ->
        NavigationSuiteItem(
            selected = spec.tab == current,
            onClick = { onSelect(spec.tab) },
            icon = { Icon(spec.icon, contentDescription = null, modifier = Modifier.size(Sizes.icon)) },
            label = { Text(stringResource(spec.label), style = IntervalTheme.type.caption) },
            navigationSuiteType = type,
            colors = colors,
        )
    }
}
