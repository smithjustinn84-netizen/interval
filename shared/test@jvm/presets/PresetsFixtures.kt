@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.presets

import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset
import dev.justinsmith.interval.timer.builtInPresets
import dev.justinsmith.interval.timer.formatClock

// What PresetsScreenTest and PresetEditorTest both drive the Presets tab with.

internal val morning = Preset("Morning", IntervalConfig(workSeconds = 25, restSeconds = 10, rounds = 8), id = 7)
internal val current = IntervalConfig(prepSeconds = 10, workSeconds = 40, restSeconds = 20, rounds = 6)

internal fun row(preset: Preset, selected: Boolean = false) =
    PresetRowItem(preset, formatClock(preset.config.totalSeconds), selected)

internal fun state(
    custom: List<Preset> = listOf(morning),
    canSaveCurrent: Boolean = true,
    selected: Preset? = null,
) = PresetsUiState(
    custom = custom.map { row(it, selected = it == selected) },
    builtIn = builtInPresets.map { row(it, selected = it == selected) },
    canSaveCurrent = canSaveCurrent,
    current = current,
)

// The list stays in the tree under a sheet, and a sheet under the delete dialog, each drawn before
// what covers it; a sheet is a dialog to semantics, as the dialog is.

/** The editor's Save, not the unsaved card's Save pill under it. */
internal fun DesktopComposeUiTest.saveInEditor() = onAllNodesWithText("Save").onLast().performClick()

/** A node in an open sheet or dialog, not one of the list's under it. */
internal fun DesktopComposeUiTest.inSheet(text: String) = onNode(hasText(text) and hasAnyAncestor(isDialog()))
