@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.ui

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
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.fakes.FakeIntervalActivity
import dev.justinsmith.bells.fakes.RecordingBeeper
import dev.justinsmith.bells.fakes.bellsTestKoin
import dev.justinsmith.bells.fakes.fakeBellsPlatformModule
import dev.justinsmith.bells.nav.LiftTab
import dev.justinsmith.bells.nav.liftEntries
import dev.justinsmith.bells.nav.liftRoutes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.NoticeHost
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.rememberNotices
import dev.justinsmith.interval.ui.runUiTest
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import java.util.concurrent.atomic.AtomicLong

/** Sets [content] inside the dark theme. */
fun DesktopComposeUiTest.setBellsContent(content: @Composable () -> Unit) {
    setContent { IntervalTheme(darkTheme = true, content = content) }
}

/**
 * What a whole-app test drives: the fake storage under the app, its clock, the beeps it played, and [back],
 * the system back gesture.
 */
class BellsAppFixture(
    val storage: FakeBellsStorage,
    val clock: AtomicLong,
    val beeper: RecordingBeeper,
    private val backInput: DirectNavigationEventInput,
) {
    fun back() {
        backInput.backStarted(NavigationEvent())
        backInput.backCompleted()
    }
}

/** Lift's keys alone: the app's own live in `shared`, which Bells can't see. */
private val liftNavConfiguration = SavedStateConfiguration {
    serializersModule = SerializersModule { polymorphic(NavKey::class) { liftRoutes() } }
}

/** Bells' pages as the app wires them ([liftEntries]), on a back stack of their own, so tests drive them end to end. */
@Composable
private fun BellsHost(koin: KoinApplication, back: NavigationEventDispatcherOwner) {
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

/** Bells' pages, real ViewModels and repository, over fake storage and platform services. */
fun bellsAppTest(
    storage: FakeBellsStorage = FakeBellsStorage(),
    intervals: FakeIntervalActivity = FakeIntervalActivity(),
    window: Window = Window.Phone,
    block: suspend DesktopComposeUiTest.(BellsAppFixture) -> Unit,
) = runUiTest(window) {
    val clock = AtomicLong(0L)
    val beeper = RecordingBeeper()
    val koin = bellsTestKoin(storage, fakeBellsPlatformModule(beeper = beeper), now = clock::get, intervals = intervals)
    val owner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
    }
    val input = DirectNavigationEventInput()
    owner.navigationEventDispatcher.addInput(input)
    try {
        setContent { BellsHost(koin, owner) }
        block(BellsAppFixture(storage, clock, beeper, input))
    } finally {
        koin.close()
    }
}
