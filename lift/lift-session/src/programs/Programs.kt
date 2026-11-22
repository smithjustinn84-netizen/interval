package dev.justinsmith.lift.programs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import dev.justinsmith.interval.ui.LocalNotices
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.QuietTextButton
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SecondaryButton
import dev.justinsmith.interval.ui.SheetColumns
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.sheetIsSideways
import dev.justinsmith.lift.domain.Block
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.ProgramDay
import dev.justinsmith.lift.domain.ProgramFamily
import dev.justinsmith.lift.domain.ProgramId
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.domain.RestClock
import dev.justinsmith.lift.domain.builtInPrograms
import dev.justinsmith.lift.domain.dayMinutes
import dev.justinsmith.lift.domain.estimatedMinutes
import dev.justinsmith.lift.domain.family
import dev.justinsmith.lift.domain.programCatalogue
import dev.justinsmith.lift.domain.shippedPrograms
import dev.justinsmith.lift.domain.weekBalance
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.notice_program_followed
import dev.justinsmith.lift.session.resources.notice_program_stopped
import dev.justinsmith.lift.session.resources.program_balance_span
import dev.justinsmith.lift.session.resources.program_balance_untrained
import dev.justinsmith.lift.session.resources.program_follow
import dev.justinsmith.lift.session.resources.program_following
import dev.justinsmith.lift.session.resources.program_keep
import dev.justinsmith.lift.session.resources.program_make_yours
import dev.justinsmith.lift.session.resources.program_preview_day
import dev.justinsmith.lift.session.resources.program_replaces
import dev.justinsmith.lift.session.resources.program_see_progress
import dev.justinsmith.lift.session.resources.program_stop
import dev.justinsmith.lift.session.resources.program_switch
import dev.justinsmith.lift.session.resources.program_switch_text
import dev.justinsmith.lift.session.resources.program_switch_title
import dev.justinsmith.lift.session.resources.programs_barbell
import dev.justinsmith.lift.session.resources.programs_barbell_hint
import dev.justinsmith.lift.session.resources.programs_barbell_show
import dev.justinsmith.lift.session.resources.programs_beginner
import dev.justinsmith.lift.session.resources.programs_classics
import dev.justinsmith.lift.session.resources.programs_dan_john
import dev.justinsmith.lift.session.resources.programs_edit_cd
import dev.justinsmith.lift.session.resources.programs_grappling
import dev.justinsmith.lift.session.resources.programs_mixed
import dev.justinsmith.lift.session.resources.programs_new
import dev.justinsmith.lift.session.resources.programs_new_hint
import dev.justinsmith.lift.session.resources.programs_title
import dev.justinsmith.lift.session.resources.programs_yours
import dev.justinsmith.lift.ui.BalanceBars
import dev.justinsmith.lift.ui.blockLine
import dev.justinsmith.lift.ui.estimateLine
import dev.justinsmith.lift.ui.liftName
import dev.justinsmith.lift.ui.programAbout
import dev.justinsmith.lift.ui.programFacts
import dev.justinsmith.lift.ui.programFor
import dev.justinsmith.lift.ui.programName
import dev.justinsmith.lift.ui.programSummary
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** What the Programs page does: follow a program or stop, make one or change one made, and go back. */
data class ProgramsActions(
    val onFollow: (ProgramKey) -> Unit = {},
    val onStop: () -> Unit = {},
    val onNew: () -> Unit = {},
    val onEdit: (uid: String) -> Unit = {},
    val onBack: () -> Unit = {},
    /** Opens the program followed: its days done and left. */
    val onOpenProgress: () -> Unit = {},
    /** Opens a copy of a built-in in the editor, to make it the user's. */
    val onCopy: (ProgramId) -> Unit = {},
)

@Composable
fun ProgramsRoute(
    onNew: () -> Unit,
    onEdit: (uid: String) -> Unit,
    onBack: () -> Unit,
    onOpenProgress: () -> Unit = {},
    onCopy: (ProgramId) -> Unit = {},
    open: String? = null,
) {
    val viewModel = koinViewModel<ProgramsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProgramsScreen(
        following = state.following?.key,
        custom = state.custom,
        followingDay = state.followingDay,
        restSeconds = state.restSeconds,
        bar = state.bar,
        actions = ProgramsActions(
            onFollow = {
                viewModel.follow(it)
                onBack()
            },
            onStop = {
                viewModel.stop()
                onBack()
            },
            onNew = onNew,
            onEdit = onEdit,
            onBack = onBack,
            onOpenProgress = onOpenProgress,
            onCopy = onCopy,
        ),
        open = open,
    )
}

