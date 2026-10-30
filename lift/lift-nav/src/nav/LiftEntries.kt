@file:OptIn(ExperimentalMaterial3AdaptiveApi::class)

package dev.justinsmith.lift.nav

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import dev.justinsmith.interval.nav.push
import dev.justinsmith.interval.ui.PageKind
import dev.justinsmith.interval.ui.pageKind
import dev.justinsmith.lift.gear.GearRoute
import dev.justinsmith.lift.history.LiftDetailRoute
import dev.justinsmith.lift.library.ExerciseLibraryRoute
import dev.justinsmith.lift.programs.ProgramEditorRoute
import dev.justinsmith.lift.programs.ProgramProgressRoute
import dev.justinsmith.lift.programs.ProgramsRoute
import dev.justinsmith.lift.session.SessionRoute
import dev.justinsmith.lift.today.TodayRoute

/**
 * The screen for each Lift key, and what each screen's actions do to the [backStack]. A finished session
 * opened from History sits in [historyScene]'s detail pane, beside the log when [twoPane].
 */
fun EntryProviderScope<NavKey>.liftEntries(backStack: NavBackStack<NavKey>, twoPane: Boolean, historyScene: String) {
    entry<LiftTab>(metadata = pageKind(PageKind.Tab)) {
        TodayRoute(
            onStartSession = { backStack.push(Lifting) },
            onChooseProgram = { backStack.push(Programs) },
            onOpenBells = { backStack.push(OwnedBells) },
            onOpenSession = { backStack.push(LiftDetail(it)) },
            onOpenExercises = { backStack.push(ExerciseLibrary) },
            onOpenProgram = { backStack.push(ProgramProgress) },
        )
    }
    entry<Lifting>(metadata = pageKind(PageKind.Mode)) {
        SessionRoute(onExit = { backStack.removeLastOrNull() })
    }
    entry<Programs> {
        ProgramsRoute(
            onNew = { backStack.push(ProgramEditor()) },
            onEdit = { backStack.push(ProgramEditor(it)) },
            onOpenProgress = { backStack.push(ProgramProgress) },
            onBack = { backStack.removeLastOrNull() },
        )
    }
    entry<ProgramEditor> { key -> ProgramEditorRoute(uid = key.uid, onBack = { backStack.removeLastOrNull() }) }
    entry<ProgramProgress> {
        ProgramProgressRoute(
            onOpenSession = { backStack.push(LiftDetail(it)) },
            // In place of the page, so Back from Programs lands on Lift, whichever page opened it.
            onPickAnother = {
                backStack.removeLastOrNull()
                backStack.push(Programs)
            },
            onBack = { backStack.removeLastOrNull() },
        )
    }
    entry<OwnedBells> { GearRoute(onBack = { backStack.removeLastOrNull() }) }
    entry<ExerciseLibrary> {
        ExerciseLibraryRoute(
            onBack = { backStack.removeLastOrNull() },
            onOpenSession = { backStack.push(LiftDetail(it)) },
        )
    }
    entry<LiftDetail>(
        metadata = { if (it.fromHistory) ListDetailSceneStrategy.detailPane(historyScene) else emptyMap() },
    ) { key ->
        LiftDetailRoute(
            uid = key.uid,
            onBack = { backStack.removeLastOrNull() },
            inPane = twoPane && key.fromHistory,
        )
    }
}
