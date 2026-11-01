package dev.justinsmith.interval.model

/**
 * How hard a finished workout felt, as the user says on its summary: five words, stored as their [level], 1 to 5.
 * Lift's sessions keep the same five (`dev.justinsmith.lift.domain.PerceivedEffort`): the sides share no domain.
 */
enum class PerceivedEffort {
    Easy,
    Steady,
    Hard,
    VeryHard,
    AllOut,
    ;

    /** As stored: 1 for [Easy] up to 5 for [AllOut]. Their order is the scale's, so never reorder them. */
    val level: Int get() = ordinal + 1

    companion object {
        /** The longest note a finished workout keeps: a line, not a diary. */
        const val NOTE_MAX_LENGTH = 140

        /** The effort stored as [level]; null for none, or a level this build doesn't know. */
        fun of(level: Int?): PerceivedEffort? = level?.let { entries.getOrNull(it - 1) }
    }
}