/**
 * The built-in programs by family, then the user's own, side by side on a wider window. A tap opens a
 * program's sheet, its days laid out, from which it is followed from its first day. Following one in place
 * of a program part done asks first: [followingDay] is the followed program's next day, null for none or
 * once it is over. Each program and each of its days says about how long it takes, resting [restSeconds]
 * between sets. It opens on the sheet of the program stored as [open], when one is. With no [bar] owned the barbell
 * programs stand folded behind a row that shows them, unless one of them is followed.
 */
@Composable
fun ProgramsScreen(
    following: ProgramKey?,
    custom: List<Program>,
    actions: ProgramsActions,
    modifier: Modifier = Modifier,
    followingDay: ProgramDay? = null,
    restSeconds: Int = RestClock.DEFAULT_SECONDS,
    open: String? = null,
    bar: Boolean = true,
) {
    var showBarbell by rememberSaveable { mutableStateOf(false) }
    val families = programCatalogue(following, bar = bar || showBarbell).groupBy { it.family }
    // Saved by their stored keys: a program is not saveable.
    var previewing by rememberSaveable { mutableStateOf(open) }
    var switching by rememberSaveable { mutableStateOf<String?>(null) }
    val programOf = { stored: String? -> (shippedPrograms + custom).firstOrNull { it.key.stored == stored } }
    val notices = LocalNotices.current
    val onOpen = { key: ProgramKey -> previewing = key.stored }
    val gutter = Modifier.padding(horizontal = Spacing.space16)
    // One column on a phone; wider windows lay the families out side by side.
    TabGrid(stringResource(Res.string.programs_title), modifier, navigation = { BackAction(actions.onBack) }) {
        val folded = !bar && !showBarbell
        familyItems(families, folded, onShowBarbell = { showBarbell = true }) { family, programs ->
            ProgramGroup(familyName(family), programs, following, restSeconds, onOpen, actions, gutter)
        }
        item(key = "yours") { YoursGroup(custom, following, restSeconds, onOpen, actions, gutter) }
        val followed = programOf(following?.stored)
        if (followed != null) {
            item(key = "stop", span = StaggeredGridItemSpan.FullLine) {
                val stopped = stringResource(Res.string.notice_program_stopped, programName(followed))
                QuietTextButton(
                    stringResource(Res.string.program_stop),
                    onClick = {
                        notices.show(stopped)
                        actions.onStop()
                    },
                    gutter.padding(top = Spacing.space12),
                )
            }
        }
    }
    programOf(previewing)?.let { program ->
        // Only a day past the first has anything to lose.
        val replacing = followingDay?.takeIf { it.program.key != program.key }
        val followedText = stringResource(Res.string.notice_program_followed, programName(program))
        ProgramSheet(
            program,
            restSeconds,
            current = program.key == following,
            replacing = replacing,
            onFollow = {
                if (replacing != null && replacing.number > 1) {
                    previewing = null
                    switching = program.key.stored
                } else {
                    notices.show(followedText)
                    actions.onFollow(program.key)
                }
            },
            onOpenProgress = {
                previewing = null
                actions.onOpenProgress()
            },
            onCopy = (program.key as? ProgramKey.BuiltIn)?.let { key ->
                {
                    previewing = null
                    actions.onCopy(key.id)
                }
            },
            onDismiss = { previewing = null },
        )
    }
    val switchTo = programOf(switching)
    val replaced = followingDay
    if (switchTo != null && replaced != null) {
        val name = programName(switchTo)
        val followedText = stringResource(Res.string.notice_program_followed, name)
        ConfirmDialog(
            title = stringResource(Res.string.program_switch_title, name),
            text = stringResource(Res.string.program_switch_text, replaced.number, programName(replaced.program), name),
            confirm = stringResource(Res.string.program_switch),
            dismiss = stringResource(Res.string.program_keep),
            onConfirm = {
                switching = null
                notices.show(followedText)
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
        ProgramFamily.Beginner -> Res.string.programs_beginner
        ProgramFamily.Classic -> Res.string.programs_classics
        ProgramFamily.DanJohn -> Res.string.programs_dan_john
        ProgramFamily.Grappling -> Res.string.programs_grappling
        ProgramFamily.Mixed -> Res.string.programs_mixed
        ProgramFamily.Barbell -> Res.string.programs_barbell
    },
)

/**
 * A program before it is followed: what it is, what a week of it works, each of its days block by block, and
 * Follow this program, over a line naming the program and day it replaces when one is followed. The program
 * followed shows Following instead, and the way to its days done and left. A built-in offers Make it yours under
 * them ([onCopy]), to open a copy in the editor.
 */
@Composable
private fun ProgramSheet(
    program: Program,
    restSeconds: Int,
    current: Boolean,
    replacing: ProgramDay?,
    onFollow: () -> Unit,
    onOpenProgress: () -> Unit,
    onCopy: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    val name = programName(program)
    val sideways = sheetIsSideways()
    IntervalSheet(title = name, onDismiss = onDismiss) {
        // What the program is, its days, then Follow; on a phone on its side Follow stays by the name, the
        // days beside.
        SheetColumns(
            sideways,
            first = {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
                    Text(name, style = IntervalTheme.type.value, color = palette.text)
                    programFor(program)?.let { Text(it, style = IntervalTheme.type.label, color = palette.text) }
                    Text(programSummary(program), style = IntervalTheme.type.body, color = palette.textDim)
                }
                if (sideways) FollowFooter(current, replacing, onFollow, onOpenProgress, onCopy)
            },
            second = {
                // On its side the days come first, beside the name, and the balance under them.
                if (!sideways) WeekBalance(program)
                ProgramDays(program, restSeconds)
                if (sideways) {
                    WeekBalance(program)
                } else {
                    FollowFooter(current, replacing, onFollow, onOpenProgress, onCopy)
                }
            },
            spacing = Spacing.space20,
        )
    }
}

/** The sets a week of [program] plans, by muscle group, naming any group it leaves out. */
@Composable
private fun WeekBalance(program: Program) {
    BalanceBars(
        remember(program) { program.weekBalance() },
        span = Res.string.program_balance_span,
        untrained = Res.string.program_balance_untrained,
    )
}

/** Each day of [program], its blocks in a card, under its number and about how long it takes. */
@Composable
private fun ProgramDays(program: Program, restSeconds: Int) {
    val palette = IntervalTheme.palette
    program.days.forEachIndexed { i, blocks ->
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                GroupLabel(stringResource(Res.string.program_preview_day, i + 1), Modifier.weight(1f))
                val minutes = remember(program, i, restSeconds) { program.day(i + 1).estimatedMinutes(restSeconds) }
                Text(
                    estimateLine(minutes),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                    modifier = Modifier.padding(end = Spacing.space4),
                )
            }
            // On the sheet's own surface a card needs the variant fill to show as one.
            Column(Modifier.fillMaxWidth().clip(Corners.radius18).background(palette.surfaceVariant)) {
                blocks.forEachIndexed { j, block ->
                    if (j > 0) RowDivider()
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
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
}

/**
 * Following and the way to its days done, for the program followed; otherwise Follow, under the day it takes
 * the place of. Make it yours under either, for a built-in.
 */
@Composable
private fun FollowFooter(
    current: Boolean,
    replacing: ProgramDay?,
    onFollow: () -> Unit,
    onOpenProgress: () -> Unit,
    onCopy: (() -> Unit)?,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        FollowButtons(current, replacing, onFollow, onOpenProgress)
        onCopy?.let { QuietTextButton(stringResource(Res.string.program_make_yours), it) }
    }
}

@Composable
private fun FollowButtons(current: Boolean, replacing: ProgramDay?, onFollow: () -> Unit, onOpenProgress: () -> Unit) {
    val palette = IntervalTheme.palette
    if (current) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
            Text(
                stringResource(Res.string.program_following),
                style = IntervalTheme.type.label,
                color = palette.accent,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            SecondaryButton(stringResource(Res.string.program_see_progress), onOpenProgress, Modifier.fillMaxWidth())
        }
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

@Composable
private fun ProgramGroup(
    title: String,
    programs: List<Program>,
    following: ProgramKey?,
    restSeconds: Int,
    onOpen: (ProgramKey) -> Unit,
    actions: ProgramsActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        GroupLabel(title)
        ListCard {
            programs.forEachIndexed { i, program ->
                if (i > 0) RowDivider()
                ProgramRow(program, program.key == following, restSeconds, onOpen, actions)
            }
        }
    }
}

/**
 * A card a family of the built-ins on offer, drawn by [group], in [ProgramFamily]'s order; one with none drawn not
 * at all. The barbell's, while [folded] for someone with no bar, is a row that [onShowBarbell]s it, under the one of
 * them followed when there is one.
 */
private fun LazyStaggeredGridScope.familyItems(
    families: Map<ProgramFamily?, List<Program>>,
    folded: Boolean,
    onShowBarbell: () -> Unit,
    group: @Composable (ProgramFamily, List<Program>) -> Unit,
) {
    val gutter = Modifier.padding(horizontal = Spacing.space16)
    ProgramFamily.entries.forEach { family ->
        val programs = families[family].orEmpty()
        val unfold = folded && family == ProgramFamily.Barbell
        if (programs.isNotEmpty() || unfold) {
            item(key = family.name) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.space8)) {
                    if (programs.isNotEmpty()) group(family, programs)
                    // Under the one followed, the row alone: the heading is over it already.
                    if (unfold) FoldedGroup(familyName(family).takeIf { programs.isEmpty() }, onShowBarbell, gutter)
                }
            }
        }
    }
}

