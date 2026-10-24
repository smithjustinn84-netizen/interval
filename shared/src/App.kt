@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package dev.justinsmith.interval

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.gear.GearRoute
import dev.justinsmith.bells.history.LiftDetailRoute
import dev.justinsmith.bells.library.ExerciseLibraryRoute
import dev.justinsmith.bells.programs.ProgramEditorRoute
import dev.justinsmith.bells.programs.ProgramsRoute
import dev.justinsmith.bells.session.SessionRoute
import dev.justinsmith.bells.today.TodayRoute
import dev.justinsmith.interval.about.AboutRoute
import dev.justinsmith.interval.about.LicensesRoute
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.di.rememberAppKoin
import dev.justinsmith.interval.history.HistoryPlaceholder
import dev.justinsmith.interval.history.HistoryRoute
import dev.justinsmith.interval.history.WorkoutDetailRoute
import dev.justinsmith.interval.home.TimerRoute
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.nav.About
import dev.justinsmith.interval.nav.ExerciseLibrary
import dev.justinsmith.interval.nav.HistoryTab
import dev.justinsmith.interval.nav.Licenses
import dev.justinsmith.interval.nav.LiftDetail
import dev.justinsmith.interval.nav.LiftTab
import dev.justinsmith.interval.nav.Lifting
import dev.justinsmith.interval.nav.OwnedBells
import dev.justinsmith.interval.nav.Plan
import dev.justinsmith.interval.nav.Presets
import dev.justinsmith.interval.nav.ProgramEditor
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
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NavigationItems
import dev.justinsmith.interval.ui.NoticeHost
import dev.justinsmith.interval.ui.Notices
import dev.justinsmith.interval.ui.PageKind
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.backPreviewCorners
import dev.justinsmith.interval.ui.backPreviewTransition
import dev.justinsmith.interval.ui.navigationSuiteColors
import dev.justinsmith.interval.ui.navigationSuiteType
import dev.justinsmith.interval.ui.onlyBeside
import dev.justinsmith.interval.ui.pageKind
import dev.justinsmith.interval.ui.pagePopTransition
import dev.justinsmith.interval.ui.pageTransition
import dev.justinsmith.interval.ui.rememberNotices
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
            subclass(ProgramEditor::class)
            subclass(OwnedBells::class)
            subclass(ExerciseLibrary::class)
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
            val notices = rememberNotices()
            // The intro is dark in either theme while it covers the app.
            SystemBarIcons(lightIcons = dark || introPlaying)
            CompositionLocalProvider(LocalNotices provides notices) { AppShell(backStack, notices) }
        }
    }
}

/**
 * One back stack: the Timer tab at the root, at most one other tab above it, then whatever was
 * pushed (Settings, Presets, Workout, a session). Back from Lift or History returns to Timer, as both
 * platforms expect. The navigation bar or rail shows only while a tab is on top, or while a page sits beside
 * its tab: windows of [Sizes.twoPaneMinWidth] and up open Settings as a supporting pane beside Timer, and
 * windows of Material's expanded width and up a finished workout or session beside History's log, a pane
 * waiting for one until it is picked.
 */
@Composable
private fun AppShell(backStack: NavBackStack<NavKey>, notices: Notices) {
    val top = backStack.lastOrNull()
    val panes = rememberPanes()
    val twoPane = panes.historyBeside
    val settingsBeside = panes.settingsBeside
    val currentTab = top as? Tab ?: when {
        settingsBeside && top == Settings -> TimerTab
        twoPane && top.opensBesideHistory -> HistoryTab
        else -> null
    }
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
            val content = Modifier
                .fillMaxSize()
                .then(if (bar) Modifier.hairlineAtBottom(palette.line) else Modifier)
                .then(
                    if (insets != null) {
                        Modifier.windowInsetsPadding(insets).consumeWindowInsets(insets)
                    } else {
                        Modifier
                    },
                )
            // NavDisplay stays at one place in the tree whatever the bars do, so its entries keep their state.
            // A notice floats over the content's foot: above the bar, and over a full-screen mode.
            Box(content) {
                SharedTransitionLayout(Modifier.fillMaxSize()) {
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
                        sceneStrategies = panes.strategies,
                        // The Timer is in the scene both alone and beside Settings, so it moves between them
                        // rather than fading out of one and into the other.
                        sharedTransitionScope = this,
                        transitionSpec = pageTransition(),
                        popTransitionSpec = pagePopTransition(),
                        predictivePopTransitionSpec = if (materialBackPreview) {
                            backPreviewTransition()
                        } else {
                            defaultPredictivePopTransitionSpec()
                        },
                        modifier = Modifier.fillMaxSize(),
                        entryProvider = appEntries(backStack, twoPane, settingsBeside, selectTab, sharedScope = this),
                    )
                }
                NoticeHost(
                    notices,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .then(if (insets == null) Modifier.safeDrawingPadding() else Modifier)
                        .padding(Spacing.space16),
                )
            }
        }
    }
}

