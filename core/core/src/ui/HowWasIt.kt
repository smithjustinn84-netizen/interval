package dev.justinsmith.interval.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.flow.drop

/**
 * "How was it?" at a finish, the same on both sides: how hard it felt, one of [efforts] (none until picked), and a
 * one-line note. Each change goes out as it is made ([onEffortChange], [onNoteChange]), so leaving loses nothing.
 * The words are the caller's: [title], each effort's [effortLabel], and the note's [notePlaceholder].
 */
@Composable
fun <T> HowWasIt(
    title: String,
    efforts: List<T>,
    effort: T?,
    effortLabel: @Composable (T) -> String,
    onEffortChange: (T) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    notePlaceholder: String,
    noteMaxLength: Int,
    modifier: Modifier = Modifier,
) {
    val noteState = rememberTextFieldState(note)
    val noteChange by rememberUpdatedState(onNoteChange)
    LaunchedEffect(noteState) {
        // The first value is the note handed in: only what is typed goes back.
        snapshotFlow { noteState.text.toString() }.drop(1).collect { noteChange(it) }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
        Text(
            title,
            style = IntervalTheme.type.label,
            color = IntervalTheme.palette.textDim,
            modifier = Modifier.semantics { heading() },
        )
        SegmentedPicker(efforts, effort, effortLabel, onEffortChange, check = false, maxLines = 2)
        NameField(noteState, placeholder = notePlaceholder, label = notePlaceholder, maxLength = noteMaxLength)
    }
}
