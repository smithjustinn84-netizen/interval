@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package dev.justinsmith.interval

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.material3.adaptive.navigation3.SupportingPaneSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import dev.justinsmith.interval.about.AboutRoute
import dev.justinsmith.interval.about.LicensesRoute
import dev.justinsmith.interval.about.PrivacyScreen
import dev.justinsmith.interval.history.ClassDetailRoute
import dev.justinsmith.interval.history.HistoryPlaceholder
import dev.justinsmith.interval.history.HistoryRoute
import dev.justinsmith.interval.history.WorkoutDetailRoute
import dev.justinsmith.interval.home.TimerRoute
import dev.justinsmith.interval.mobility.FlowEditorRoute
import dev.justinsmith.interval.mobility.MobilityActions
import dev.justinsmith.interval.mobility.MobilityPage
import dev.justinsmith.interval.mobility.MobilityRoute
import dev.justinsmith.interval.nav.About
import dev.justinsmith.interval.nav.ClassDetail
import dev.justinsmith.interval.nav.ConditioningTab
import dev.justinsmith.interval.nav.FlowEditor
import dev.justinsmith.interval.nav.HistoryTab
import dev.justinsmith.interval.nav.Licenses
import dev.justinsmith.interval.nav.MobilityTab
import dev.justinsmith.interval.nav.Plan
import dev.justinsmith.interval.nav.Privacy
import dev.justinsmith.interval.nav.Settings
import dev.justinsmith.interval.nav.Tab
import dev.justinsmith.interval.nav.Timer
import dev.justinsmith.interval.nav.TodayTab
import dev.justinsmith.interval.nav.WeekSetup
import dev.justinsmith.interval.nav.Welcome
import dev.justinsmith.interval.nav.Workout
import dev.justinsmith.interval.nav.WorkoutDetail
import dev.justinsmith.interval.nav.push
import dev.justinsmith.interval.plan.PlanRoute
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.presets.PresetsRoute
import dev.justinsmith.interval.presets.PresetsTab
import dev.justinsmith.interval.resources.Res
import dev.justinsmith.interval.resources.nav_train
import dev.justinsmith.interval.settings.SettingsRoute
import dev.justinsmith.interval.today.HomeRoute
import dev.justinsmith.interval.ui.AppSizes
import dev.justinsmith.interval.ui.PageKind
import dev.justinsmith.interval.ui.PillarPicker
import dev.justinsmith.interval.ui.TRAIN_GROUP
import dev.justinsmith.interval.ui.pageKind
import dev.justinsmith.interval.ui.tabGroup
import dev.justinsmith.interval.weeksetup.WeekSetupRoute
import dev.justinsmith.interval.welcome.WelcomeRoute
import dev.justinsmith.interval.workout.WorkoutRoute
import dev.justinsmith.lift.nav.LiftDetail
import dev.justinsmith.lift.nav.LiftTab
import dev.justinsmith.lift.nav.Lifting
import dev.justinsmith.lift.nav.Programs
import dev.justinsmith.lift.nav.liftEntries
import kotlinx.serialization.modules.plus
import org.jetbrains.compose.resources.stringResource

/**
 * The screen for each [NavKey], and what each screen's actions do to the [backStack]. [twoPane] when a finished
 * workout opens beside History's log, [settingsBeside] when Settings is open beside the Timer. Every tab's gear
 * and the Timer's opens Settings; only over the Timer does it take the supporting pane.
 */
internal fun appEntries(
    backStack: NavBackStack<NavKey>,
    twoPane: Boolean,
    settingsBeside: Boolean,
    selectTab: (Tab) -> Unit,
    sharedScope: SharedTransitionScope,
    clock: WallClock,
    pendingHistoryDay: MutableState<Long?>,
): (NavKey) -> NavEntry<NavKey> = entryProvider {
    todayEntry(backStack, selectTab, clock, pendingHistoryDay)
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
            onOpenClass = { backStack.openDetail(ClassDetail(it)) },
            onPlan = { backStack.push(Plan) },
            onStartWorkout = { backStack.startWorkout(clock, it) },
            onShowTimer = { backStack.showTimer() },
            onStartSession = { backStack.push(Lifting) },
            onOpenSettings = { backStack.push(Settings) },
            openDay = pendingHistoryDay.value,
            onClearOpenDay = { pendingHistoryDay.value = null },
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
            onShowSetup = { backStack.push(WeekSetup) },
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
    entry<ClassDetail>(metadata = ListDetailSceneStrategy.detailPane(HISTORY_SCENE)) { key ->
        ClassDetailRoute(key.startedAtEpochMillis, onBack = { backStack.removeLastOrNull() }, inPane = twoPane)
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
    setupEntry(backStack, selectTab)
    entry<FlowEditor> { key ->
        FlowEditorRoute(key.id, key.copyOf, onDone = { backStack.removeLastOrNull() })
    }
}

/**
 * Set up, pushed over Today or Settings, or put in the welcome's place. Back and Skip leave it as it was found; a
 * week built shows on Today, whatever it was opened from; and its link to Lift's programs opens them over it, so
 * one followed there is the one its week keeps.
 */
private fun EntryProviderScope<NavKey>.setupEntry(backStack: NavBackStack<NavKey>, selectTab: (Tab) -> Unit) {
    // The welcome before it on a new install: Set up takes its place, so Back and Skip there land on Today.
    entry<Welcome> { WelcomeRoute(onSetUp = { backStack.replaceTop(WeekSetup) }) }
    entry<WeekSetup> {
        WeekSetupRoute(
            onBack = { backStack.removeLastOrNull() },
            onShowWeek = { selectTab(TodayTab) },
            onPrograms = { backStack.push(Programs) },
        )
    }
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
        MobilityRoute(
            page,
            MobilityActions(
                onStart = { backStack.startWorkout(clock, flow = it) },
                onBuild = { backStack.push(FlowEditor()) },
                onCopy = { backStack.push(FlowEditor(copyOf = it)) },
                onEdit = { backStack.push(FlowEditor(id = it)) },
            ),
        )
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
    pendingHistoryDay: MutableState<Long?>,
) {
    entry<TodayTab>(metadata = pageKind(PageKind.Tab)) {
        HomeRoute(
            onStartWorkout = { backStack.startWorkout(clock, it) },
            onStartSession = { backStack.push(Lifting) },
            onPlan = { backStack.push(Plan) },
            onPrograms = { backStack.push(Programs) },
            onOpenHistory = { day ->
                pendingHistoryDay.value = day
                selectTab(HistoryTab)
            },
            onOpenTimer = { backStack.showTimer() },
            onOpenSettings = { backStack.push(Settings) },
            onSetup = { backStack.push(WeekSetup) },
            onOpenMobility = { selectTab(MobilityTab) },
            // A class just synced opens in the Log, where its row is: Back from it lands there.
            onOpenClass = {
                selectTab(HistoryTab)
                backStack.openDetail(ClassDetail(it))
            },
        )
    }
}

/** The scene History's log and the workout beside it share, apart from Settings beside the Timer. */
private const val HISTORY_SCENE = "history"
