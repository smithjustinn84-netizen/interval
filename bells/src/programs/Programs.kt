package dev.justinsmith.bells.programs

import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.justinsmith.bells.data.BellsRepository
import dev.justinsmith.bells.domain.Program
import dev.justinsmith.bells.domain.ProgramDay
import dev.justinsmith.bells.domain.ProgramFamily
import dev.justinsmith.bells.domain.ProgramKey
import dev.justinsmith.bells.domain.builtInPrograms
import dev.justinsmith.bells.resources.Res
import dev.justinsmith.bells.resources.program_follow
import dev.justinsmith.bells.resources.program_following
import dev.justinsmith.bells.resources.program_keep
import dev.justinsmith.bells.resources.program_preview_day
import dev.justinsmith.bells.resources.program_replaces
import dev.justinsmith.bells.resources.program_stop
import dev.justinsmith.bells.resources.program_switch
import dev.justinsmith.bells.resources.program_switch_text
import dev.justinsmith.bells.resources.program_switch_title
import dev.justinsmith.bells.resources.programs_classics
import dev.justinsmith.bells.resources.programs_dan_john
import dev.justinsmith.bells.resources.programs_edit_cd
import dev.justinsmith.bells.resources.programs_grappling
import dev.justinsmith.bells.resources.programs_new
import dev.justinsmith.bells.resources.programs_new_hint
import dev.justinsmith.bells.resources.programs_title
import dev.justinsmith.bells.resources.programs_yours
import dev.justinsmith.bells.ui.blockLine
import dev.justinsmith.bells.ui.liftName
import dev.justinsmith.bells.ui.programName
import dev.justinsmith.bells.ui.programSummary
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.ConfirmDialog
import dev.justinsmith.interval.ui.Corners
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IconAction
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalSheet
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

/** The built-in programs, the ones the user made, which one is followed and its next day. */
class ProgramsViewModel(private val repository: BellsRepository) : ViewModel() {
    val following: StateFlow<Program?> = repository.following
    val programDay: StateFlow<ProgramDay?> = repository.programDay
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
    val day by viewModel.programDay.collectAsStateWithLifecycle()
    ProgramsScreen(
        following = following?.key,
        custom = custom,
        followingDay = day,
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

/**
 * The built-in programs, then the user's own. A tap opens a program's sheet, its days laid out, from which
 * it is followed from its first day. Following one in place of a program part done asks first:
 * [followingDay] is the followed program's next day, null for none or once it is over.
 */
@Composable
fun ProgramsScreen(
    following: ProgramKey?,
    custom: List<Program>,
    actions: ProgramsActions,
    modifier: Modifier = Modifier,
    followingDay: ProgramDay? = null,
) {
    val families = builtInPrograms.groupBy { (it.key as? ProgramKey.BuiltIn)?.id?.family }
    // Saved by their stored keys: a program is not saveable.
    var previewing by rememberSaveable { mutableStateOf<String?>(null) }
    var switching by rememberSaveable { mutableStateOf<String?>(null) }
    val programOf = { stored: String? -> (builtInPrograms + custom).firstOrNull { it.key.stored == stored } }
    val onOpen = { key: ProgramKey -> previewing = key.stored }
    ScrollPage(stringResource(Res.string.programs_title), modifier, navigation = { BackAction(actions.onBack) }) {
        ProgramFamily.entries.forEach { family ->
            ProgramGroup(familyName(family), families[family].orEmpty(), following, onOpen, actions)
        }
        GroupLabel(stringResource(Res.string.programs_yours))
        ListCard {
            custom.forEach { program ->
                ProgramRow(program, program.key == following, onOpen, actions)
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
    programOf(previewing)?.let { program ->
        // Only a day past the first has anything to lose.
        val replacing = followingDay?.takeIf { it.program.key != program.key }
        ProgramSheet(
            program,
            current = program.key == following,
            replacing = replacing,
            onFollow = {
                if (replacing != null && replacing.number > 1) {
                    previewing = null
                    switching = program.key.stored
                } else {
                    actions.onFollow(program.key)
                }
            },
            onDismiss = { previewing = null },
        )
    }
    val switchTo = programOf(switching)
    val replaced = followingDay
    if (switchTo != null && replaced != null) {
        val name = programName(switchTo)
        ConfirmDialog(
            title = stringResource(Res.string.program_switch_title, name),
            text = stringResource(Res.string.program_switch_text, replaced.number, programName(replaced.program), name),
            confirm = stringResource(Res.string.program_switch),
            dismiss = stringResource(Res.string.program_keep),
            onConfirm = {
                switching = null
                actions.onFollow(switchTo.key)
            },
            onDismiss = { switching = null },
        )
    }
}

/** The heading over a family of built-in programs. */
@Composable
private fun familyName(family: ProgramFamily): String = stringResource(
    when (family) {
        ProgramFamily.Classic -> Res.string.programs_classics
        ProgramFamily.DanJohn -> Res.string.programs_dan_john
        ProgramFamily.Grappling -> Res.string.programs_grappling
    },
)

/**
 * A program before it is followed: what it is, each of its days block by block, and Follow this program,
 * over a line naming the program and day it replaces when one is followed. The program followed shows
 * Following instead.
 */
@Composable
private fun ProgramSheet(
    program: Program,
    current: Boolean,
    replacing: ProgramDay?,
    onFollow: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    val name = programName(program)
    IntervalSheet(title = name, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(name, style = IntervalTheme.type.value, color = palette.text)
            Text(programSummary(program), style = IntervalTheme.type.body, color = palette.textDim)
        }
        program.days.forEachIndexed { i, blocks ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                GroupLabel(stringResource(Res.string.program_preview_day, i + 1))
                // On the sheet's own surface a card needs the variant fill to show as one.
                Column(Modifier.fillMaxWidth().clip(Corners.radius18).background(palette.surfaceVariant)) {
                    blocks.forEachIndexed { j, block ->
                        if (j > 0) RowDivider()
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = Spacing.space16, vertical = Spacing.space12),
                        ) {
                            Text(
                                liftName(block.exercise, block.double),
                                style = IntervalTheme.type.label,
                                color = palette.text,
                            )
                            Text(blockLine(block), style = IntervalTheme.type.meta, color = palette.textDim)
                        }
                    }
                }
            }
        }
        if (current) {
            Text(
                stringResource(Res.string.program_following),
                style = IntervalTheme.type.label,
                color = palette.accent,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                replacing?.let { day ->
                    Text(
                        stringResource(
                            Res.string.program_replaces,
                            programName(day.program),
                            day.number,
                            day.program.totalDays,
                        ),
                        style = IntervalTheme.type.meta,
                        color = palette.textDim,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
                AccentButton(stringResource(Res.string.program_follow), onFollow, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ProgramGroup(
    title: String,
    programs: List<Program>,
    following: ProgramKey?,
    onOpen: (ProgramKey) -> Unit,
    actions: ProgramsActions,
) {
    GroupLabel(title)
    ListCard {
        programs.forEachIndexed { i, program ->
            if (i > 0) RowDivider()
            ProgramRow(program, program.key == following, onOpen, actions)
        }
    }
}

/** A program, opened on a tap, checked while it is followed; one the user made ends in a way to change it. */
@Composable
private fun ProgramRow(program: Program, current: Boolean, onOpen: (ProgramKey) -> Unit, actions: ProgramsActions) {
    val palette = IntervalTheme.palette
    val name = programName(program)
    val key = program.key
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .clickable(role = Role.Button) { onOpen(program.key) }
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
