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
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
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
import dev.justinsmith.interval.about.AboutRoute
import dev.justinsmith.interval.about.LicensesRoute
import dev.justinsmith.interval.about.PrivacyScreen
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.di.processKoin
import dev.justinsmith.interval.history.HistoryPlaceholder
import dev.justinsmith.interval.history.HistoryRoute
import dev.justinsmith.interval.history.WorkoutDetailRoute
import dev.justinsmith.interval.home.TimerRoute
import dev.justinsmith.interval.mobility.MobilityPage
import dev.justinsmith.interval.mobility.MobilityRoute
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.nav.About
import dev.justinsmith.interval.nav.ConditioningTab
import dev.justinsmith.interval.nav.HistoryTab
import dev.justinsmith.interval.nav.Licenses
import dev.justinsmith.interval.nav.MobilityTab
import dev.justinsmith.interval.nav.Plan
import dev.justinsmith.interval.nav.Presets
import dev.justinsmith.interval.nav.Privacy
import dev.justinsmith.interval.nav.Settings
import dev.justinsmith.interval.nav.Shortcut
import dev.justinsmith.interval.nav.Tab
import dev.justinsmith.interval.nav.Timer
import dev.justinsmith.interval.nav.TodayTab
import dev.justinsmith.interval.nav.Workout
import dev.justinsmith.interval.nav.WorkoutDetail
import dev.justinsmith.interval.nav.push
import dev.justinsmith.interval.plan.PlanRoute
import dev.justinsmith.interval.platform.ActiveWorkout
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.platform.SystemBarIcons
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.materialBackPreview
import dev.justinsmith.interval.presets.PresetsRoute
import dev.justinsmith.interval.presets.PresetsTab
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.nav_train
import dev.justinsmith.interval.resources.save_failed
import dev.justinsmith.interval.settings.SettingsRoute
import dev.justinsmith.interval.splash.WithIntro
import dev.justinsmith.interval.today.HomeRoute
import dev.justinsmith.interval.ui.AppSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NavigationAreaBar
import dev.justinsmith.interval.ui.NavigationItems
import dev.justinsmith.interval.ui.NoticeHost
import dev.justinsmith.interval.ui.Notices
import dev.justinsmith.interval.ui.PageKind
import dev.justinsmith.interval.ui.PillarPicker
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TRAIN_GROUP
import dev.justinsmith.interval.ui.TrainPillars
import dev.justinsmith.interval.ui.WatchForIdle
import dev.justinsmith.interval.ui.backPreviewCorners
import dev.justinsmith.interval.ui.backPreviewTransition
import dev.justinsmith.interval.ui.navigationSuiteType
import dev.justinsmith.interval.ui.onlyBeside
import dev.justinsmith.interval.ui.pageKind
import dev.justinsmith.interval.ui.pagePopTransition
import dev.justinsmith.interval.ui.pageTransition
import dev.justinsmith.interval.ui.rememberNavigationArea
import dev.justinsmith.interval.ui.rememberNotices
import dev.justinsmith.interval.ui.tabGroup
import dev.justinsmith.interval.ui.tabScenes
import dev.justinsmith.interval.workout.WorkoutRoute
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.nav.LiftDetail
import dev.justinsmith.lift.nav.LiftTab
import dev.justinsmith.lift.nav.Lifting
import dev.justinsmith.lift.nav.Programs
import dev.justinsmith.lift.nav.liftEntries
import dev.justinsmith.lift.nav.liftRoutes
import dev.justinsmith.lift.platform.ActiveTimers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.plus
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import org.jetbrains.compose.reload.DevelopmentEntryPoint
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinIsolatedContext
import org.koin.compose.koinInject
import org.koin.core.KoinApplication

/**
 * Every [NavKey] the back stack can hold, registered explicitly so saving and restoring works on
 * every platform (native has no reflection-based serialization).
 */
internal val navConfiguration = SavedStateConfiguration {
    serializersModule = serializersModule + SerializersModule {
        polymorphic(NavKey::class) {
            subclass(TodayTab::class)
            subclass(Timer::class)
            subclass(LiftTab::class)
            subclass(ConditioningTab::class)
            subclass(MobilityTab::class)
            subclass(HistoryTab::class)
            subclass(Settings::class)
            subclass(Workout::class)
            subclass(About::class)
            subclass(Licenses::class)
            subclass(Privacy::class)
            subclass(WorkoutDetail::class)
            subclass(Plan::class)
            subclass(Presets::class)
            liftRoutes()
        }
    }
}

