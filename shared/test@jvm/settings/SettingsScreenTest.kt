@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.justinsmith.interval.data.AppSettings
import dev.justinsmith.interval.data.BodySettings
import dev.justinsmith.interval.data.CueSettings
import dev.justinsmith.interval.data.ThemeMode
import dev.justinsmith.interval.timer.Effort
import dev.justinsmith.interval.timer.WeightUnit
import dev.justinsmith.interval.ui.Window
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.interval.ui.setThemedContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val defaults = SettingsUiState(
    cues = CueSettings(vibrate = false),
    prepSeconds = 10,
    app = AppSettings(theme = ThemeMode.Dark),
)

/** Calorie estimates turned off: no weight. */
private val caloriesOff = defaults.copy(app = defaults.app.copy(body = BodySettings(weightKg = null)))

class SettingsScreenTest {
    private val calls = mutableListOf<Any>()

    @Composable
    private fun Screen(state: SettingsUiState = defaults, inPane: Boolean = false) {
        SettingsScreen(
            state = state,
            onBack = { calls += "back" },
            onToggle = { calls += it },
            onStepPrep = { calls += "step $it" },
            onSetPrep = { calls += "set $it" },
            onToggleKeepScreenOn = { calls += "keep screen on" },
            onSetTheme = { calls += it },
            onShowAbout = { calls += "about" },
            inPane = inPane,
            onSetWeight = { calls += "weight $it" },
            onSetWeightUnit = { calls += it },
            onClearWeight = { calls += "clear weight" },
            onSetEffort = { calls += it },
        )
    }

    @Test
    fun `full screen on a tablet the groups sit side by side`() = runUiTest(Window.TabletPortrait) {
        setThemedContent { Screen() }
        val cues = onNodeWithText("Cues").fetchSemanticsNode().boundsInRoot
        val workout = onNodeWithText("Workout").fetchSemanticsNode().boundsInRoot
        assertTrue(workout.left > cues.right, "Workout beside Cues: $cues, $workout")
    }

    @Test
    fun `beside the timer the groups stay in one column`() = runUiTest(Window.TabletPortrait) {
        setThemedContent { Screen(inPane = true) }
        val cues = onNodeWithText("Cues").fetchSemanticsNode().boundsInRoot
        val workout = onNodeWithText("Workout").fetchSemanticsNode().boundsInRoot
        assertEquals(cues.left, workout.left)
        assertTrue(workout.top > cues.bottom, "Workout under Cues: $cues, $workout")
    }

    @Test
    fun `the about row opens about`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("About Interval").performScrollTo().performClick()
        assertEquals(listOf<Any>("about"), calls)
    }

    @Test
    fun `switches show their state and report toggles`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Sound cues").assertIsOn()
        onNodeWithText("Vibrate").assertIsOff()
        onNodeWithText("Sound cues").performClick()
        onNodeWithText("Voice").performClick()
        onNodeWithText("Halfway alert").performClick()
        onNodeWithText("Vibrate").performClick()
        onNodeWithText("Keep screen on").performClick()
        assertEquals(
            listOf<Any>(CueToggle.Sound, CueToggle.Voice, CueToggle.Halfway, CueToggle.Vibrate, "keep screen on"),
            calls,
        )
    }

    @Test
    fun `halfway dims while sound and voice are both off`() = runUiTest {
        var cues by mutableStateOf(CueSettings(sound = false, voice = false))
        setThemedContent { Screen(defaults.copy(cues = cues)) }
        onNodeWithText("Halfway alert").assertIsNotEnabled().assertIsOff()
        onNodeWithText("Turn on sound or voice to hear it").assertExists()
        onNodeWithText("Halfway alert").performClick()
        assertEquals(emptyList(), calls)
        cues = cues.copy(voice = true)
        onNodeWithText("Halfway alert").assertIsEnabled().assertIsOn()
    }

    @Test
    fun `a line warns when every cue is off`() = runUiTest {
        val warning = "Workouts will run without any cues"
        var cues by mutableStateOf(CueSettings(sound = false, voice = false, vibrate = false))
        setThemedContent { Screen(defaults.copy(cues = cues)) }
        onNodeWithText(warning).assertExists()
        cues = cues.copy(vibrate = true)
        onAllNodesWithText(warning).assertCountEquals(0)
    }

    @Test
    fun `the theme picker shows and changes the theme`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Dark").assertIsSelected()
        onNodeWithText("Light").performClick()
        assertEquals(listOf<Any>(ThemeMode.Light), calls)
    }

    @Test
    fun `get ready opens its sheet`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Countdown before the first round").performClick()
        onNodeWithContentDescription("More get ready time").performClick()
        onNodeWithText("0:45").performClick()
        assertEquals(listOf<Any>("step 1", "set 45"), calls)
    }

    @Test
    fun `full screen it goes back, beside the timer it closes`() = runUiTest {
        var inPane by mutableStateOf(false)
        setThemedContent { Screen(inPane = inPane) }
        onNodeWithContentDescription("Back").performClick()
        onNodeWithContentDescription("Close settings").assertDoesNotExist()

        inPane = true
        onNodeWithContentDescription("Back").assertDoesNotExist()
        onNodeWithContentDescription("Close settings").performClick()
        assertEquals(listOf<Any>("back", "back"), calls)
    }

    @Test
    fun `the weight starts at the default`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("${WeightUnit.Kg.default} kg").performScrollTo().assertExists()
    }

    @Test
    fun `with calories off the row asks for a weight and Done sets the default`() = runUiTest {
        setThemedContent { Screen(caloriesOff) }
        onNodeWithText("Off. Set it to see calorie estimates").performScrollTo().performClick()
        onNodeWithText("Turn off calorie estimates").assertDoesNotExist()
        onNodeWithText("Done").performClick()
        assertEquals(listOf<Any>("weight ${WeightUnit.Kg.default}"), calls)
    }

    @Test
    fun `the weight sheet steps switches unit and clears`() = runUiTest {
        val state = defaults.copy(app = defaults.app.copy(body = BodySettings(weightKg = 80.0)))
        setThemedContent { Screen(state) }
        onNodeWithText("80 kg").performScrollTo().performClick()
        onNodeWithContentDescription("More weight").performClick()
        onNodeWithText("lb").performClick()
        onNodeWithText("Turn off calorie estimates").performClick()
        assertEquals(listOf<Any>("weight 81", WeightUnit.Lb, "clear weight"), calls)
    }

    @Test
    fun `the effort picker shows and changes the effort`() = runUiTest {
        setThemedContent { Screen() }
        onNodeWithText("Hard").performScrollTo().assertIsSelected()
        onNodeWithText("Easy").performClick()
        assertEquals(listOf<Any>(Effort.Easy), calls)
    }

    @Test
    fun `setting a first weight does not move the sheet's buttons`() = runUiTest {
        var state by mutableStateOf(caloriesOff)
        setThemedContent {
            SettingsScreen(
                state = state,
                onBack = {},
                onToggle = {},
                onStepPrep = {},
                onSetPrep = {},
                onToggleKeepScreenOn = {},
                onSetTheme = {},
                onShowAbout = {},
                onSetWeight = { kg ->
                    calls += "weight $kg"
                    state = state.copy(app = state.app.copy(body = BodySettings(weightKg = kg.toDouble())))
                },
            )
        }
        onNodeWithText("Off. Set it to see calorie estimates").performScrollTo().performClick()
        onNodeWithContentDescription("More weight").performClick()
        onNodeWithText("Turn off calorie estimates").assertDoesNotExist()
        onNodeWithText("Done").performClick()
        assertEquals(listOf<Any>("weight 71"), calls)
    }
}
