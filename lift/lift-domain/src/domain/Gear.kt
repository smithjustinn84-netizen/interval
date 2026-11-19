package dev.justinsmith.lift.domain

/**
 * What the lifter has to train with: the [bells], by weight, those of them owned two of ([pairs]), the [bar]'s
 * weight (null for none) and the [bands]. What a step up, or an easier day, can go to.
 */
data class Gear(
    val bells: List<Double> = emptyList(),
    val pairs: Set<Double> = emptySet(),
    val bar: Double? = null,
    val bands: List<Band> = emptyList(),
)