/**
 * The root composable every platform entry point hosts: the app, with the intro over it at launch. [shortcut] is
 * a launcher shortcut to open (Android's, or an iOS quick action); the app opens it once it is ready and then calls [onShortcutOpen],
 * whose caller lets it go, so the same shortcut handed again opens again.
 */
@Composable
@DevelopmentEntryPoint
fun App(shortcut: Shortcut? = null, onShortcutOpen: () -> Unit = {}) {
    val koin = remember { processKoin() }
    val ready by remember(koin) { storageReady(koin.koin.get(), koin.koin.get()) }.collectAsStateWithLifecycle(false)
    WithIntro(ready) { introPlaying -> AppContent(koin, introPlaying, shortcut, onShortcutOpen) }
}

/**
 * [App] with its container passed in, so tests can run the whole app over fake platform services.
 * While [introPlaying], the system bar icons suit the intro over it rather than the app.
 */
@Composable
internal fun AppContent(
    koin: KoinApplication,
    introPlaying: Boolean = false,
    shortcut: Shortcut? = null,
    onShortcutOpen: () -> Unit = {},
) {
    KoinIsolatedContext(context = koin) {
        val repository = koinInject<IntervalRepository>()
        val lift = koinInject<LiftRepository>()
        val ready by remember(repository, lift) { storageReady(repository, lift) }.collectAsStateWithLifecycle(false)
        // The cue player is built on first use, and Android's speech engine takes a moment to start: starting it
        // with the app, while storage reads, means the first workout cue is never the one it misses.
        koinInject<CuePlayer>()
        // Saved settings take milliseconds to read. Until then the window background shows, rather than
        // screens built from defaults or a flash of the wrong theme.
        if (!ready) return@KoinIsolatedContext
        val settings by repository.settings.appSettings.collectAsStateWithLifecycle()
        val dark = when (settings.theme) {
            ThemeMode.System -> isSystemInDarkTheme()
            ThemeMode.Light -> false
            ThemeMode.Dark -> true
        }
        IntervalTheme(darkTheme = dark) {
            val backStack = rememberNavBackStack(navConfiguration, TodayTab)
            // Once, before anything reads it: a stack saved by a build that opened on the Timer, or had it as a tab.
            remember(backStack) {
                backStack.rootAtToday()
                backStack.timerUnderTrain()
            }
            val clock = koinInject<WallClock>()
            val opened by rememberUpdatedState(onShortcutOpen)
            LaunchedEffect(shortcut) {
                if (shortcut == null) return@LaunchedEffect
                backStack.open(shortcut, clock, startLift = { lift.sessions.startSession() })
                opened()
            }
            // The workout lives as long as its page is on the back stack, which outlives the screen when Android
            // destroys it in the background: once the page has gone, whichever way it left, so has the workout.
            val workout = koinInject<ActiveWorkout>()
            val workoutOnStack = backStack.any { it is Workout }
            LaunchedEffect(workoutOnStack) { if (!workoutOnStack) workout.leave() }
            // Lift's timers the same way: a session's page gone, however it went, takes its rest, EMOM or hold.
            val timers = koinInject<ActiveTimers>()
            val liftingOnStack = backStack.any { it is Lifting }
            LaunchedEffect(liftingOnStack) { if (!liftingOnStack) timers.stopAll() }
            val notices = rememberNotices()
            // A save that failed is gone: say so, wherever the app is, rather than let it pass unnoticed.
            val saveFailed = stringResource(Res.string.save_failed)
            LaunchedEffect(repository, lift) {
                merge(repository.writeErrors, lift.writeErrors).collect { notices.show(saveFailed) }
            }
            // The intro is dark in either theme while it covers the app.
            SystemBarIcons(lightIcons = dark || introPlaying)
            WatchForIdle {
                CompositionLocalProvider(LocalNotices provides notices) { AppShell(backStack, notices) }
            }
        }
    }
}

