package dev.justinsmith.lift.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.ProgressBar
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.AbcProtocol
import dev.justinsmith.lift.domain.Challenge
import dev.justinsmith.lift.domain.SNATCH_TEST_REPS
import dev.justinsmith.lift.domain.TEN_THOUSAND
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.today.resources.Res
import dev.justinsmith.lift.today.resources.today_challenge_abc
import dev.justinsmith.lift.today.resources.today_challenge_abc_best
import dev.justinsmith.lift.today.resources.today_challenge_abc_best_as_many
import dev.justinsmith.lift.today.resources.today_challenge_abc_best_on_the_minute
import dev.justinsmith.lift.today.resources.today_challenge_get_up
import dev.justinsmith.lift.today.resources.today_challenge_get_up_best
import dev.justinsmith.lift.today.resources.today_challenge_snatch
import dev.justinsmith.lift.today.resources.today_challenge_snatch_best
import dev.justinsmith.lift.today.resources.today_challenge_snatch_best_from
import dev.justinsmith.lift.today.resources.today_challenge_snatch_best_from_intervals
import dev.justinsmith.lift.today.resources.today_challenge_swings
import dev.justinsmith.lift.today.resources.today_challenge_swings_count
import dev.justinsmith.lift.today.resources.today_challenge_swings_program
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private const val PERCENT = 100

/** The benchmarks under way, each with how far it has come and, where it has one, a bar toward its target. */
@Composable
internal fun ChallengesCard(challenges: List<Challenge>, modifier: Modifier = Modifier) {
    ListCard(modifier) {
        challenges.forEachIndexed { index, challenge ->
            if (index > 0) RowDivider()
            when (challenge) {
                is Challenge.TenThousandSwings -> ChallengeRow(
                    Res.string.today_challenge_swings,
                    stringResource(
                        if (challenge.ofProgram) {
                            Res.string.today_challenge_swings_program
                        } else {
                            Res.string.today_challenge_swings_count
                        },
                        challenge.swings,
                    ),
                    share = challenge.swings.toFloat() / TEN_THOUSAND,
                )

                is Challenge.SnatchTest -> ChallengeRow(
                    Res.string.today_challenge_snatch,
                    snatchBest(challenge),
                    share = challenge.best.toFloat() / SNATCH_TEST_REPS,
                )

                is Challenge.ArmorBuilding -> ChallengeRow(
                    Res.string.today_challenge_abc,
                    stringResource(
                        when (challenge.protocol) {
                            AbcProtocol.OnTheMinute -> Res.string.today_challenge_abc_best_on_the_minute
                            AbcProtocol.AsManyAsYouCan -> Res.string.today_challenge_abc_best_as_many
                            null -> Res.string.today_challenge_abc_best
                        },
                        challenge.best,
                    ),
                    share = null,
                )

                is Challenge.BodyweightGetUp -> ChallengeRow(
                    Res.string.today_challenge_get_up,
                    stringResource(
                        Res.string.today_challenge_get_up_best,
                        formatKg(challenge.kg),
                        (challenge.share * PERCENT).roundToInt(),
                    ),
                    share = challenge.share.toFloat(),
                )
            }
        }
    }
}

@Composable
private fun ChallengeRow(title: StringResource, detail: String, share: Float?) {
    val palette = IntervalTheme.palette
    Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
        Text(stringResource(title), style = IntervalTheme.type.label, color = palette.text)
        Text(detail, style = IntervalTheme.type.meta, color = palette.textDim)
        share?.let { ProgressBar(it.coerceIn(0f, 1f), palette.accent) }
    }
}

/** The snatch test's best, and where it came from when that was an interval workout's snatches. */
@Composable
private fun snatchBest(challenge: Challenge.SnatchTest): String {
    val preset = challenge.preset
    return when {
        !challenge.fromIntervals -> stringResource(Res.string.today_challenge_snatch_best, challenge.best)
        preset != null -> stringResource(Res.string.today_challenge_snatch_best_from, challenge.best, preset)
        else -> stringResource(Res.string.today_challenge_snatch_best_from_intervals, challenge.best)
    }
}
