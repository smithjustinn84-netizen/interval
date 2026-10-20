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
import dev.justinsmith.bells.domain.ProgramId
import dev.justinsmith.bells.domain.builtInPrograms
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.program_following
import dev.justinsmith.bells.resources.program_stop
import dev.justinsmith.bells.resources.programs_title
import dev.justinsmith.bells.ui.programName
import dev.justinsmith.bells.ui.programSummary
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.currentKoinScope

/** The built-in programs and which one is followed. */
class ProgramsViewModel(private val repository: BellsRepository) : ViewModel() {
    val following: StateFlow<ProgramId?> = repository.following

    fun follow(id: ProgramId) {
        repository.follow(id)
    }

    fun stop() {
        repository.stopFollowing()
    }
}

@Composable
fun ProgramsRoute(onBack: () -> Unit) {
    val scope = currentKoinScope()
    val viewModel = viewModel { ProgramsViewModel(scope.get()) }
    val following by viewModel.following.collectAsStateWithLifecycle()
    ProgramsScreen(
        following = following,
        onFollow = {
            if (it != following) viewModel.follow(it)
            onBack()
        },
        onStop = {
            viewModel.stop()
            onBack()
        },
        onBack = onBack,
    )
}

/** Every built-in program; picking one follows it from its first day. */
@Composable
fun ProgramsScreen(
    following: ProgramId?,
    onFollow: (ProgramId) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = IntervalTheme.palette
    ScrollPage(stringResource(Res.string.programs_title), modifier, navigation = { BackAction(onBack) }) {
        ListCard {
            builtInPrograms.forEachIndexed { i, program ->
                if (i > 0) RowDivider()
                val current = program.id == following
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = Sizes.listRow)
                        .clickable(role = Role.RadioButton) { onFollow(program.id) }
                        .semantics { selected = current }
                        .padding(Spacing.space16),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(programName(program.id), style = IntervalTheme.type.label, color = palette.text)
                        Text(programSummary(program.id), style = IntervalTheme.type.meta, color = palette.textDim)
                    }
                    if (current) {
                        Icon(
                            IntervalIcons.Check,
                            stringResource(Res.string.program_following),
                            tint = palette.accent,
                            modifier = Modifier.size(Sizes.icon),
                        )
                    }
                }
            }
        }
        if (following != null) {
            QuietTextButton(stringResource(Res.string.program_stop), onStop, Modifier.padding(top = Spacing.space12))
        }
    }
}
