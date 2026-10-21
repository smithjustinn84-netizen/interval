package dev.justinsmith.bells.programs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.builtInPrograms
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.program_following
import dev.justinsmith.bells.resources.program_stop
import dev.justinsmith.bells.resources.programs_classics
import dev.justinsmith.bells.resources.programs_dan_john
import dev.justinsmith.bells.resources.programs_edit_cd
import dev.justinsmith.bells.resources.programs_new
import dev.justinsmith.bells.resources.programs_new_hint
import dev.justinsmith.bells.resources.programs_title
import dev.justinsmith.bells.resources.programs_yours
import dev.justinsmith.bells.ui.programName
import dev.justinsmith.bells.ui.programSummary
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** The built-in programs, the ones the user made, and which one is followed. */
class ProgramsViewModel(private val repository: BellsRepository) : ViewModel() {
    val following: StateFlow<Program?> = repository.following
    val custom: StateFlow<List<Program>> = repository.customPrograms

    fun follow(key: ProgramKey) {
        repository.follow(key)
    }

    fun stop() {
        repository.stopFollowing()
    }
}

/** What the Programs page does: follow a program or stop, make one or change one made, and go back. */
data class ProgramsActions(
    val onFollow: (ProgramKey) -> Unit = {},
    val onStop: () -> Unit = {},
    val onNew: () -> Unit = {},
    val onEdit: (uid: String) -> Unit = {},
    val onBack: () -> Unit = {},
)

@Composable
fun ProgramsRoute(onNew: () -> Unit, onEdit: (uid: String) -> Unit, onBack: () -> Unit) {
    val scope = currentKoinScope()
    val viewModel = viewModel { ProgramsViewModel(scope.get()) }
    val following by viewModel.following.collectAsStateWithLifecycle()
    val custom by viewModel.custom.collectAsStateWithLifecycle()
    ProgramsScreen(
        following = following?.key,
        custom = custom,
        actions = ProgramsActions(
            onFollow = {
                if (it != following?.key) viewModel.follow(it)
                onBack()
            },
            onStop = {
                viewModel.stop()
                onBack()
            },
            onNew = onNew,
            onEdit = onEdit,
            onBack = onBack,
        ),
    )
}

/** The built-in programs, then the user's own; picking one follows it from its first day. */
@Composable
fun ProgramsScreen(
    following: ProgramKey?,
    custom: List<Program>,
    actions: ProgramsActions,
    modifier: Modifier = Modifier,
) {
    val (danJohn, classics) = builtInPrograms.partition { (it.key as? ProgramKey.BuiltIn)?.id?.danJohn == true }
    ScrollPage(stringResource(Res.string.programs_title), modifier, navigation = { BackAction(actions.onBack) }) {
        ProgramGroup(stringResource(Res.string.programs_classics), classics, following, actions)
        ProgramGroup(stringResource(Res.string.programs_dan_john), danJohn, following, actions)
        GroupLabel(stringResource(Res.string.programs_yours))
        ListCard {
            custom.forEach { program ->
                ProgramRow(program, program.key == following, actions)
                RowDivider()
            }
            LinkRow(
                stringResource(Res.string.programs_new),
                stringResource(Res.string.programs_new_hint),
                onClick = actions.onNew,
            )
        }
        if (following != null) {
            QuietTextButton(
                stringResource(Res.string.program_stop),
                actions.onStop,
                Modifier.padding(top = Spacing.space12),
            )
        }
    }
}

@Composable
private fun ProgramGroup(title: String, programs: List<Program>, following: ProgramKey?, actions: ProgramsActions) {
    GroupLabel(title)
    ListCard {
        programs.forEachIndexed { i, program ->
            if (i > 0) RowDivider()
            ProgramRow(program, program.key == following, actions)
        }
    }
}

/** A program to follow on a tap, checked while it is; one the user made ends in a way to change it. */
@Composable
private fun ProgramRow(program: Program, current: Boolean, actions: ProgramsActions) {
    val palette = IntervalTheme.palette
    val name = programName(program)
    val key = program.key
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .clickable(role = Role.RadioButton) { actions.onFollow(program.key) }
            .semantics { selected = current }
            // The edit action brings its own room to the end.
            .padding(
                start = Spacing.space16,
                top = Spacing.space16,
                bottom = Spacing.space16,
                end = if (key is ProgramKey.Custom) Spacing.space4 else Spacing.space16,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = IntervalTheme.type.label, color = palette.text)
            Text(programSummary(program), style = IntervalTheme.type.meta, color = palette.textDim)
        }
        if (current) {
            Icon(
                IntervalIcons.Check,
                stringResource(Res.string.program_following),
                tint = palette.accent,
                modifier = Modifier.size(Sizes.icon),
            )
        }
        if (key is ProgramKey.Custom) {
            IconAction(IntervalIcons.Describe, stringResource(Res.string.programs_edit_cd, name), {
                actions.onEdit(key.uid)
            })
        }
    }
}
