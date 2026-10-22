@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.bells.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.navigationevent.DirectNavigationEventInput
import androidx.navigationevent.NavigationEvent
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dev.justinsmith.bells.fakes.FakeBellsStorage
import dev.justinsmith.bells.fakes.FakeIntervalActivity
import dev.justinsmith.bells.fakes.RecordingBeeper
import dev.justinsmith.bells.fakes.bellsTestKoin
import dev.justinsmith.bells.fakes.fakeBellsPlatformModule
import dev.justinsmith.bells.gear.GearRoute
import dev.justinsmith.bells.library.ExerciseEditorRoute
import dev.justinsmith.bells.library.ExerciseLibraryRoute
import dev.justinsmith.bells.programs.ProgramEditorRoute
import dev.justinsmith.bells.programs.ProgramsRoute
import dev.justinsmith.bells.session.SessionRoute
import dev.justinsmith.bells.today.TodayRoute
import dev.justinsmith.interval.ui.IntervalTheme
import org.koin.compose.KoinIsolatedContext
import org.koin.core.KoinApplication
import java.util.concurrent.atomic.AtomicLong

/** A phone's window, as Interval's harness sizes it, at density 1 so pixels are dp. */
private const val PHONE_WIDTH = 411
private const val PHONE_HEIGHT = 891

/** Runs [block] in a phone-sized desktop window. */
fun runUiTest(block: suspend DesktopComposeUiTest.() -> Unit) =
    runDesktopComposeUiTest(width = PHONE_WIDTH, height = PHONE_HEIGHT, block = block)

/** Sets [content] inside the dark theme. */
fun DesktopComposeUiTest.setBellsContent(content: @Composable () -> Unit) {
    setContent { IntervalTheme(darkTheme = true, content = content) }
}

/** How long a view model on the main thread, ticking in real time, may take to show what the test did. */
private const val STATE_TIMEOUT_MILLIS = 5_000L

/**
 * Waits for [text] to show. The real view models emit on `Dispatchers.Main` (Swing's thread), which the
 * test's idling doesn't track, so whole-app tests wait for what the state brings rather than assert at once.
 */
fun DesktopComposeUiTest.waitForText(text: String) =
    waitUntil(timeoutMillis = STATE_TIMEOUT_MILLIS) { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

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

/** Where [BellsHost] can be: the app's own keys live in `shared`, which Bells can't see. */
private sealed interface Page {
    data object Today : Page

    data object Session : Page

    data object Programs : Page

    data object Bells : Page

    data object Exercises : Page

    data class ExerciseEditor(val uid: String?, val name: String) : Page

    data class Editor(val uid: String?) : Page
}

/** Bells' pages as the app wires them, on a back stack of their own, so tests drive them end to end. */
@Composable
private fun BellsHost(koin: KoinApplication, back: NavigationEventDispatcherOwner) {
    KoinIsolatedContext(context = koin) {
        val backStack: SnapshotStateList<Page> = remember { listOf(Page.Today).toMutableStateList() }
        val pop: () -> Unit = { backStack.removeLastOrNull() }
        IntervalTheme(darkTheme = true) {
            CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides back) {
                NavDisplay(
                    backStack = backStack,
                    onBack = { if (backStack.size > 1) pop() },
                    entryDecorators = listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                    entryProvider = { page ->
                        NavEntry(page) {
                            when (page) {
                                Page.Today -> TodayRoute(
                                    onStartSession = { backStack.add(Page.Session) },
                                    onChooseProgram = { backStack.add(Page.Programs) },
                                    onOpenBells = { backStack.add(Page.Bells) },
                                    onOpenExercises = { backStack.add(Page.Exercises) },
                                )

                                Page.Session -> SessionRoute(
                                    onExit = pop,
                                    onNewExercise = { backStack.add(Page.ExerciseEditor(null, it)) },
                                )

                                Page.Programs -> ProgramsRoute(
                                    onNew = { backStack.add(Page.Editor(null)) },
                                    onEdit = { backStack.add(Page.Editor(it)) },
                                    onBack = pop,
                                )

                                is Page.Editor -> ProgramEditorRoute(page.uid, onBack = pop)

                                Page.Bells -> GearRoute(onBack = pop)

                                Page.Exercises -> ExerciseLibraryRoute(
                                    onBack = pop,
                                    onNew = { backStack.add(Page.ExerciseEditor(null, it)) },
                                    onEdit = { backStack.add(Page.ExerciseEditor(it, "")) },
                                )

                                is Page.ExerciseEditor -> ExerciseEditorRoute(page.uid, page.name, onBack = pop)
                            }
                        }
                    },
                )
            }
        }
    }
}

/** Bells' pages, real ViewModels and repository, over fake storage and platform services. */
fun bellsAppTest(
    storage: FakeBellsStorage = FakeBellsStorage(),
    intervals: FakeIntervalActivity = FakeIntervalActivity(),
    block: suspend DesktopComposeUiTest.(BellsAppFixture) -> Unit,
) = runUiTest {
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
