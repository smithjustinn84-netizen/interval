package dev.justinsmith.bells.domain

/**
 * The load one [step] (−1 lighter, +1 heavier) from [kg] takes a lift done with [equipment] to, among what the
 * user can load: the next of their [bells], the bar a [BAR_KG_STEP] either way but never below the empty [bar],
 * or weight added to bodyweight (none, then a bell's weight). A weight between two bells goes to the next one
 * that way. Null at either end, and for a band or the mat: a band steps by [steppedBand].
 */
fun steppedKg(equipment: Equipment, kg: Double, step: Int, bells: List<Double>, bar: Double?): Double? =
    when (equipment) {
        Equipment.Kettlebell -> nextOf(bells, kg, step)
        Equipment.Bodyweight -> nextOf(listOf(0.0) + bells, kg, step)
        Equipment.Barbell -> (kg + step * BAR_KG_STEP).takeIf { it >= (bar ?: 0.0) && it <= MAX_BAR_KG }
        Equipment.Band, Equipment.Mat -> null
    }

/** The band one [step] from [band] among [offered], lightest first: null at either end, or with none to step from. */
fun steppedBand(band: Band?, step: Int, offered: List<Band>): Band? {
    val at = offered.indexOfFirst { it.id == band?.id }
    return if (at < 0) null else offered.getOrNull(at + step)
}

private fun nextOf(options: List<Double>, kg: Double, step: Int): Double? {
    val sorted = options.distinct().sorted()
    return if (step > 0) sorted.firstOrNull { it > kg } else sorted.lastOrNull { it < kg }
}
