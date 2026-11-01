package dev.justinsmith.interval.history

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.effort_all_out
import dev.justinsmith.interval.history.resources.effort_easy
import dev.justinsmith.interval.history.resources.effort_hard
import dev.justinsmith.interval.history.resources.effort_steady
import dev.justinsmith.interval.history.resources.effort_very_hard
import dev.justinsmith.interval.model.PerceivedEffort
import org.jetbrains.compose.resources.stringResource

/** The word for how hard a workout felt, as its summary said it: "Easy" through "All out". */
@Composable
internal fun effortWord(effort: PerceivedEffort): String = stringResource(
    when (effort) {
        PerceivedEffort.Easy -> Res.string.effort_easy
        PerceivedEffort.Steady -> Res.string.effort_steady
        PerceivedEffort.Hard -> Res.string.effort_hard
        PerceivedEffort.VeryHard -> Res.string.effort_very_hard
        PerceivedEffort.AllOut -> Res.string.effort_all_out
    },
)
