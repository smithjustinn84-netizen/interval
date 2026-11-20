package dev.justinsmith.lift.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.Opacity
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Muscle
import dev.justinsmith.lift.domain.MuscleMap
import dev.justinsmith.lift.ui.resources.Res
import dev.justinsmith.lift.ui.resources.balance_assisted
import dev.justinsmith.lift.ui.resources.balance_worked
import dev.justinsmith.lift.ui.resources.muscle_map_back
import dev.justinsmith.lift.ui.resources.muscle_map_cd
import dev.justinsmith.lift.ui.resources.muscle_map_front
import dev.justinsmith.lift.ui.resources.muscles_worked_also
import dev.justinsmith.lift.ui.resources.muscles_worked_joined
import org.jetbrains.compose.resources.stringResource

/**
 * Front and back anatomical silhouettes highlighting the muscles in [map]: primary muscles in the accent color
 * (shaded by relative volume) and assisted-only muscles in a lighter accent share, matching [BalanceBars].
 */
@Composable
fun MuscleBodyMap(
    map: MuscleMap,
    modifier: Modifier = Modifier,
    height: Dp = LiftSizes.bodyMap,
    describe: Boolean = true,
) {
    val palette = IntervalTheme.palette
    val description = stringResource(Res.string.muscle_map_cd)
    val semanticsModifier = if (describe) {
        modifier.clearAndSetSemantics { contentDescription = description }
    } else {
        modifier.clearAndSetSemantics {}
    }
    Row(
        semanticsModifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space24, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top,
    ) {
        BodyHalf(
            silhouette = MuscleBodyPaths.frontSilhouette,
            regions = MuscleBodyPaths.frontMuscles,
            map = map,
            label = stringResource(Res.string.muscle_map_front),
            height = height,
            accent = palette.accent,
            neutral = palette.textDim,
        )
        BodyHalf(
            silhouette = MuscleBodyPaths.backSilhouette,
            regions = MuscleBodyPaths.backMuscles,
            map = map,
            label = stringResource(Res.string.muscle_map_back),
            height = height,
            accent = palette.accent,
            neutral = palette.textDim,
        )
    }
}

/**
 * A card showing [map]'s front and back body diagram over its primary and assisted muscle names and legend. Used on
 * Exercise Detail, Session Summary, and finished Session Detail.
 */
@Composable
fun MusclesWorkedCard(
    map: MuscleMap,
    modifier: Modifier = Modifier,
    fill: Color = IntervalTheme.palette.surface,
    note: String? = null,
) {
    val palette = IntervalTheme.palette
    ListCard(modifier, fill = fill) {
        Column(Modifier.padding(Spacing.space16), verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
            MuscleBodyMap(map, describe = false)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.space4)) {
                val primary = map.primary
                val assisted = map.assistedOnly
                if (primary.isNotEmpty()) {
                    Text(joinedMuscles(primary), style = IntervalTheme.type.label, color = palette.text)
                }
                if (assisted.isNotEmpty()) {
                    val text = if (primary.isEmpty()) {
                        joinedMuscles(assisted)
                    } else {
                        stringResource(Res.string.muscles_worked_also, joinedMuscles(assisted))
                    }
                    Text(text, style = IntervalTheme.type.meta, color = palette.textDim)
                }
                note?.let {
                    Text(it, style = IntervalTheme.type.caption, color = palette.textDim)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.space16)) {
                if (map.primary.isNotEmpty()) {
                    Legend(palette.accent, stringResource(Res.string.balance_worked))
                }
                if (map.assistedOnly.isNotEmpty()) {
                    Legend(
                        palette.accent.copy(alpha = Opacity.LIGHT_SHARE),
                        stringResource(Res.string.balance_assisted),
                    )
                }
            }
        }
    }
}

