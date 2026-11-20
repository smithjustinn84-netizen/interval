@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package dev.justinsmith.interval

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation.BackNavigationBehavior
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.rememberSupportingPaneSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPredictivePopTransitionSpec
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.di.processKoin
import dev.justinsmith.interval.model.ThemeMode
import dev.justinsmith.interval.nav.About
import dev.justinsmith.interval.nav.ClassDetail
import dev.justinsmith.interval.nav.ConditioningTab
import dev.justinsmith.interval.nav.FlowEditor
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
import dev.justinsmith.interval.nav.WeekSetup
import dev.justinsmith.interval.nav.Welcome
import dev.justinsmith.interval.nav.Workout
import dev.justinsmith.interval.nav.WorkoutDetail
import dev.justinsmith.interval.platform.ActiveWorkout
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.platform.SystemBarIcons
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.platform.materialBackPreview
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.save_failed
import dev.justinsmith.interval.splash.WithIntro
import dev.justinsmith.interval.ui.AppSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.LocalRestingHeartRate
import dev.justinsmith.interval.ui.NavigationArea
import dev.justinsmith.interval.ui.NavigationAreaBar
import dev.justinsmith.interval.ui.NavigationItems
import dev.justinsmith.interval.ui.NoticeHost
import dev.justinsmith.interval.ui.Notices
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.WatchForIdle
import dev.justinsmith.interval.ui.backPreviewCorners
import dev.justinsmith.interval.ui.backPreviewTransition
import dev.justinsmith.interval.ui.navigationSuiteType
import dev.justinsmith.interval.ui.onlyBeside
import dev.justinsmith.interval.ui.pagePopTransition
import dev.justinsmith.interval.ui.pageTransition
import dev.justinsmith.interval.ui.rememberNavigationArea
import dev.justinsmith.interval.ui.rememberNotices
import dev.justinsmith.interval.ui.rememberTrainPillar
import dev.justinsmith.interval.ui.tabScenes
import dev.justinsmith.interval.weeksetup.isSetupTurn
import dev.justinsmith.interval.weeksetup.nothingYet
import dev.justinsmith.interval.weeksetup.opensOnSetup
import dev.justinsmith.lift.data.LiftRepository
import dev.justinsmith.lift.nav.LiftTab
import dev.justinsmith.lift.nav.Lifting
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
            subclass(ClassDetail::class)
            subclass(Plan::class)
            subclass(Presets::class)
            subclass(WeekSetup::class)
            subclass(Welcome::class)
            subclass(FlowEditor::class)
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
    WithIntro(ready) { introPlaying -> AppContent(koin, introPlaying, shortcut, onShortcutOpen, offerSetup = true) }
}

/**
 * [App] with its container passed in, so tests can run the whole app over fake platform services.
 * While [introPlaying], the system bar icons suit the intro over it rather than the app. With [offerSetup], as
 * the app itself runs, a new install's first plain open (no shortcut) lands on the welcome over Today, which ends
 * on Set up, once, and Today shows its First week card from then: a test leaves it off unless that is what it is
 * about, so the app opens on Today whatever its storage holds.
 */
@Composable
internal fun AppContent(
    koin: KoinApplication,
    introPlaying: Boolean = false,
    shortcut: Shortcut? = null,
    onShortcutOpen: () -> Unit = {},
    offerSetup: Boolean = false,
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
            val backStack = rememberAppBackStack(
                repository,
                lift,
                offerSetup = offerSetup,
                shortcutOpen = shortcut != null,
                setupOffered = settings.setupOffered,
            )
            val clock = koinInject<WallClock>()
            val opened by rememberUpdatedState(onShortcutOpen)
            LaunchedEffect(shortcut) {
                if (shortcut == null) return@LaunchedEffect
                backStack.open(shortcut, clock, startLift = { lift.sessions.startSession() })
                opened()
            }
            EndWithTheirPages(backStack)
            val notices = rememberNotices()
            // A save that failed is gone: say so, wherever the app is, rather than let it pass unnoticed.
            val saveFailed = stringResource(Res.string.save_failed)
            LaunchedEffect(repository, lift) {
                merge(repository.writeErrors, lift.writeErrors).collect { notices.show(saveFailed) }
            }
            // The intro is dark in either theme while it covers the app.
            SystemBarIcons(lightIcons = dark || introPlaying)
            WatchForIdle {
                CompositionLocalProvider(
                    LocalNotices provides notices,
                    // Every heart-rate zone drawn goes by the reserve over this, once one is set.
                    LocalRestingHeartRate provides settings.body.restingHeartRate,
                ) { AppShell(backStack, notices) }
            }
        }
    }
}

