package dev.justinsmith.lift.domain

/**
 * How hard a finished session felt, as the user says on its summary: five words, stored as their [level], 1 to 5.
 * The interval side's workouts keep the same five (`dev.justinsmith.interval.model.PerceivedEffort`): the sides
 * share no domain.
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

    /** Whether a program day that felt this hard leaves its steps up off until asked for: very hard or more. */
    val holdsBackSteps: Boolean get() = this >= VeryHard

    companion object {
        /** The longest note a finished session keeps: a line, not a diary. */
        const val NOTE_MAX_LENGTH = 140

        /** The effort stored as [level]; null for none, or a level this build doesn't know. */
        fun of(level: Int?): PerceivedEffort? = level?.let { entries.getOrNull(it - 1) }
    }
}