@Composable
private fun BodyHalf(
    silhouette: List<Path>,
    regions: Map<Muscle, List<Path>>,
    map: MuscleMap,
    label: String,
    height: Dp,
    accent: Color,
    neutral: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.space4),
    ) {
        Canvas(
            Modifier
                .height(height)
                .aspectRatio(MuscleBodyPaths.VIEW_WIDTH / MuscleBodyPaths.VIEW_HEIGHT),
        ) {
            drawBodyHalf(silhouette, regions, map, accent, neutral)
        }
        Text(label, style = IntervalTheme.type.caption, color = IntervalTheme.palette.textDim)
    }
}

private fun DrawScope.drawBodyHalf(
    silhouette: List<Path>,
    regions: Map<Muscle, List<Path>>,
    map: MuscleMap,
    accent: Color,
    neutral: Color,
) {
    val scale = minOf(size.width / MuscleBodyPaths.VIEW_WIDTH, size.height / MuscleBodyPaths.VIEW_HEIGHT)
    val left = (size.width - MuscleBodyPaths.VIEW_WIDTH * scale) / 2f
    val top = (size.height - MuscleBodyPaths.VIEW_HEIGHT * scale) / 2f
    val baseFill = neutral.copy(alpha = SILHOUETTE_FILL_ALPHA)
    val baseStroke = neutral.copy(alpha = SILHOUETTE_STROKE_ALPHA)
    val inactiveFill = neutral.copy(alpha = INACTIVE_MUSCLE_ALPHA)

    withTransform({
        translate(left, top)
        scale(scale, scale, pivot = Offset.Zero)
    }) {
        silhouette.forEach { path ->
            drawPath(path, color = baseFill)
        }
        regions.forEach { (muscle, paths) ->
            val color = muscleFill(muscle, map, accent, inactiveFill)
            paths.forEach { path -> drawPath(path, color = color) }
        }
        val outline = Stroke(width = OUTLINE_STROKE_UNITS, join = StrokeJoin.Round)
        silhouette.forEach { path ->
            drawPath(path, color = baseStroke, style = outline)
        }
    }
}

private fun muscleFill(muscle: Muscle, map: MuscleMap, accent: Color, inactive: Color): Color {
    val primary = map.primaryIntensity(muscle)
    if (primary > 0f) {
        val alpha = PRIMARY_MIN_ALPHA + (1f - PRIMARY_MIN_ALPHA) * primary
        return accent.copy(alpha = alpha)
    }
    val assisted = map.assistedIntensity(muscle)
    if (assisted > 0f) {
        val alpha = ASSISTED_MIN_ALPHA + (Opacity.LIGHT_SHARE - ASSISTED_MIN_ALPHA) * assisted
        return accent.copy(alpha = alpha)
    }
    return inactive
}

@Composable
private fun joinedMuscles(muscles: List<Muscle>): String =
    muscles.map { muscleName(it) }.reduce { line, name -> stringResource(Res.string.muscles_worked_joined, line, name) }

private const val SILHOUETTE_FILL_ALPHA = 0.10f
private const val SILHOUETTE_STROKE_ALPHA = 0.24f
private const val INACTIVE_MUSCLE_ALPHA = 0.08f
private const val PRIMARY_MIN_ALPHA = 0.52f
private const val ASSISTED_MIN_ALPHA = 0.22f
private const val OUTLINE_STROKE_UNITS = 1.1f

@PreviewFormFactors
@Composable
private fun MuscleBodyMapSwingPreview() {
    IntervalTheme {
        MusclesWorkedCard(
            MuscleMap.of(BuiltInExercise.TwoHandSwing),
            modifier = Modifier.padding(Spacing.space16),
        )
    }
}

@PreviewFormFactors
@Composable
private fun MuscleBodyMapSessionPreview() {
    val map = MuscleMap.ofSets(
        listOf(
            BuiltInExercise.GobletSquat to null,
            BuiltInExercise.GobletSquat to null,
            BuiltInExercise.GobletSquat to null,
            BuiltInExercise.Press to null,
            BuiltInExercise.Press to null,
            BuiltInExercise.Row to null,
        ),
    )
    IntervalTheme {
        MusclesWorkedCard(map, modifier = Modifier.padding(Spacing.space16))
    }
}