/**
 * One back stack: the Today tab at the root, at most one other tab above it (a pillar of Train, or History), then
 * whatever was pushed (the Timer, Settings, Workout, a session). Back from Train or History returns to Today, as
 * both platforms expect. The navigation bar or rail shows only while a tab is on top, or while a page sits beside
 * History's log: windows of Material's expanded width and up open a finished workout or session there, a pane
 * waiting for one until it is picked. Windows of [AppSizes.twoPaneMinWidth] and up open Settings as a supporting
 * pane beside the Timer, a pair of pages with no bar (opened from a tab it is a page of its own, whatever the
 * window: [settingsOverTimer]). The bar or rail lies over the pages and slides away as a page is pushed over its
 * tab ([NavigationAreaBar]).
 */
@Composable
private fun AppShell(backStack: NavBackStack<NavKey>, notices: Notices) {
    val top = backStack.lastOrNull()
    val clock = koinInject<WallClock>()
    val panes = rememberPanes()
    val twoPane = panes.historyBeside
    val settingsBeside = panes.settingsBeside && backStack.settingsOverTimer
    val currentTab = top as? Tab ?: HistoryTab.takeIf { twoPane && top.opensBesideHistory }
    val selectTab = { tab: Tab -> backStack.selectTab(tab) }
    val pillar = rememberTrainPillar(backStack)
    val palette = IntervalTheme.palette
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The bar or rail is decided by the window alone and lies over the pages: NavDisplay fills the window
        // whatever is on top, so a page lays out once, at its size. A tab's scene keeps the area's room clear
        // itself and holds it while it leaves, so the tab under a push stays still as the area slides off.
        val area = rememberNavigationArea(navigationSuiteType(maxWidth, maxHeight))
        val tabScenes = remember(area) { area.tabScenes<NavKey>() }
        // The area still names the tab it leaves with.
        val shown = remember { ShownTab(currentTab ?: TodayTab) }
        currentTab?.let { shown.tab = it }
        Surface(Modifier.fillMaxSize(), color = palette.background, contentColor = palette.text) {
            // NavDisplay stays at one place in the tree whatever the area does, so its entries keep their state.
            // A notice floats over the content's foot: above the bar, and over a full-screen page.
            Box(Modifier.fillMaxSize()) {
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
                        sceneDecoratorStrategies = listOf(tabScenes),
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
                        entryProvider = appEntries(
                            backStack,
                            twoPane,
                            settingsBeside,
                            selectTab,
                            sharedScope = this,
                            clock,
                        ),
                    )
                }
                NavigationAreaBar(
                    area,
                    visible = currentTab != null,
                    Modifier.align(if (area.bar) Alignment.BottomCenter else Alignment.TopStart),
                ) {
                    NavigationItems(shown.tab, area.type, pillar, selectTab)
                }
                NoticeHost(
                    notices,
                    Modifier
                        .align(Alignment.BottomCenter)
                        .then(
                            if (currentTab != null) {
                                Modifier.windowInsetsPadding(area.tabInsets())
                            } else {
                                Modifier.safeDrawingPadding()
                            },
                        )
                        .padding(Spacing.space16),
                )
            }
        }
    }
}

/**
 * The pillar Train opens on from the bar: the one last shown, Strength before either was. It follows the tab over
 * Today however it got there (the picker, a shortcut, a restored stack), pages pushed over it or not.
 */
@Composable
private fun rememberTrainPillar(backStack: List<NavKey>): Tab {
    // Its place among the pillars, which every platform can save; a key can't be.
    var last by rememberSaveable(saver = PillarSaver) { mutableIntStateOf(0) }
    val shown = TrainPillars.indexOf(backStack.getOrNull(1))
    SideEffect { if (shown >= 0) last = shown }
    return TrainPillars.getOrElse(last) { TrainPillars.first() }
}

/**
 * Saves a pillar's place among [TrainPillars]. A build with two pillars saved a Boolean where this is, true for
 * Conditioning, which still reads as that pillar.
 */
private val PillarSaver = Saver<MutableIntState, Any>(
    save = { it.intValue },
    restore = { saved -> mutableIntStateOf(if (saved is Boolean) saved.compareTo(false) else saved as? Int ?: 0) },
)

/** The tab the navigation area names: the one on top, or the last one while the area leaves. */
private class ShownTab(var tab: Tab)

