@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package dev.justinsmith.interval

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.data.ThemeMode
import dev.justinsmith.interval.di.rememberAppKoin
import dev.justinsmith.interval.history.HistoryRoute
import dev.justinsmith.interval.home.TimerRoute
import dev.justinsmith.interval.nav.HistoryTab
import dev.justinsmith.interval.nav.PresetsTab
import dev.justinsmith.interval.nav.Settings
import dev.justinsmith.interval.nav.Tab
import dev.justinsmith.interval.nav.TimerTab
import dev.justinsmith.interval.nav.Workout
import dev.justinsmith.interval.platform.SystemBarIcons
import dev.justinsmith.interval.platform.materialBackPreview
import dev.justinsmith.interval.presets.PresetsRoute
import dev.justinsmith.interval.settings.SettingsRoute
import dev.justinsmith.interval.splash.WithIntro
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.NavigationItems
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.backPreviewCorners
import dev.justinsmith.interval.ui.backPreviewTransition
import dev.justinsmith.interval.ui.navigationSuiteColors
import dev.justinsmith.interval.ui.navigationSuiteType
import dev.justinsmith.interval.workout.WorkoutRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.currentKoinScope
import org.koin.core.KoinApplication

/**
 * Every [NavKey] the back stack can hold, registered explicitly so saving and restoring works on
 * every platform (native has no reflection-based serialization).
 */
internal val navConfiguration = SavedStateConfiguration {
    serializersModule = serializersModule + SerializersModule {
        polymorphic(NavKey::class) {
            subclass(TimerTab::class)
            subclass(PresetsTab::class)
            subclass(HistoryTab::class)
            subclass(Settings::class)
            subclass(Workout::class)
        }
    }
}

/** The root composable every platform entry point hosts: the app, with the intro over it at launch. */
@Composable
@DevelopmentEntryPoint
fun App() {
    val koin = rememberAppKoin()
    val repository = remember(koin) { koin.koin.get<IntervalRepository>() }
    val ready by repository.ready.collectAsStateWithLifecycle()
    WithIntro(ready) { AppContent(koin) }
}

/** [App] with its container passed in, so tests can run the whole app over fake platform services. */
@Composable
internal fun AppContent(koin: KoinApplication) {
    KoinIsolatedContext(context = koin) {
        val repository = currentKoinScope().get<IntervalRepository>()
        val ready by repository.ready.collectAsStateWithLifecycle()
        // Saved settings take milliseconds to read. Until then the window background shows, rather than
        // screens built from defaults or a flash of the wrong theme.
        if (!ready) return@KoinIsolatedContext
        val settings by repository.appSettings.collectAsStateWithLifecycle()
        val dark = when (settings.theme) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
        IntervalTheme(darkTheme = dark) {
            val backStack = rememberNavBackStack(navConfiguration, TimerTab)
            // The workout paints phase colours under the bars in either theme.
            SystemBarIcons(lightIcons = dark || backStack.lastOrNull() == Workout)
            AppShell(backStack)
        }
    }
}

/**
 * One back stack: the Timer tab at the root, at most one other tab above it, then whatever was
 * pushed (Settings, Workout). Back from Presets or History returns to Timer, as both platforms
 * expect. The navigation bar or rail shows only while a tab is on top, or while Settings sits beside
 * Timer: windows of Material's expanded width and up open Settings as a supporting pane.
 */
@Composable
private fun AppShell(backStack: NavBackStack<NavKey>) {
    val top = backStack.lastOrNull()
    // Side by side only: Material would also stack Timer over Settings on a tall tablet in portrait,
    // which leaves both too short. There Settings opens full screen, as on a phone.
    val directive = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
        .copy(maxVerticalPartitions = 1, verticalPartitionSpacerSize = 0.dp)
    val supportingPane = rememberSupportingPaneSceneStrategy<NavKey>(directive = directive)
    val twoPane = directive.maxHorizontalPartitions > 1
    val currentTab = top as? Tab ?: TimerTab.takeIf { twoPane && top == Settings }
    val selectTab = { tab: Tab -> backStack.selectTab(tab) }
    val palette = IntervalTheme.palette
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Full-screen destinations get no navigation area at all, rather than one animating away, so
        // the workout lays out once at its full size.
        val type = if (currentTab == null) NavigationSuiteType.None else navigationSuiteType(maxWidth, maxHeight)
        val bar = type == NavigationSuiteType.NavigationBar
        // The bar or rail takes the system insets on its side; full-screen destinations (Settings,
        // Workout) handle their own, so the workout's colour runs under the system bars.
        val insets = when {
            currentTab == null -> null
            bar -> WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            else -> WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
        }
        NavigationSuiteScaffold(
            navigationItems = { currentTab?.let { NavigationItems(it, type, selectTab) } },
            navigationSuiteType = type,
            navigationSuiteColors = navigationSuiteColors(),
            containerColor = palette.background,
            contentColor = palette.text,
        ) {
            // NavDisplay stays at one place in the tree whatever the bars do, so its entries keep their state.
            NavDisplay(
                backStack = backStack,
                onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                // Passing this replaces NavDisplay's default list, so the saveable-state holder is
                // repeated. The ViewModel store decorator follows it: it needs the holder above it.
                // On Android the back preview's corners clip each entry's content, innermost.
                entryDecorators = listOfNotNull(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                    if (materialBackPreview) backPreviewCorners() else null,
                ),
                sceneStrategies = listOf(supportingPane),
                predictivePopTransitionSpec = if (materialBackPreview) {
                    backPreviewTransition()
                } else {
                    defaultPredictivePopTransitionSpec()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (bar) Modifier.hairlineAtBottom(palette.line) else Modifier)
                    .then(
                        if (insets != null) {
                            Modifier.windowInsetsPadding(insets).consumeWindowInsets(insets)
                        } else {
                            Modifier
                        },
                    ),
                entryProvider = entryProvider {
                    entry<TimerTab>(metadata = SupportingPaneSceneStrategy.mainPane()) {
                        TimerRoute(
                            // A double tap on Start must not stack two workouts.
                            onStart = { if (backStack.lastOrNull() != Workout) backStack.add(Workout) },
                            // Beside Timer, the gear closes Settings again.
                            onOpenSettings = {
                                when {
                                    backStack.lastOrNull() != Settings -> backStack.add(Settings)
                                    twoPane -> backStack.removeLastOrNull()
                                }
                            },
                            onSeeAllPresets = { selectTab(PresetsTab) },
                        )
                    }
                    entry<PresetsTab> { PresetsRoute(onShowTimer = { selectTab(TimerTab) }) }
                    entry<HistoryTab> { HistoryRoute() }
                    entry<Settings>(metadata = SupportingPaneSceneStrategy.supportingPane()) {
                        SettingsRoute(onBack = { backStack.removeLastOrNull() }, inPane = twoPane)
                    }
                    entry<Workout> { WorkoutRoute(onExit = { backStack.removeLastOrNull() }) }
                },
            )
        }
    }
}

/** The line along the top of the bottom bar, drawn over the bottom edge of the content above it. */
private fun Modifier.hairlineAtBottom(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = Sizes.hairline.toPx()
    val y = size.height - stroke / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
}

/** Shows [tab]: Timer is the root, so it pops back to it; any other tab replaces what is above Timer. */
private fun NavBackStack<NavKey>.selectTab(tab: Tab) {
    while (size > 1) removeLastOrNull()
    if (tab != TimerTab) add(tab)
}