/** Which pages the window pairs, and the scene strategies that lay the pairs out. */
private class Panes(
    val strategies: List<SceneStrategy<NavKey>>,
    /** A finished workout or session opens beside History's log. */
    val historyBeside: Boolean,
    /** Settings opens beside the Timer. */
    val settingsBeside: Boolean,
)

@Composable
private fun rememberPanes(): Panes {
    // Side by side only: Material would also stack a pair on a tall tablet in portrait, which leaves both
    // too short. There the second page opens full screen, as on a phone.
    val historyDirective = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
        .copy(maxVerticalPartitions = 1, verticalPartitionSpacerSize = 0.dp)
    // Settings pairs only with room for a phone-wide Timer beside it: from Material's 840dp the Timer would be
    // left narrower than a phone.
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val settingsBeside = windowWidth >= Sizes.twoPaneMinWidth
    val settingsDirective = historyDirective.copy(
        maxHorizontalPartitions = if (settingsBeside) 2 else 1,
        defaultPanePreferredWidth = Sizes.settingsPane,
    )
    val supportingPane = rememberSupportingPaneSceneStrategy<NavKey>(directive = settingsDirective)
    // The placeholder beside History counts as a pane, so the default behaviour would pop History along with
    // the workout beside it, landing on the Timer.
    val listDetail = rememberListDetailSceneStrategy<NavKey>(
        backNavigationBehavior = BackNavigationBehavior.PopLatest,
        directive = historyDirective,
    )
    return Panes(
        strategies = listOf(supportingPane.onlyBeside(), listDetail),
        historyBeside = historyDirective.maxHorizontalPartitions > 1,
        settingsBeside = settingsBeside,
    )
}

/**
 * The screen for each [NavKey], and what each screen's actions do to the [backStack]. [twoPane] when a finished
 * workout opens beside History's log, [settingsBeside] when Settings opens beside the Timer.
 */