/** A family left folded, as the barbell's is for someone with no bar: a row that shows its programs. */
@Composable
private fun FoldedGroup(title: String?, onShow: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        title?.let { GroupLabel(it) }
        ListCard {
            LinkRow(
                stringResource(Res.string.programs_barbell_show),
                stringResource(Res.string.programs_barbell_hint),
                onClick = onShow,
            )
        }
    }
}

/** The programs the user made, then a way to make one. */
@Composable
private fun YoursGroup(
    custom: List<Program>,
    following: ProgramKey?,
    restSeconds: Int,
    onOpen: (ProgramKey) -> Unit,
    actions: ProgramsActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        GroupLabel(stringResource(Res.string.programs_yours))
        ListCard {
            custom.forEach { program ->
                ProgramRow(program, program.key == following, restSeconds, onOpen, actions)
                RowDivider()
            }
            LinkRow(
                stringResource(Res.string.programs_new),
                stringResource(Res.string.programs_new_hint),
                onClick = actions.onNew,
            )
        }
    }
}

/**
 * A program, opened on a tap, checked while it is followed: under its name how often, for how long and about how
 * long a day takes, then what it asks for; one the user made ends in a way to change it.
 */
@Composable
private fun ProgramRow(
    program: Program,
    current: Boolean,
    restSeconds: Int,
    onOpen: (ProgramKey) -> Unit,
    actions: ProgramsActions,
) {
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
                end = if (key is ProgramKey.Custom) Spacing.space4 else Spacing.space12,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = IntervalTheme.type.label, color = palette.text)
            // What it is chosen by first, in the text's ink: who it is for, else how often and how long; then the
            // rest, a built-in's facts or what one made asks for, cut to two lines.
            val facts = programFacts(program, remember(program, restSeconds) { program.dayMinutes(restSeconds) })
            val forWhom = programFor(program)
            Text(forWhom ?: facts, style = IntervalTheme.type.meta, color = palette.text)
            Text(
                if (forWhom != null) facts else programAbout(program),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
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
        } else {
            Icon(
                IntervalIcons.Chevron,
                contentDescription = null,
                tint = palette.textDim,
                modifier = Modifier.size(Sizes.icon),
            )
        }
    }
}

@PreviewFormFactors
@Composable
private fun ProgramsScreenPreview() {
    val following = builtInPrograms.first()
    val mine = Program(
        ProgramKey.Custom("mine"),
        days = listOf(
            listOf(Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10)),
            listOf(Block(BuiltInExercise.GobletSquat, rounds = 3, reps = 8)),
        ),
        daysPerWeek = 3,
        weeks = 4,
        name = "Garage mornings",
    )
    IntervalTheme {
        ProgramsScreen(
            following = following.key,
            custom = listOf(mine),
            actions = ProgramsActions(),
            followingDay = ProgramDay(following, number = 3, week = 1, blocks = following.days.first()),
        )
    }
}
