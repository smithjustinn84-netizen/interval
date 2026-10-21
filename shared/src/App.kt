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
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.gear.GearRoute
import dev.justinsmith.bells.history.LiftDetailRoute
import dev.justinsmith.bells.programs.ProgramsRoute
import dev.justinsmith.bells.session.SessionRoute
import dev.justinsmith.bells.today.TodayRoute
import dev.justinsmith.interval.about.AboutRoute
import dev.justinsmith.interval.about.LicensesRoute
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.data.ThemeMode
import dev.justinsmith.interval.di.rememberAppKoin
import dev.justinsmith.interval.history.HistoryRoute
import dev.justinsmith.interval.history.WorkoutDetailRoute
import dev.justinsmith.interval.home.TimerRoute
import dev.justinsmith.interval.nav.About
import dev.justinsmith.interval.nav.HistoryTab
import dev.justinsmith.interval.nav.Licenses
import dev.justinsmith.interval.nav.LiftDetail
import dev.justinsmith.interval.nav.LiftTab
import dev.justinsmith.interval.nav.Lifting
import dev.justinsmith.interval.nav.OwnedBells
import dev.justinsmith.interval.nav.Plan
import dev.justinsmith.interval.nav.Presets
import dev.justinsmith.interval.nav.Programs
import dev.justinsmith.interval.nav.Settings
import dev.justinsmith.interval.nav.Tab
import dev.justinsmith.interval.nav.TimerTab
import dev.justinsmith.interval.nav.Workout
import dev.justinsmith.interval.nav.WorkoutDetail
import dev.justinsmith.interval.plan.PlanRoute
import dev.justinsmith.interval.platform.SystemBarIcons
import dev.justinsmith.interval.platform.materialBackPreview
import dev.justinsmith.interval.presets.PresetsRoute
import dev.justinsmith.interval.settings.SettingsRoute
import dev.justinsmith.interval.splash.WithIntro
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.NavigationItems
import dev.justinsmith.interval.ui.PageKind
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.backPreviewCorners
import dev.justinsmith.interval.ui.backPreviewTransition
import dev.justinsmith.interval.ui.navigationSuiteColors
import dev.justinsmith.interval.ui.navigationSuiteType
import dev.justinsmith.interval.ui.pageKind
import dev.justinsmith.interval.ui.pagePopTransition
import dev.justinsmith.interval.ui.pageTransition
import dev.justinsmith.interval.workout.WorkoutRoute
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
            subclass(LiftTab::class)
            subclass(HistoryTab::class)
            subclass(Settings::class)
            subclass(Workout::class)
            subclass(About::class)
            subclass(Licenses::class)
            subclass(WorkoutDetail::class)
            subclass(Plan::class)
            subclass(Presets::class)
            subclass(Lifting::class)
            subclass(Programs::class)
            subclass(OwnedBells::class)
            subclass(LiftDetail::class)
        }
    }
}

/** The root composable every platform entry point hosts: the app, with the intro over it at launch. */
@Composable
@DevelopmentEntryPoint
fun App() {
    val koin = rememberAppKoin()
    val ready by remember(koin) { storageReady(koin.koin.get(), koin.koin.get()) }.collectAsStateWithLifecycle(false)
    WithIntro(ready) { introPlaying -> AppContent(koin, introPlaying) }
}

/**
 * [App] with its container passed in, so tests can run the whole app over fake platform services.
 * While [introPlaying], the system bar icons suit the intro over it rather than the app.
 */
@Composable
internal fun AppContent(koin: KoinApplication, introPlaying: Boolean = false) {
    KoinIsolatedContext(context = koin) {
        val scope = currentKoinScope()
        val repository = scope.get<IntervalRepository>()
        val ready by remember(scope) { storageReady(repository, scope.get()) }.collectAsStateWithLifecycle(false)
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
            // The workout and a session are dark in either theme, and so is the intro while it covers the app.
            val top = backStack.lastOrNull()
            SystemBarIcons(lightIcons = dark || top == Workout || top == Lifting || introPlaying)
            AppShell(backStack)
        }
    }
}

