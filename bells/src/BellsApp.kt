package dev.justinsmith.bells

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.di.rememberBellsKoin
import dev.justinsmith.bells.gear.GearRoute
import dev.justinsmith.bells.history.BellsHistoryRoute
import dev.justinsmith.bells.nav.BellsInventoryTab
import dev.justinsmith.bells.nav.BellsTab
import dev.justinsmith.bells.nav.HistoryTab
import dev.justinsmith.bells.nav.Programs
import dev.justinsmith.bells.nav.Session
import dev.justinsmith.bells.nav.TodayTab
import dev.justinsmith.bells.programs.ProgramsRoute
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.nav_bells
import dev.justinsmith.bells.resources.nav_history
import dev.justinsmith.bells.resources.nav_today
import dev.justinsmith.bells.session.SessionRoute
import dev.justinsmith.bells.today.TodayRoute
import dev.justinsmith.core.sync.SiblingSync
import dev.justinsmith.interval.platform.SystemBarIcons
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.PageKind
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.navigationSuiteColors
import dev.justinsmith.interval.ui.navigationSuiteType
import dev.justinsmith.interval.ui.pageKind
import dev.justinsmith.interval.ui.pagePopTransition
import dev.justinsmith.interval.ui.pageTransition
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.currentKoinScope
import org.koin.core.KoinApplication

/** Every [NavKey] the back stack can hold, registered so saving it works on every platform. */
internal val bellsNavConfiguration = SavedStateConfiguration {
    serializersModule = serializersModule + SerializersModule {
        polymorphic(NavKey::class) {
            subclass(TodayTab::class)
            subclass(HistoryTab::class)
            subclass(BellsInventoryTab::class)
            subclass(Session::class)
            subclass(Programs::class)
        }
    }
}

/** The root composable every Bells entry point hosts. */
@Composable
fun BellsApp() {
    BellsContent(rememberBellsKoin())
}

/** [BellsApp] with its container passed in, so tests can run it over fakes. Always Gym dark. */
@Composable
internal fun BellsContent(koin: KoinApplication) {
    KoinIsolatedContext(context = koin) {
        val repository = currentKoinScope().get<BellsRepository>()
        val sync = currentKoinScope().get<SiblingSync>()
        // Interval may have recorded workouts while Bells was away: take them in on every return.
        LifecycleResumeEffect(sync) {
            sync.pull()
            onPauseOrDispose {}
        }
        val ready by repository.ready.collectAsStateWithLifecycle()
        if (!ready) return@KoinIsolatedContext
        IntervalTheme(darkTheme = true) {
            SystemBarIcons(lightIcons = true)
            BellsShell(rememberNavBackStack(bellsNavConfiguration, TodayTab))
        }
    }
}

private data class TabSpec(val tab: BellsTab, val label: StringResource, val icon: ImageVector)

private val Tabs = listOf(
    TabSpec(TodayTab, Res.string.nav_today, IntervalIcons.Today),
    TabSpec(HistoryTab, Res.string.nav_history, IntervalIcons.History),
    TabSpec(BellsInventoryTab, Res.string.nav_bells, IntervalIcons.Bell),
)

/** One back stack: Today at the root, at most one other tab above it, then the session. */
@Composable
private fun BellsShell(backStack: NavBackStack<NavKey>) {
    val currentTab = backStack.lastOrNull() as? BellsTab
    val palette = IntervalTheme.palette
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val type = if (currentTab == null) NavigationSuiteType.None else navigationSuiteType(maxWidth, maxHeight)
        val insets = when {
            currentTab == null -> null

            type == NavigationSuiteType.NavigationBar -> WindowInsets.safeDrawing.only(
                WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
            )

            else -> WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
        }
        NavigationSuiteScaffold(
            navigationItems = { currentTab?.let { TabItems(it, type) { tab -> backStack.selectTab(tab) } } },
            navigationSuiteType = type,
            navigationSuiteColors = navigationSuiteColors(),
            containerColor = palette.background,
            contentColor = palette.text,
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                transitionSpec = pageTransition(),
                popTransitionSpec = pagePopTransition(),
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (insets !=
                            null
                        ) {
                            Modifier.windowInsetsPadding(insets).consumeWindowInsets(insets)
                        } else {
                            Modifier
                        },
                    ),
                entryProvider = bellsEntries(backStack),
            )
        }
    }
}

private fun bellsEntries(backStack: NavBackStack<NavKey>): (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<TodayTab>(metadata = pageKind(PageKind.Tab)) {
        TodayRoute(
            onStartSession = { if (backStack.lastOrNull() != Session) backStack.add(Session) },
            onChooseProgram = { if (backStack.lastOrNull() != Programs) backStack.add(Programs) },
        )
    }
    entry<Programs> { ProgramsRoute(onBack = { backStack.removeLastOrNull() }) }
    entry<HistoryTab>(metadata = pageKind(PageKind.Tab)) { BellsHistoryRoute() }
    entry<BellsInventoryTab>(metadata = pageKind(PageKind.Tab)) { GearRoute() }
    entry<Session>(metadata = pageKind(PageKind.Mode)) { SessionRoute(onExit = { backStack.removeLastOrNull() }) }
}

@Suppress("detekt.MultipleEmitters", "detekt.ModifierMissing") // The scaffold's item slot: one item per tab.
@Composable
private fun TabItems(current: BellsTab, type: NavigationSuiteType, onSelect: (BellsTab) -> Unit) {
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

/** Shows [tab]: Today is the root, so it pops back to it; any other tab replaces what is above Today. */
private fun NavBackStack<NavKey>.selectTab(tab: BellsTab) {
    while (size > 1) removeLastOrNull()
    if (tab != TodayTab) add(tab)
}
