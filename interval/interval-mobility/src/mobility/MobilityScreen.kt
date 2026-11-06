package dev.justinsmith.interval.mobility

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.mobility.resources.Res
import dev.justinsmith.interval.mobility.resources.mobility_after
import dev.justinsmith.interval.mobility.resources.mobility_before
import dev.justinsmith.interval.mobility.resources.mobility_build
import dev.justinsmith.interval.mobility.resources.mobility_build_hint
import dev.justinsmith.interval.mobility.resources.mobility_open_cd
import dev.justinsmith.interval.mobility.resources.mobility_own
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalSizes
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.SettingsAction
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.StripeMark
import dev.justinsmith.interval.ui.TabGrid
import dev.justinsmith.interval.ui.joined
import dev.justinsmith.interval.ui.movesText
import dev.justinsmith.interval.ui.pressScale
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The flows as a tab's page: Train's Mobility pillar. It goes under the tab's [title], with [header] across the
 * top (the picker of Train's pillars), and its actions end in the gear, [onOpenSettings], as every tab's do.
 */
class MobilityPage(val title: String, val onOpenSettings: () -> Unit, val header: @Composable () -> Unit)

/**
 * What the page's flows lead to: [onStart] runs the flow a key names; [onBuild] opens the editor on a new flow,
 * [onCopy] on a copy of the shipped one a key names, [onEdit] on the user's own by its id.
 */
class MobilityActions(
    val onStart: (String) -> Unit = {},
    val onBuild: () -> Unit = {},
    val onCopy: (String) -> Unit = {},
    val onEdit: (Long) -> Unit = {},
)

/** Resolves the ViewModel and binds it to [MobilityScreen]. */
@Composable
fun MobilityRoute(page: MobilityPage, actions: MobilityActions, modifier: Modifier = Modifier) {
    val viewModel = koinViewModel<MobilityViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    MobilityScreen(state, page, actions, modifier)
}

/**
 * The flows in two groups, by when each is for, the user's own after the shipped ones: a row is its name, its moves
 * and what it takes ("6 moves · no bell"), and how long it runs. Tapping one opens its [FlowSheet], which lists its
 * moves and starts it, and leads to a copy of a shipped one or the editor of one's own. Under them, "On your own"
 * leads to a new one.
 */
@Composable
fun MobilityScreen(
    state: MobilityUiState,
    page: MobilityPage,
    actions: MobilityActions,
    modifier: Modifier = Modifier,
) {
    // The key rather than the flow: it is what saves, and the sheet follows the get-ready as it changes.
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    TabGrid(
        page.title,
        modifier,
        actions = { SettingsAction(page.onOpenSettings) },
        header = page.header,
    ) {
        item(key = "before") {
            FlowGroup(stringResource(Res.string.mobility_before), state.before, onOpen = { openKey = it })
        }
        item(key = "after") {
            FlowGroup(stringResource(Res.string.mobility_after), state.after, onOpen = { openKey = it })
        }
        item(key = "own") { OwnGroup(actions.onBuild) }
    }
    state.item(openKey)?.let { open ->
        FlowSheet(
            item = open,
            prepSeconds = state.prepSeconds,
            onStart = {
                openKey = null
                actions.onStart(open.flow.key)
            },
            onChange = {
                openKey = null
                val id = open.flow.id
                if (id == null) actions.onCopy(open.flow.key) else actions.onEdit(id)
            },
            onDismiss = { openKey = null },
        )
    }
}

@Composable
private fun FlowGroup(label: String, flows: List<FlowItem>, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        GroupLabel(label)
        ListCard {
            flows.forEachIndexed { index, item ->
                if (index > 0) RowDivider(Modifier.padding(start = Spacing.space20))
                FlowRow(item, onOpen = { onOpen(item.flow.key) })
            }
        }
    }
}

/** "On your own": the way to a flow of the user's making. */
@Composable
private fun OwnGroup(onBuild: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.padding(horizontal = Spacing.space16, vertical = Spacing.space4),
        verticalArrangement = Arrangement.spacedBy(Spacing.space8),
    ) {
        GroupLabel(stringResource(Res.string.mobility_own))
        ListCard {
            LinkRow(stringResource(Res.string.mobility_build), stringResource(Res.string.mobility_build_hint), onBuild)
        }
    }
}

/** One flow: its name over its moves and note, and its length at the end of the row, which is the button. */
@Composable
private fun FlowRow(item: FlowItem, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.listRow)
            .clickable(
                interaction,
                LocalIndication.current,
                role = Role.Button,
                onClickLabel = stringResource(Res.string.mobility_open_cd),
                onClick = onOpen,
            )
            .pressScale(interaction)
            .padding(horizontal = Spacing.space16, vertical = Spacing.space12),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        StripeMark(palette.restMark, height = IntervalSizes.historyMarkHeight)
        Column(Modifier.weight(1f)) {
            Text(item.flow.name, style = IntervalTheme.type.label, color = palette.text)
            Text(
                joined(listOfNotNull(movesText(item.flow), item.flow.note)),
                style = IntervalTheme.type.meta,
                color = palette.textDim,
            )
        }
        Text(item.total, style = IntervalTheme.type.value, color = palette.text)
    }
}
