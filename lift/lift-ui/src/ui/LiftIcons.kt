package dev.justinsmith.lift.ui

import dev.justinsmith.core.resources.Res
import dev.justinsmith.core.resources.ic_equip_bands
import dev.justinsmith.core.resources.ic_equip_double_kb
import dev.justinsmith.core.resources.ic_equip_kettlebell
import dev.justinsmith.core.resources.ic_equip_pullup_bar
import dev.justinsmith.core.resources.ic_pattern_carry
import dev.justinsmith.core.resources.ic_pattern_getup
import dev.justinsmith.core.resources.ic_pattern_hinge
import dev.justinsmith.core.resources.ic_pattern_press
import dev.justinsmith.core.resources.ic_pattern_squat
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.Doubles
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Pattern
import dev.justinsmith.lift.domain.movement
import org.jetbrains.compose.resources.DrawableResource

/** The vector glyph for a kind of [Equipment], or null when the word stands on its own. */
fun Equipment.iconRes(doubleBell: Boolean = false): DrawableResource? = when (this) {
    Equipment.Kettlebell -> if (doubleBell) Res.drawable.ic_equip_double_kb else Res.drawable.ic_equip_kettlebell
    Equipment.Band -> Res.drawable.ic_equip_bands
    else -> null
}

/** The vector glyph for a movement [Pattern], or null for patterns without a dedicated mark. */
fun Pattern.iconRes(): DrawableResource? = when (this) {
    Pattern.Hinge -> Res.drawable.ic_pattern_hinge
    Pattern.Squat -> Res.drawable.ic_pattern_squat
    Pattern.Press -> Res.drawable.ic_pattern_press
    Pattern.Carry -> Res.drawable.ic_pattern_carry
    Pattern.GetUp -> Res.drawable.ic_pattern_getup
    else -> null
}

/**
 * The leading vector glyph for an [Exercise]: pull-up bar for pull-ups/chin-ups, double kettlebells for
 * always-paired or double-bell lifts, its movement pattern when one exists, otherwise its equipment glyph.
 */
fun Exercise.iconRes(doubleBell: Boolean = false): DrawableResource? = when (this) {
    BuiltInExercise.PullUp, BuiltInExercise.ChinUp -> Res.drawable.ic_equip_pullup_bar

    is BuiltInExercise -> movement.pattern.iconRes()
        ?: equipment.iconRes(doubleBell = doubleBell || doubles == Doubles.Always)

    else -> equipment.iconRes(doubleBell = doubleBell || doubles == Doubles.Always)
}
