package dev.justinsmith.interval

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
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
import dev.justinsmith.interval.presets.PresetsRoute
import dev.justinsmith.interval.settings.SettingsRoute
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.NavBar
import dev.justinsmith.interval.ui.NavRail
import dev.justinsmith.interval.workout.WorkoutRoute
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.currentKoinScope

/**
 * Every [NavKey] the back stack can hold, registered explicitly so saving and restoring works on
 * every platform (native has no reflection-based serialization).
 */
private val navConfiguration = SavedStateConfiguration {
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

/** The root composable every platform entry point hosts. */
@Composable
@DevelopmentEntryPoint
fun App() {
    KoinIsolatedContext(context = rememberAppKoin()) {
        val repository = currentKoinScope().get<IntervalRepository>()
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
 * expect. The navigation bar shows only while a tab is on top; sideways it becomes a rail.
 */
@Composable
private fun AppShell(backStack: NavBackStack<NavKey>) {
    val top = backStack.lastOrNull()
    val currentTab = top as? Tab
    val selectTab = { tab: Tab -> backStack.selectTab(tab) }
    BoxWithConstraints(Modifier.fillMaxSize().background(IntervalTheme.palette.background)) {
        val landscape = maxWidth > maxHeight
        // The bar or rail takes the system insets on its side; full-screen destinations (Settings,
        // Workout) handle their own, so the workout's colour runs under the system bars.
        val insets = when {
            currentTab == null -> null
            landscape -> WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            else -> WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
        }
        // NavDisplay stays at one place in the tree whatever the bars do, so its entries keep their state.
        Row(Modifier.fillMaxSize()) {
            if (currentTab != null && landscape) NavRail(currentTab, selectTab, Modifier.fillMaxHeight())
            Column(Modifier.weight(1f).fillMaxHeight()) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                    // Passing this replaces NavDisplay's default list, so the saveable-state holder is
                    // repeated. The ViewModel store decorator goes last: it needs the holder above it.
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (insets !=
                                null
                            ) {
                                Modifier.windowInsetsPadding(insets).consumeWindowInsets(insets)
                            } else {
                                Modifier
                            },
                        ),
                    entryProvider = entryProvider {
                        entry<TimerTab> {
                            TimerRoute(
                                // A double tap on Start must not stack two workouts.
                                onStart = { if (backStack.lastOrNull() != Workout) backStack.add(Workout) },
                                onOpenSettings = { if (backStack.lastOrNull() != Settings) backStack.add(Settings) },
                                onSeeAllPresets = { selectTab(PresetsTab) },
                            )
                        }
                        entry<PresetsTab> { PresetsRoute(onShowTimer = { selectTab(TimerTab) }) }
                        entry<HistoryTab> { HistoryRoute() }
                        entry<Settings> { SettingsRoute(onBack = { backStack.removeLastOrNull() }) }
                        entry<Workout> { WorkoutRoute(onExit = { backStack.removeLastOrNull() }) }
                    },
                )
                if (currentTab != null && !landscape) NavBar(currentTab, selectTab)
            }
        }
    }
}

/** Shows [tab]: Timer is the root, so it pops back to it; any other tab replaces what is above Timer. */
private fun NavBackStack<NavKey>.selectTab(tab: Tab) {
    while (size > 1) removeLastOrNull()
    if (tab != TimerTab) add(tab)
}
