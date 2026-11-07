package dev.justinsmith.interval.model

/**
 * About how many snatches a set the lifter did in a workout of kettlebell intervals that switch hands (15:15 MVO2,
 * or one of their own with the switch on), and the bell, as they said on its summary or its page in the Log. It is
 * an estimate, never a default: a workout no one counted has none. Lift reads it, through `IntervalActivity`, as
 * a set of [perSet] snatches at [bellKg] for each work interval done.
 */
data class SnatchCount(val perSet: Int, val bellKg: Double) {
    init {
        require(perSet in MIN_PER_SET..MAX_PER_SET) { "perSet $perSet is outside $MIN_PER_SET..$MAX_PER_SET" }
        require(bellKg > 0 && bellKg <= MAX_BELL_KG) { "bellKg $bellKg is outside (0, $MAX_BELL_KG]" }
    }

    companion object {
        const val MIN_PER_SET = 1
        const val MAX_PER_SET = 20

        /** The count the question opens on with none said before: about eight in fifteen seconds. */
        const val USUAL_PER_SET = 8

        /** The bell it opens on with none owned and none used before. */
        const val USUAL_BELL_KG = 16.0

        /** The heaviest bell a count may name. */
        const val MAX_BELL_KG = 100.0

        /** The count stored as these two columns; null unless both are there and make sense. */
        fun of(perSet: Int?, bellKg: Double?): SnatchCount? {
            if (perSet == null || bellKg == null) return null
            if (perSet !in MIN_PER_SET..MAX_PER_SET || bellKg <= 0 || bellKg > MAX_BELL_KG) return null
            return SnatchCount(perSet, bellKg)
        }
    }
}