private fun appEntries(
    backStack: NavBackStack<NavKey>,
    twoPane: Boolean,
    settingsBeside: Boolean,
    selectTab: (Tab) -> Unit,
    sharedScope: SharedTransitionScope,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    entry<TimerTab>(metadata = SupportingPaneSceneStrategy.mainPane() + pageKind(PageKind.Tab)) {
        TimerRoute(
            onStart = { backStack.push(Workout) },
            // Beside Timer, the gear closes Settings again.
            onOpenSettings = {
                when {
                    backStack.lastOrNull() != Settings -> backStack.add(Settings)
                    settingsBeside -> backStack.removeLastOrNull()
                }
            },
            onSeeAllPresets = { backStack.push(Presets) },
            // As Settings opens or closes beside it, the Timer lays out at the size it is going to, once,
            // rather than squeezing through every width on the way, and shows only as much as its pane
            // has grown to, so Settings is seen leaving rather than covered at once.
            modifier = with(sharedScope) { Modifier.clipToBounds().skipToLookaheadSize() },
        )
    }
    entry<Presets> {
        PresetsRoute(
            onBack = { backStack.removeLastOrNull() },
            onShowTimer = { selectTab(TimerTab) },
            onStartWorkout = { backStack.push(Workout) },
        )
    }
    liftEntries(backStack, twoPane)
    entry<HistoryTab>(
        metadata = ListDetailSceneStrategy.listPane(HISTORY_SCENE, detailPlaceholder = { HistoryPlaceholder() }) +
            ListDetailSceneStrategy.preferredPaneSize(width = Sizes.listPane) +
            pageKind(PageKind.Tab),
    ) {
        // Beside the log, the row whose workout is open shows it.
        val open = backStack.lastOrNull().takeIf { twoPane }
        HistoryRoute(
            selectedWorkout = (open as? WorkoutDetail)?.id,
            selectedLift = (open as? LiftDetail)?.uid,
            onOpenWorkout = { backStack.openDetail(WorkoutDetail(it)) },
            onOpenLift = { backStack.openDetail(LiftDetail(it, fromHistory = true)) },
            onPlan = { backStack.push(Plan) },
            onStartWorkout = { backStack.push(Workout) },
            onShowTimer = { selectTab(TimerTab) },
            onStartSession = { backStack.push(Lifting) },
        )
    }
    entry<Settings>(metadata = SupportingPaneSceneStrategy.supportingPane() + pageKind(PageKind.Beside)) {
        SettingsRoute(
            onBack = { backStack.removeLastOrNull() },
            onShowAbout = { backStack.push(About) },
            inPane = settingsBeside,
        )
    }
    entry<WorkoutDetail>(metadata = ListDetailSceneStrategy.detailPane(HISTORY_SCENE)) { key ->
        WorkoutDetailRoute(
            id = key.id,
            onBack = { backStack.removeLastOrNull() },
            onStartWorkout = { backStack.push(Workout) },
            inPane = twoPane,
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
 * Lift's screens: the tab, a session, programs, bells, the exercise library and a finished session. A session
 * opened from History goes beside its log when [twoPane]; from Lift it is a page of its own, as Lift has no list.
 */
private fun EntryProviderScope<NavKey>.liftEntries(backStack: NavBackStack<NavKey>, twoPane: Boolean) {
    entry<LiftTab>(metadata = pageKind(PageKind.Tab)) {
        TodayRoute(
            onStartSession = { backStack.push(Lifting) },
            onChooseProgram = { backStack.push(Programs) },
            onOpenBells = { backStack.push(OwnedBells) },
            onOpenSession = { backStack.push(LiftDetail(it)) },
            onOpenExercises = { backStack.push(ExerciseLibrary) },
        )
    }
    entry<Lifting>(metadata = pageKind(PageKind.Mode)) {
        SessionRoute(onExit = { backStack.removeLastOrNull() })
    }
    entry<Programs> {
        ProgramsRoute(
            onNew = { backStack.push(ProgramEditor()) },
            onEdit = { backStack.push(ProgramEditor(it)) },
            onBack = { backStack.removeLastOrNull() },
        )
    }
    entry<ProgramEditor> { key -> ProgramEditorRoute(uid = key.uid, onBack = { backStack.removeLastOrNull() }) }
    entry<OwnedBells> { GearRoute(onBack = { backStack.removeLastOrNull() }) }
    entry<ExerciseLibrary> {
        ExerciseLibraryRoute(onBack = { backStack.removeLastOrNull() })
    }
    entry<LiftDetail>(
        metadata = { if (it.fromHistory) ListDetailSceneStrategy.detailPane(HISTORY_SCENE) else emptyMap() },
    ) { key ->
        LiftDetailRoute(
            uid = key.uid,
            onBack = { backStack.removeLastOrNull() },
            inPane = twoPane && key.fromHistory,
        )
    }
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

/** The scene History's log and the workout beside it share, apart from Settings beside the Timer. */
private const val HISTORY_SCENE = "history"

/** A finished workout or session opened from History: on a wide window it sits beside the log. */
private val NavKey?.opensBesideHistory: Boolean
    get() = this is WorkoutDetail || (this is LiftDetail && fromHistory)

/**
 * Opens [key] beside History's log, in place of whatever was open there, so Back closes it rather than walking
 * through every workout looked at. On a phone nothing is open over History when a row is tapped.
 */
private fun NavBackStack<NavKey>.openDetail(key: NavKey) {
    if (lastOrNull() == key) return
    while (lastOrNull().opensBesideHistory) removeLastOrNull()
    add(key)
}

/** Shows [tab]: Timer is the root, so it pops back to it; any other tab replaces what is above Timer. */
private fun NavBackStack<NavKey>.selectTab(tab: Tab) {
    while (size > 1) removeLastOrNull()
    if (tab != TimerTab) add(tab)
}
