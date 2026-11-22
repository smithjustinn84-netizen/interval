package dev.justinsmith.lift.session

import androidx.compose.runtime.Composable
import dev.justinsmith.lift.domain.SupersetPlace
import dev.justinsmith.lift.domain.SupersetRest
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.common_joined
import dev.justinsmith.lift.session.resources.superset_rest_alternating
import dev.justinsmith.lift.session.resources.superset_rest_shared
import dev.justinsmith.lift.session.resources.superset_straight_on
import dev.justinsmith.lift.session.resources.superset_with
import dev.justinsmith.lift.ui.exerciseName
import org.jetbrains.compose.resources.stringResource

/**
 * Where a set stands in its superset, in words: "A1 · straight on to Pull-up" when the pair's other set follows it
 * with no rest, else "A2 · with Push-up · rest after the pair".
 */
@Composable
internal fun supersetLine(place: SupersetPlace, straightOn: Boolean): String {
    val partner = exerciseName(place.partner)
    return if (straightOn) {
        stringResource(Res.string.common_joined, place.mark, stringResource(Res.string.superset_straight_on, partner))
    } else {
        stringResource(
            Res.string.common_joined,
            stringResource(Res.string.common_joined, place.mark, stringResource(Res.string.superset_with, partner)),
            supersetRestWords(place.rest),
        )
    }
}

/** How a superset rests, as a line says it: "rest after the pair" or "rest after each". */
@Composable
internal fun supersetRestWords(rest: SupersetRest): String = stringResource(
    when (rest) {
        SupersetRest.Shared -> Res.string.superset_rest_shared
        SupersetRest.Alternating -> Res.string.superset_rest_alternating
    },
)