/**
 * The back stack the app opens on: Today, with the welcome over it when it is a new install's turn for Set up
 * (decided once, as storage stands on arrival, and marked taken whether or not it opens, so someone with the app
 * already never meets it unasked), or the stack restored, put right for what older builds saved.
 */
@Composable
private fun rememberAppBackStack(
    repository: IntervalRepository,
    lift: LiftRepository,
    offerSetup: Boolean,
    shortcutOpen: Boolean,
    setupOffered: Boolean,
): NavBackStack<NavKey> {
    // Set up's turn is this open's, if it is a plain one.
    val setupTurn = remember { isSetupTurn(offerSetup, shortcutOpen, setupOffered) }
    // A new install is welcomed on the way to Set up, and has its first week on Today from here on.
    val welcomed = remember { opensOnSetup(setupTurn, nothingYet(repository, lift)) }
    val start = remember {
        if (welcomed) arrayOf<NavKey>(TodayTab, Welcome) else arrayOf<NavKey>(TodayTab)
    }
    LaunchedEffect(setupTurn) {
        if (setupTurn) {
            repository.settings.updateAppSettings {
                it.copy(setupOffered = true, firstWeek = it.firstWeek || welcomed)
            }
        }
    }
    // A stack restored takes the place of [start]: the page left open is the one that comes back.
    val backStack = rememberNavBackStack(navConfiguration, *start)
    // Once, before anything reads it: a stack saved by a build that opened on the Timer, or had it as a tab.
    remember(backStack) {
        backStack.rootAtToday()
        backStack.timerUnderTrain()
    }
    return backStack
}

/**
 * The workout lives as long as its page is on the back stack, which outlives the screen when Android destroys it
 * in the background: once the page has gone, whichever way it left, so has the workout. Lift's timers the same
 * way: a session's page gone, however it went, takes its rest, EMOM or hold.
 */
@Composable
private fun EndWithTheirPages(backStack: List<NavKey>) {
    val workout = koinInject<ActiveWorkout>()
    val workoutOnStack = backStack.any { it is Workout }
    LaunchedEffect(workoutOnStack) { if (!workoutOnStack) workout.leave() }
    val timers = koinInject<ActiveTimers>()
    val liftingOnStack = backStack.any { it is Lifting }
    LaunchedEffect(liftingOnStack) { if (!liftingOnStack) timers.stopAll() }
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
    val selectTab = remember(backStack) { { tab: Tab -> backStack.selectTab(tab) } }
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
                        entryDecorators = appEntryDecorators(),
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
                        // Built again only when what it takes changes, not at every navigation: a new provider
                        // gives every entry on show new content, and each of them composes again.
                        entryProvider = remember(backStack, twoPane, settingsBeside, this, clock) {
                            appEntries(backStack, twoPane, settingsBeside, selectTab, sharedScope = this, clock)
                        },
                    )
                }
                NavigationAreaBar(
                    area,
                    visible = currentTab != null,
                    Modifier.align(if (area.bar) Alignment.BottomCenter else Alignment.TopStart),
                ) {
                    NavigationItems(shown.tab, area.type, pillar, selectTab)
                }
                AppNoticeHost(notices, area, onTab = currentTab != null, Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** Each entry's saved state and ViewModels; on Android, the back preview's corners clipping its content. */
@Composable
private fun appEntryDecorators(): List<NavEntryDecorator<NavKey>> = listOfNotNull(
    rememberSaveableStateHolderNavEntryDecorator(),
    rememberViewModelStoreNavEntryDecorator(),
    if (materialBackPreview) backPreviewCorners() else null,
)

/** A notice over the content's foot: above the bar or rail while a tab is on top ([onTab]), else the safe area. */
@Composable
private fun AppNoticeHost(notices: Notices, area: NavigationArea, onTab: Boolean, modifier: Modifier = Modifier) {
    NoticeHost(
        notices,
        modifier
            .then(if (onTab) Modifier.windowInsetsPadding(area.tabInsets()) else Modifier.safeDrawingPadding())
            .padding(Spacing.space16),
    )
}

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
 * True once both stores are read: the interval side's and Lift's. Until then the app draws nothing, so
 * no screen starts from defaults.
 */
private fun storageReady(interval: IntervalRepository, lift: LiftRepository): Flow<Boolean> =
    combine(interval.ready, lift.ready) { intervalReady, liftReady -> intervalReady && liftReady }