/** Which pages the window pairs, and the scene strategies that lay the pairs out. */
private class Panes(
    val strategies: List<SceneStrategy<NavKey>>,
    /** A finished workout or session opens beside History's log. */
    val historyBeside: Boolean,
    /** The window has room for Settings beside the Timer, where it was opened from there. */
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
    val settingsBeside = windowWidth >= AppSizes.twoPaneMinWidth
    val settingsDirective = historyDirective.copy(
        maxHorizontalPartitions = if (settingsBeside) 2 else 1,
        defaultPanePreferredWidth = AppSizes.settingsPane,
    )
    val supportingPane = rememberSupportingPaneSceneStrategy<NavKey>(directive = settingsDirective)
    // The placeholder beside History counts as a pane, so the default behaviour would pop History along with
    // the workout beside it, landing on Today.
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
 * workout opens beside History's log, [settingsBeside] when Settings is open beside the Timer. Every tab's gear
 * and the Timer's opens Settings; only over the Timer does it take the supporting pane.
 */
private fun appEntries(
    backStack: NavBackStack<NavKey>,
    twoPane: Boolean,
    settingsBeside: Boolean,
    selectTab: (Tab) -> Unit,
    sharedScope: SharedTransitionScope,
    clock: WallClock,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    todayEntry(backStack, selectTab, clock)
    timerEntry(backStack, settingsBeside, sharedScope, clock)
    trainEntries(backStack, twoPane, selectTab, clock)
    entry<HistoryTab>(
        metadata = ListDetailSceneStrategy.listPane(HISTORY_SCENE, detailPlaceholder = { HistoryPlaceholder() }) +
            ListDetailSceneStrategy.preferredPaneSize(width = AppSizes.listPane) +
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
            onStartWorkout = { backStack.startWorkout(clock, it) },
            onShowTimer = { backStack.showTimer() },
            onStartSession = { backStack.push(Lifting) },
            onOpenSettings = { backStack.push(Settings) },
        )
    }
    entry<Settings>(
        // The supporting pane's scene is made of the entries that carry its metadata, and of the tabs only the
        // Timer does: over Lift or History, Settings beside it would be beside nothing.
        metadata = {
            if (backStack.settingsOverTimer) {
                SupportingPaneSceneStrategy.supportingPane() + pageKind(PageKind.Beside)
            } else {
                emptyMap()
            }
        },
    ) {
        SettingsRoute(
            onBack = { backStack.removeLastOrNull() },
            onShowAbout = { backStack.push(About) },
            onShowPlan = { backStack.push(Plan) },
            // Beside the Timer its start is the Timer's edge, not the window's.
            modifier = if (settingsBeside) {
                Modifier.consumeWindowInsets(safeDrawingAt(WindowInsetsSides.Start))
            } else {
                Modifier
            },
            inPane = settingsBeside,
        )
    }
    entry<WorkoutDetail>(metadata = ListDetailSceneStrategy.detailPane(HISTORY_SCENE)) { key ->
        WorkoutDetailRoute(
            id = key.id,
            onBack = { backStack.removeLastOrNull() },
            onStartWorkout = { backStack.startWorkout(clock, it) },
            inPane = twoPane,
        )
    }
    entry<Plan> { PlanRoute(onBack = { backStack.removeLastOrNull() }) }
    entry<Workout>(metadata = pageKind(PageKind.Mode)) { key ->
        WorkoutRoute(id = key.id, onExit = { backStack.removeLastOrNull() }, flow = key.flow)
    }
    entry<About> {
        AboutRoute(
            onBack = { backStack.removeLastOrNull() },
            onShowLicenses = { backStack.push(Licenses) },
            onShowPrivacy = { backStack.push(Privacy) },
        )
    }
    entry<Licenses> { LicensesRoute(onBack = { backStack.removeLastOrNull() }) }
    entry<Privacy> { PrivacyScreen(onBack = { backStack.removeLastOrNull() }) }
}

/**
 * The Timer, a page pushed over Train's Conditioning pillar, and the main pane when Settings opens beside it. Back
 * and its presets square both return to Conditioning, through Settings if that is open beside it.
 */
