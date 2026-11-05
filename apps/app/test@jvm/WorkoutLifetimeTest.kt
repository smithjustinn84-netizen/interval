@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventDispatcherOwner
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import dev.justinsmith.interval.fakes.RecordingBackgroundWorkout
import dev.justinsmith.interval.fakes.fakePlatformModule
import dev.justinsmith.interval.fakes.testKoin
import dev.justinsmith.interval.ui.StateRestoration
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.workout.WorkoutSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.TestTimeSource

/** The running workout belongs to the process for as long as its page is on the back stack, and no longer. */
class WorkoutLifetimeTest {
    @Test
    fun `leaving the workout's page ends the workout`() = appTest { app ->
        onNodeWithText("Start workout").performClick()
        onNodeWithContentDescription("End workout").assertExists()
        val session = app.koin.koin.get<WorkoutSession>()
        assertNotNull(session.state.value)

        onNodeWithContentDescription("End workout").performClick()
        onNodeWithText("End workout").performClick()
        onNodeWithText("Start workout").assertExists()

        waitForIdle()
        assertNull(session.state.value)
        assertNull(app.repository.settings.workoutProgress.value)
    }

    @Test
    fun `the workout runs on while the app's screen is destroyed and built again`() = runUiTest(Window.Phone) {
        val background = RecordingBackgroundWorkout()
        val koin = testKoin(platformModule = fakePlatformModule(background = background), timeSource = TestTimeSource())
        val owner = object : NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = NavigationEventDispatcher()
        }
        try {
            val restoration = StateRestoration(this)
            restoration.setContent {
                CompositionLocalProvider(LocalNavigationEventDispatcherOwner provides owner) { AppContent(koin) }
            }
            onNodeWithText("Start workout").performClick()
            onNodeWithContentDescription("End workout").assertExists()
            val session = koin.koin.get<WorkoutSession>()
            val started = assertNotNull(session.state.value)

            // As Android destroys the activity in the background and builds it again from what it saved.
            restoration.emulateSaveAndRestore()

            onNodeWithContentDescription("End workout").assertExists()
            assertEquals(started.id, session.state.value?.id)
            assertEquals(started.startedAt, session.state.value?.startedAt)
            assertEquals(1, background.starts)
            assertEquals(0, background.stops)
        } finally {
            koin.close()
        }
    }
}
