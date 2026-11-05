package dev.justinsmith.lift.domain

/**
 * What a set is done with, and how heavy: a bell or a pair of them, a bar as loaded, weight added to
 * bodyweight, a band, or the mat, which takes nothing. A new kind of equipment is a case here; every `when`
 * over it then says where it needs rules or words of its own.
 */
sealed interface Load {
    /** The kind of equipment it is. */
    val equipment: Equipment

    /** Its weight as a set records it: the bell, the total on the bar, the weight added; 0 for none. */
    val kg: Double

    /** The weight one rep moves: both bells of a pair; a band and the mat move none that can be weighed. */
    val movedKg: Double get() = kg

    /** A bell of [kg], or a pair of them when [double]. */
    data class Bell(override val kg: Double, val double: Boolean = false) : Load {
        override val equipment: Equipment get() = Equipment.Kettlebell
        override val movedKg: Double get() = if (double) kg * 2 else kg
    }

    /** A bar loaded to [kg] in all. */
    data class Bar(override val kg: Double) : Load {
        override val equipment: Equipment get() = Equipment.Barbell
    }

    /** Bodyweight with [kg] added; 0 for none. */
    data class Bodyweight(override val kg: Double = 0.0) : Load {
        override val equipment: Equipment get() = Equipment.Bodyweight
    }

    /** A band; null for a set that kept none. */
    data class Banded(val band: Band?) : Load {
        override val equipment: Equipment get() = Equipment.Band
        override val kg: Double get() = 0.0
        override val movedKg: Double get() = 0.0
    }

    data object Mat : Load {
        override val equipment: Equipment get() = Equipment.Mat
        override val kg: Double get() = 0.0
    }

    companion object {
        /**
         * The load done with [equipment] at [kg] (a bell's, the bar's total, the weight added), with two bells
         * when [double], or with [band]: what a set's stored parts make. The one place equipment becomes a load.
         */
        fun of(equipment: Equipment, kg: Double, double: Boolean = false, band: Band? = null): Load =
            when (equipment) {
                Equipment.Kettlebell -> Bell(kg, double)
                Equipment.Barbell -> Bar(kg)
                Equipment.Bodyweight -> Bodyweight(kg)
                Equipment.Band -> Banded(band)
                Equipment.Mat -> Mat
            }

        /**
         * The load a plan names for [equipment], from the parts it is stored as: [kg] for what is weighed (a
         * bell, or a pair when [double]; the bar's total; the weight added), [band] for band work. Null when it
         * names none, leaving it to what was used last time, and for the mat, which takes none.
         */
        fun named(equipment: Equipment, kg: Double?, double: Boolean = false, band: Band? = null): Load? =
            when (equipment) {
                Equipment.Kettlebell, Equipment.Barbell, Equipment.Bodyweight -> kg?.let { of(equipment, it, double) }
                Equipment.Band -> band?.let(::Banded)
                Equipment.Mat -> null
            }
    }
}

/**
 * [load] as this exercise takes it: of its equipment, with two bells only if it can be done so, and a band only
 * for band work. A weight carries over from one kind to another; a band or the mat has none.
 */
fun Exercise.taking(load: Load): Load = Load.of(equipment, load.kg, doubled(load.double), load.band)

/** Whether it is done with two bells. */
val Load.double: Boolean get() = this is Load.Bell && double

/** The band it is done with, if a band it is. */
val Load.band: Band? get() = (this as? Load.Banded)?.band

/** Its weight where it is weighed, as a plan stores it: none for a band or the mat. */
val Load.weighedKg: Double? get() = when (this) {
    is Load.Bell, is Load.Bar, is Load.Bodyweight -> kg
    is Load.Banded, Load.Mat -> null
}

/**
 * This load one [step] (−1 lighter, +1 heavier) among what the user can load: the next of their [bells], the
 * bar a [BAR_KG_STEP] either way but never below the empty [bar], weight added to bodyweight (none, then a
 * bell's weight), or the next of the [offered] bands. A weight between two bells goes to the next one that way.
 * Null at either end, and for the mat.
 */
fun Load.stepped(step: Int, bells: List<Double>, bar: Double?, offered: List<Band>): Load? = when (this) {
    is Load.Bell -> nextOf(bells, kg, step)?.let { copy(kg = it) }
    is Load.Bodyweight -> nextOf(listOf(0.0) + bells, kg, step)?.let { copy(kg = it) }
    is Load.Bar -> (kg + step * BAR_KG_STEP).takeIf { it >= (bar ?: 0.0) && it <= MAX_BAR_KG }?.let(Load::Bar)
    is Load.Banded -> steppedBand(band, step, offered)?.let(Load::Banded)
    Load.Mat -> null
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
