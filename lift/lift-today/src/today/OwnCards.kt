package dev.justinsmith.lift.today

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.ProgramKey
import dev.justinsmith.lift.today.resources.Res
import dev.justinsmith.lift.today.resources.bells_title
import dev.justinsmith.lift.today.resources.common_joined
import dev.justinsmith.lift.today.resources.library_entry_text
import dev.justinsmith.lift.today.resources.library_title
import dev.justinsmith.lift.today.resources.today_equipment_hint
import dev.justinsmith.lift.today.resources.today_equipment_owned
import dev.justinsmith.lift.today.resources.today_free_session
import dev.justinsmith.lift.today.resources.today_free_session_hint
import dev.justinsmith.lift.today.resources.today_tests
import dev.justinsmith.lift.today.resources.today_tests_hint
import dev.justinsmith.lift.ui.weightValue
import org.jetbrains.compose.resources.stringResource

// Strength's groups of what there is apart from the program: on one's own, and what is looked up or set.

/**
 * What there is to do on one's own: a session, while none is under way and a program is followed (with none, the
 * Following card starts one), and the tests.
 */
@Composable
internal fun OwnCard(
    state: TodayUiState,
    onStartSession: (ProgramKey?) -> Unit,
    onOpenTests: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListCard(modifier) {
        if (!state.inProgress && state.following != null) {
            LinkRow(
                stringResource(Res.string.today_free_session),
                stringResource(Res.string.today_free_session_hint),
                { onStartSession(null) },
            )
            RowDivider()
        }
        LinkRow(stringResource(Res.string.today_tests), stringResource(Res.string.today_tests_hint), onOpenTests)
    }
}

/** What is looked up or set: the exercises, and the equipment with the bells owned. */
@Composable
internal fun KitCard(
    state: TodayUiState,
    onOpenExercises: () -> Unit,
    onOpenBells: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListCard(modifier) {
        LinkRow(
            stringResource(Res.string.library_title),
            stringResource(Res.string.library_entry_text),
            onOpenExercises,
        )
        RowDivider()
        val bells = state.ownedBells.map { weightValue(it, Equipment.Kettlebell) }
        LinkRow(
            stringResource(Res.string.bells_title),
            if (bells.isEmpty()) {
                stringResource(Res.string.today_equipment_hint)
            } else {
                stringResource(
                    Res.string.today_equipment_owned,
                    bells.reduce { joined, part ->
                        stringResource(Res.string.common_joined, joined, part)
                    },
                )
            },
            onOpenBells,
        )
    }
}
