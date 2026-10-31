@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.savedstate.serialization.SavedStateConfiguration
import dev.justinsmith.interval.fakes.RecordingSharer
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NoticeHost
import dev.justinsmith.interval.ui.UI_TEST_TIMEOUT
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.rememberNotices
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.lift.fakes.FakeIntervalActivity
import dev.justinsmith.lift.fakes.FakeLiftStorage
import dev.justinsmith.lift.fakes.RecordingBeeper
import dev.justinsmith.lift.fakes.fakeLiftPlatformModule
import dev.justinsmith.lift.fakes.liftTestKoin
import dev.justinsmith.lift.nav.LiftTab
import dev.justinsmith.lift.nav.liftEntries
import dev.justinsmith.lift.nav.liftRoutes
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

/**
 * What a whole-app test drives: the fake storage under the app, its clock, the beeps it played, what it
 * offered to share, and [back], the system back gesture.
 */
class LiftAppFixture(
    val storage: FakeLiftStorage,
    val clock: AtomicLong,
    val beeper: RecordingBeeper,
    /** What the pages offered to share. */
    val sharer: RecordingSharer,
    private val backInput: DirectNavigationEventInput,
) {
    fun back() {
        backInput.backStarted(NavigationEvent())
        backInput.backCompleted()
    }
}

/** Lift's keys alone: the app's own live in `app`, which Lift can't see. */
private val liftNavConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule { polymorphic(NavKey::class) { liftRoutes() } }
}

/** Lift's pages as the app wires them ([liftEntries]), on a back stack of their own, so tests drive them end to end. */
@Composable
private fun LiftHost(koin: KoinApplication, back: NavigationEventDispatcherOwner) {
    KoinIsolatedContext(context = koin) {
        val backStack = rememberNavBackStack(liftNavConfiguration, LiftTab)
        val notices = rememberNotices()
        IntervalTheme(darkTheme = true) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides back, LocalNotices provides notices) {
                Box {
                    NavDisplay(
                        backStack = backStack,
                        onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = entryProvider {
                            liftEntries(backStack, twoPane = false, historyScene = "history")
                        },
                    )
                    NoticeHost(notices, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

/** Lift's pages, real ViewModels and repository, over fake storage and platform services. */
fun liftAppTest(
    storage: FakeLiftStorage = FakeLiftStorage(),
    intervals: FakeIntervalActivity = FakeIntervalActivity(),
    window: Window = Window.Phone,
    timeout: Duration = UI_TEST_TIMEOUT,
    block: suspend DesktopComposeUiTest.(LiftAppFixture) -> Unit,
) = runUiTest(window, timeout) {
    val clock = AtomicLong(0L)
    val beeper = RecordingBeeper()
    val sharer = RecordingSharer()
    val koin = liftTestKoin(
        storage,
        fakeLiftPlatformModule(beeper = beeper),
        now = clock::get,
        intervals = intervals,
        sharer = sharer,
    )
    val owner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
    }
    val input = DirectNavigationEventInput()
    owner.navigationEventDispatcher.addInput(input)
    try {
        setContent { LiftHost(koin, owner) }
        block(LiftAppFixture(storage, clock, beeper, sharer, input))
    } finally {
        koin.close()
    }
}
