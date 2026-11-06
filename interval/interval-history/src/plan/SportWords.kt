package dev.justinsmith.interval.plan

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.history.resources.Res
import dev.justinsmith.interval.history.resources.sport_bjj
import dev.justinsmith.interval.history.resources.sport_bjj_title
import dev.justinsmith.interval.history.resources.sport_climbing
import dev.justinsmith.interval.history.resources.sport_climbing_short
import dev.justinsmith.interval.history.resources.sport_running
import dev.justinsmith.interval.history.resources.sport_running_short
import dev.justinsmith.interval.history.resources.sport_running_title
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.shortSportName
import org.jetbrains.compose.resources.stringResource

/** A sport by name, as the picker lists it: "BJJ", "Climbing", "Running", or the user's own. */
@Composable
fun sportName(sport: Sport): String = when (sport) {
    Sport.Bjj -> stringResource(Res.string.sport_bjj)
    Sport.Climbing -> stringResource(Res.string.sport_climbing)
    Sport.Running -> stringResource(Res.string.sport_running)
    is Sport.Custom -> sport.name
}

/** A day of a sport, as the plan and Today name it: "BJJ class", "Climbing", "Run", or the user's own. */
@Composable
internal fun sportTitle(sport: Sport): String = when (sport) {
    Sport.Bjj -> stringResource(Res.string.sport_bjj_title)
    Sport.Climbing -> stringResource(Res.string.sport_climbing)
    Sport.Running -> stringResource(Res.string.sport_running_title)
    is Sport.Custom -> sport.name
}

/** A sport short enough to sit under a day of the week: "BJJ", "Climb", "Run", or the start of the user's own. */
@Composable
internal fun sportShort(sport: Sport): String = when (sport) {
    Sport.Bjj -> stringResource(Res.string.sport_bjj)
    Sport.Climbing -> stringResource(Res.string.sport_climbing_short)
    Sport.Running -> stringResource(Res.string.sport_running_short)
    is Sport.Custom -> shortSportName(sport.name)
}
