package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import dev.justinsmith.interval.nav.HistoryTab
import dev.justinsmith.interval.nav.PresetsTab
import dev.justinsmith.interval.nav.Tab
import dev.justinsmith.interval.nav.TimerTab

private data class TabSpec(val tab: Tab, val label: String, val icon: ImageVector)

private val Tabs = listOf(
    TabSpec(TimerTab, "Timer", IntervalIcons.Timer),
    TabSpec(PresetsTab, "Presets", IntervalIcons.Presets),
    TabSpec(HistoryTab, "History", IntervalIcons.History),
)

/** The bottom navigation bar: Material 3's, in the app's colours, over a hairline. */
@Composable
fun NavBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Column(modifier) {
        HorizontalDivider(color = palette.line)
        NavigationBar(containerColor = palette.surface, tonalElevation = 0.dp) {
            Tabs.forEach { spec ->
                NavigationBarItem(
                    selected = spec.tab == current,
                    onClick = { onSelect(spec.tab) },
                    icon = { Icon(spec.icon, contentDescription = null, modifier = Modifier.size(Sizes.icon)) },
                    label = { Text(spec.label, style = IntervalTheme.type.navLabel) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = palette.accent,
                        selectedTextColor = palette.text,
                        indicatorColor = palette.accentTint,
                        unselectedIconColor = palette.textDim,
                        unselectedTextColor = palette.textDim,
                    ),
                )
            }
        }
    }
}

/** The navigation rail that replaces [NavBar] when the window is wider than tall. */
@Composable
fun NavRail(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    NavigationRail(modifier, containerColor = palette.surface) {
        Spacer(Modifier.height(Spacing.space16))
        Tabs.forEach { spec ->
            NavigationRailItem(
                selected = spec.tab == current,
                onClick = { onSelect(spec.tab) },
                icon = { Icon(spec.icon, contentDescription = null, modifier = Modifier.size(Sizes.icon)) },
                label = { Text(spec.label, style = IntervalTheme.type.navLabel) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = palette.accent,
                    selectedTextColor = palette.text,
                    indicatorColor = palette.accentTint,
                    unselectedIconColor = palette.textDim,
                    unselectedTextColor = palette.textDim,
                ),
            )
        }
    }
}
