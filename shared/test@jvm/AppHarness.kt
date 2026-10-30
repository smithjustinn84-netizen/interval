@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.interval.data.IntervalRepository
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.testKoin
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import org.koin.core.KoinApplication
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.TestTimeSource

/**
 * What a whole-app test holds: the container, the fake storage of both sides under it, the wall clock, the
 * workout's elapsed time, and the system back gesture.
 */
class AppFixture(
    val koin: KoinApplication,
    val storage: FakeStorage,
    val lift: FakeBellsStorage,
    /** The wall clock in epoch millis: what History dates a workout by and Lift times a session with. */
    val clock: AtomicLong,
    /** The time a running workout counts, which moves only through [advance]. */
    private val elapsed: TestTimeSource,
    /** The system back input, for a test that drives a gesture step by step. */
    val backInput: DirectNavigationEventInput,
) {
    val repository: IntervalRepository
        get() = koin.koin.get()

    /** The system back gesture, completed. */
    fun back() {
        backInput.backStarted(NavigationEvent())
        backInput.backCompleted()
    }

    /** Moves a running workout on by [duration]; its next tick catches the whole step up. */
    fun advance(duration: Duration) {
        elapsed += duration
    }
}

/**
 * The whole app, `AppContent` with real ViewModels, repository and navigation, over fake platform services
 * and the given storage, in a [window]. The wall clock starts at [now]; a workout's time stands still until
 * the test advances it.
 */
fun appTest(
    window: Window = Window.Phone,
    storage: FakeStorage = FakeStorage(),
    lift: FakeBellsStorage = FakeBellsStorage(),
    now: Long = 0L,
    block: suspend DesktopComposeUiTest.(AppFixture) -> Unit,
) = runUiTest(window) {
    val clock = AtomicLong(now)
    val elapsed = TestTimeSource()
    val koin = testKoin(storage = storage, lift = lift, clock = WallClock { clock.get() }, timeSource = elapsed)
    val owner = object : NavigationEventDispatcherOwner {
        override val navigationEventDispatcher = NavigationEventDispatcher()
    }
    val input = DirectNavigationEventInput()
    owner.navigationEventDispatcher.addInput(input)
    try {
        setContent {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) { AppContent(koin) }
        }
        block(AppFixture(koin, storage, lift, clock, elapsed, input))
    } finally {
        koin.close()
    }
}

/** A navigation bar or rail item; "History" alone would also match the History screen's title. */
fun DesktopComposeUiTest.tab(label: String): SemanticsNodeInteraction = onNode(hasText(label) and hasClickAction())
