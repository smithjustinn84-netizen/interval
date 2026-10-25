package dev.justinsmith.interval.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.history_pick_text
import dev.justinsmith.interval.history.resources.history_pick_title
import dev.justinsmith.interval.ui.EmptyState
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.SetupLanes
import dev.justinsmith.interval.ui.Sizes
import org.jetbrains.compose.resources.stringResource

/** Beside History's log on a wide window, until a workout is picked from it: what will open here. */
@Composable
fun HistoryPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(IntervalTheme.palette.background), contentAlignment = Alignment.Center) {
        SetupLanes(Modifier.fillMaxSize())
        EmptyState(
            IntervalIcons.History,
            stringResource(Res.string.history_pick_title),
            stringResource(Res.string.history_pick_text),
            Modifier.widthIn(max = Sizes.content),
        )
    }
}
