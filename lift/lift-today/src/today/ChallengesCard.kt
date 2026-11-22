package dev.justinsmith.lift.today

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.sp
import dev.justinsmith.core.resources.ic_equip_double_kb
import dev.justinsmith.interval.ui.Angles
import dev.justinsmith.interval.ui.IntervalIcons
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.Sizes
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.interval.ui.isLargeText
import dev.justinsmith.interval.ui.pressScale
import dev.justinsmith.lift.domain.AbcProtocol
import dev.justinsmith.lift.domain.Challenge
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.SNATCH_TEST_REPS
import dev.justinsmith.lift.domain.TEN_THOUSAND
import dev.justinsmith.lift.domain.TestId
import dev.justinsmith.lift.domain.test
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
import dev.justinsmith.lift.today.resources.today_challenge_test_cd
import dev.justinsmith.lift.ui.LiftSizes
import dev.justinsmith.lift.ui.weightValue
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import kotlin.math.roundToInt
import dev.justinsmith.core.resources.Res as CoreRes

private const val PERCENT = 100
private const val GAUGE_LABEL_SP = 10f
private const val TABULAR = "tnum"
private const val MEDALLION_FILL_ALPHA = 0.14f
private const val MEDALLION_BORDER_ALPHA = 0.36f

/**
 * The benchmarks under way, each with how far it has come beside a circular dial gauge toward its target (or a
 * double-bell medallion for open-ended benchmarks). One that can be done as a test opens it ([onOpenTest]).
 */
@Composable
internal fun ChallengesCard(
    challenges: List<Challenge>,
    onOpenTest: (TestId) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListCard(modifier) {
        challenges.forEachIndexed { index, challenge ->
            if (index > 0) RowDivider()
            val open = challenge.test?.let { test -> { onOpenTest(test) } }
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
                    onClick = open,
                )

                is Challenge.SnatchTest -> ChallengeRow(
                    Res.string.today_challenge_snatch,
                    snatchBest(challenge),
                    share = challenge.best.toFloat() / SNATCH_TEST_REPS,
                    onClick = open,
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
                    onClick = open,
                )

                is Challenge.BodyweightGetUp -> ChallengeRow(
                    Res.string.today_challenge_get_up,
                    stringResource(
                        Res.string.today_challenge_get_up_best,
                        weightValue(challenge.kg, Equipment.Kettlebell),
                        (challenge.share * PERCENT).roundToInt(),
                    ),
                    share = challenge.share.toFloat(),
                    onClick = open,
                )
            }
        }
    }
}

/** A challenge's row: leading benchmark dial or medallion beside its title and progress detail. */
@Composable
private fun ChallengeRow(title: StringResource, detail: String, share: Float?, onClick: (() -> Unit)?) {
    val palette = IntervalTheme.palette
    val interaction = remember { MutableInteractionSource() }
    val tap = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .clickable(
                interaction,
                LocalIndication.current,
                onClickLabel = stringResource(Res.string.today_challenge_test_cd),
                role = Role.Button,
                onClick = onClick,
            )
            // After the ripple, as a link row does: the content shrinks, the ripple keeps the card's width.
            .pressScale(interaction)
    }
    Row(
        Modifier.fillMaxWidth().then(tap).padding(Spacing.space16),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
    ) {
        if (!isLargeText()) {
            if (share != null) ChallengeGauge(share) else BenchmarkMedallion()
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
            Text(stringResource(title), style = IntervalTheme.type.label, color = palette.text)
            Text(detail, style = IntervalTheme.type.meta, color = palette.textDim)
        }
        if (onClick != null) {
            Icon(IntervalIcons.Chevron, null, tint = palette.textDim, modifier = Modifier.size(Sizes.chipIcon))
        }
    }
}

/** Compact circular progress dial toward a benchmark target, carrying a check once complete or its percentage. */
@Composable
private fun ChallengeGauge(share: Float, modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    val clamped = share.coerceIn(0f, 1f)
    val track = palette.line
    val accent = palette.accent
    Box(
        modifier.size(LiftSizes.challengeGauge).clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Spacer(
            Modifier.fillMaxSize().drawWithCache {
                val strokePx = LiftSizes.challengeGaugeStroke.toPx()
                val stroke = Stroke(width = strokePx, cap = StrokeCap.Round)
                val diameter = (size.minDimension - strokePx).coerceAtLeast(1f)
                val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                val arcSize = Size(diameter, diameter)
                onDrawBehind {
                    drawArc(track, 0f, Angles.FULL_TURN, false, topLeft, arcSize, style = stroke)
                    if (clamped > 0f) {
                        drawArc(
                            accent,
                            Angles.TWELVE_O_CLOCK,
                            Angles.FULL_TURN * clamped,
                            false,
                            topLeft,
                            arcSize,
                            style = stroke,
                        )
                    }
                }
            },
        )
        if (clamped >= 1f) {
            Icon(IntervalIcons.Check, null, tint = accent, modifier = Modifier.size(Sizes.chipIcon))
        } else {
            Text(
                "${(clamped * PERCENT).roundToInt()}%",
                style = IntervalTheme.type.caption.copy(
                    fontSize = GAUGE_LABEL_SP.sp,
                    lineHeight = GAUGE_LABEL_SP.sp,
                    fontFeatureSettings = TABULAR,
                ),
                color = palette.text,
                maxLines = 1,
            )
        }
    }
}

/** Circular tinted medallion for an open-ended benchmark (Armor Building Complex) with no fixed ceiling. */
@Composable
private fun BenchmarkMedallion(modifier: Modifier = Modifier) {
    val palette = IntervalTheme.palette
    Box(
        modifier
            .size(LiftSizes.challengeGauge)
            .clip(CircleShape)
            .background(palette.accent.copy(alpha = MEDALLION_FILL_ALPHA))
            .border(Sizes.hairline, palette.accent.copy(alpha = MEDALLION_BORDER_ALPHA), CircleShape)
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            vectorResource(CoreRes.drawable.ic_equip_double_kb),
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(Sizes.iconInline),
        )
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
