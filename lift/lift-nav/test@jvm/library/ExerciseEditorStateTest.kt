@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.lift.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.justinsmith.interval.ui.StateRestoration
import dev.justinsmith.interval.ui.runUiTest
import dev.justinsmith.lift.domain.Measure
import dev.justinsmith.lift.fakes.liftTestKoin
import org.koin.compose.KoinIsolatedContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame

/**
 * The editor's draft as a nav entry holds it: through the save and restore of a process death, and left
 * behind by a sheet opened again, which starts afresh.
 */
class ExerciseEditorStateTest {
    private data object Page

    private var open by mutableStateOf(true)
    private var editor: ExerciseEditorViewModel? = null

    private fun editorTest(block: DesktopComposeUiTest.(StateRestoration) -> Unit) = runUiTest {
        val koin = liftTestKoin()
        try {
            val restoration = StateRestoration(this)
            restoration.setContent {
                KoinIsolatedContext(koin) {
                    NavDisplay(
                        backStack = listOf(Page),
                        onBack = {},
                        entryDecorators = listOf(
                            rememberSaveableStateHolderNavEntryDecorator(),
                            rememberViewModelStoreNavEntryDecorator(),
                        ),
                        entryProvider = entryProvider {
                            entry<Page> {
                                editor =
                                    if (open) rememberExerciseEditor(null, "Turkish sit-up") else null
                            }
                        },
                    )
                }
            }
            block(restoration)
        } finally {
            koin.close()
        }
    }

    @Test
    fun `the draft survives the process going`() = editorTest { restoration ->
        val before = assertNotNull(editor)
        before.change { it.copy(name = "Turkish get-up", measure = Measure.Time) }
        restoration.emulateSaveAndRestore()
        val after = assertNotNull(editor)
        assertNotSame(before, after, "the editor comes back from saved state, not memory")
        assertEquals("Turkish get-up", after.state.value.draft.name)
        assertEquals(Measure.Time, after.state.value.draft.measure)
    }

    @Test
    fun `a sheet opened again starts from the name it was opened with`() = editorTest {
        assertNotNull(editor).change { it.copy(name = "Turkish get-up") }
        open = false
        waitForIdle()
        open = true
        waitForIdle()
        assertEquals("Turkish sit-up", assertNotNull(editor).state.value.draft.name)
    }
}