private fun EntryProviderScope<NavKey>.timerEntry(
    backStack: NavBackStack<NavKey>,
    settingsBeside: Boolean,
    sharedScope: SharedTransitionScope,
    clock: WallClock,
) {
    entry<Timer>(metadata = SupportingPaneSceneStrategy.mainPane()) {
        val beside = settingsBeside && backStack.lastOrNull() == Settings
        TimerRoute(
            onStart = { backStack.startWorkout(clock) },
            // Beside Timer, the gear closes Settings again.
            onOpenSettings = {
                when {
                    backStack.lastOrNull() != Settings -> backStack.add(Settings)
                    settingsBeside -> backStack.removeLastOrNull()
                }
            },
            onSeeAllPresets = { backStack.leaveTimer() },
            onBack = { backStack.leaveTimer() },
            // As Settings opens or closes beside it, the Timer lays out at the size it is going to, once,
            // rather than squeezing through every width on the way, and shows only as much as its pane
            // has grown to, so Settings is seen leaving rather than covered at once. With Settings beside it
            // its end is Settings' edge, not the window's.
            modifier = with(sharedScope) { Modifier.clipToBounds().skipToLookaheadSize() }
                .then(if (beside) Modifier.consumeWindowInsets(safeDrawingAt(WindowInsetsSides.End)) else Modifier),
        )
    }
}

/** The window's safe-drawing insets on [sides] alone: what a pane gives up where its edge is another pane's. */
@Composable
private fun safeDrawingAt(sides: WindowInsetsSides): WindowInsets = WindowInsets.safeDrawing.only(sides)

/**
 * Train's pillars, each a tab of its own in one group, under the same title and the picker between them:
 * Strength is Lift's tab with every page of Lift's, Conditioning the presets as a tab's page, Mobility the flows,
 * each started on the workout screen with the Timer left as it is.
 */
private fun EntryProviderScope<NavKey>.trainEntries(
    backStack: NavBackStack<NavKey>,
    twoPane: Boolean,
    selectTab: (Tab) -> Unit,
    clock: WallClock,
) {
    val pillars = tabGroup(TRAIN_GROUP)
    liftEntries(
        backStack,
        twoPane,
        HISTORY_SCENE,
        onOpenSettings = { backStack.push(Settings) },
        tabMetadata = pillars,
        tabHeader = { PillarPicker(LiftTab, selectTab) },
    )
    entry<ConditioningTab>(metadata = pageKind(PageKind.Tab) + pillars) {
        val tab = PresetsTab(
            stringResource(Res.string.nav_train),
            onOpenSettings = { backStack.push(Settings) },
            onOpenTimer = { backStack.showTimer() },
        ) { PillarPicker(ConditioningTab, selectTab) }
        PresetsRoute(
            onShowTimer = { backStack.showTimer() },
            onStartWorkout = { backStack.startWorkout(clock) },
            tab = tab,
        )
    }
    entry<MobilityTab>(metadata = pageKind(PageKind.Tab) + pillars) {
        val page = MobilityPage(
            stringResource(Res.string.nav_train),
            onOpenSettings = { backStack.push(Settings) },
        ) { PillarPicker(MobilityTab, selectTab) }
        MobilityRoute(page, onStartFlow = { backStack.startWorkout(clock, flow = it) })
    }
}

/**
 * Today, the root of the stack. It starts what its screen set up: the Timer was handed the workout, and Lift told
 * of the session, before either page opens.
 */
private fun EntryProviderScope<NavKey>.todayEntry(
    backStack: NavBackStack<NavKey>,
    selectTab: (Tab) -> Unit,
    clock: WallClock,
) {
    entry<TodayTab>(metadata = pageKind(PageKind.Tab)) {
        HomeRoute(
            onStartWorkout = { backStack.startWorkout(clock, it) },
            onStartSession = { backStack.push(Lifting) },
            onPlan = { backStack.push(Plan) },
            onPrograms = { backStack.push(Programs) },
            onOpenHistory = { selectTab(HistoryTab) },
            onOpenTimer = { backStack.showTimer() },
            onOpenSettings = { backStack.push(Settings) },
        )
    }
}

/**
 * True once both stores are read: the interval side's and Lift's. Until then the app draws nothing, so
 * no screen starts from defaults.
 */
private fun storageReady(interval: IntervalRepository, lift: LiftRepository): Flow<Boolean> =
    combine(interval.ready, lift.ready) { intervalReady, liftReady -> intervalReady && liftReady }

/** The scene History's log and the workout beside it share, apart from Settings beside the Timer. */
private const val HISTORY_SCENE = "history"