/**
 * One back stack: the Timer tab at the root, at most one other tab above it, then whatever was
 * pushed (Settings, Presets, Workout, a session). Back from Lift or History returns to Timer, as both
 * platforms expect. The navigation bar or rail shows only while a tab is on top, or while Settings sits beside
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
                transitionSpec = pageTransition(),
                popTransitionSpec = pagePopTransition(),
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
                entryProvider = appEntries(backStack, twoPane, selectTab),
            )
        }
    }
}

/** The screen for each [NavKey], and what each screen's actions do to the [backStack]. */
private fun appEntries(
    backStack: NavBackStack<NavKey>,
    twoPane: Boolean,
    selectTab: (Tab) -> Unit,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<TimerTab>(metadata = SupportingPaneSceneStrategy.mainPane() + pageKind(PageKind.Tab)) {
        TimerRoute(
            onStart = { backStack.push(Workout) },
            // Beside Timer, the gear closes Settings again.
            onOpenSettings = {
                when {
                    backStack.lastOrNull() != Settings -> backStack.add(Settings)
                    twoPane -> backStack.removeLastOrNull()
                }
            },
            onSeeAllPresets = { backStack.push(Presets) },
        )
    }
    entry<Presets> {
        PresetsRoute(
            onBack = { backStack.removeLastOrNull() },
            onShowTimer = { selectTab(TimerTab) },
            onStartWorkout = { backStack.push(Workout) },
        )
    }
    entry<LiftTab>(metadata = pageKind(PageKind.Tab)) {
        TodayRoute(
            onStartSession = { backStack.push(Lifting) },
            onChooseProgram = { backStack.push(Programs) },
            onOpenBells = { backStack.push(OwnedBells) },
        )
    }
    // Gym dark in either theme, as the workout is: read at arm's length, mid-set.
    entry<Lifting>(metadata = pageKind(PageKind.Mode)) {
        IntervalTheme(darkTheme = true) { SessionRoute(onExit = { backStack.removeLastOrNull() }) }
    }
    entry<Programs> { ProgramsRoute(onBack = { backStack.removeLastOrNull() }) }
    entry<OwnedBells> { GearRoute(onBack = { backStack.removeLastOrNull() }) }
    entry<LiftDetail> { key -> LiftDetailRoute(uid = key.uid, onBack = { backStack.removeLastOrNull() }) }
    entry<HistoryTab>(metadata = pageKind(PageKind.Tab)) {
        HistoryRoute(
            onOpenWorkout = { backStack.push(WorkoutDetail(it)) },
            onOpenLift = { backStack.push(LiftDetail(it)) },
            onPlan = { backStack.push(Plan) },
            onStartWorkout = { backStack.push(Workout) },
            onShowTimer = { selectTab(TimerTab) },
        )
    }
    entry<Settings>(metadata = SupportingPaneSceneStrategy.supportingPane()) {
        SettingsRoute(
            onBack = { backStack.removeLastOrNull() },
            onShowAbout = { backStack.push(About) },
            inPane = twoPane,
        )
    }
    entry<WorkoutDetail> { key ->
        WorkoutDetailRoute(
            id = key.id,
            onBack = { backStack.removeLastOrNull() },
            onStartWorkout = { backStack.push(Workout) },
        )
    }
    entry<Plan> { PlanRoute(onBack = { backStack.removeLastOrNull() }) }
    entry<Workout>(metadata = pageKind(PageKind.Mode)) { WorkoutRoute(onExit = { backStack.removeLastOrNull() }) }
    entry<About> {
        AboutRoute(
            onBack = { backStack.removeLastOrNull() },
            onShowLicenses = { backStack.push(Licenses) },
        )
    }
    entry<Licenses> { LicensesRoute(onBack = { backStack.removeLastOrNull() }) }
}

/**
 * True once both stores are read: the interval side's and Lift's. Until then the app draws nothing, so
 * no screen starts from defaults.
 */
private fun storageReady(interval: IntervalRepository, lift: BellsRepository): Flow<Boolean> =
    combine(interval.ready, lift.ready) { intervalReady, liftReady -> intervalReady && liftReady }

/** The line along the top of the bottom bar, drawn over the bottom edge of the content above it. */
private fun Modifier.hairlineAtBottom(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = Sizes.hairline.toPx()
    val y = size.height - stroke / 2
    drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
}

/** Pushes [key] unless it is on top already: a double tap on Start must not stack two workouts. */
private fun NavBackStack<NavKey>.push(key: NavKey) {
    if (lastOrNull() != key) add(key)
}

/** Shows [tab]: Timer is the root, so it pops back to it; any other tab replaces what is above Timer. */
private fun NavBackStack<NavKey>.selectTab(tab: Tab) {
    while (size > 1) removeLastOrNull()
    if (tab != TimerTab) add(tab)
}
