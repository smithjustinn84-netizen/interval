package dev.justinsmith.interval.timer

/**
 * One block of a workout's rounds: work and rest for so many rounds, run in sets with a longer rest between them,
 * each round's work a ladder's step on from the one before. A workout is its bookends around its blocks
 * ([IntervalConfig.blocks]), run one after another. Whole seconds, each value in [IntervalConfig]'s ranges, or it
 * can't be made: one built from what was stored is made with [sanitized].
 */
data class Block(
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
    val sets: Int = 1,
    /** The longer rest after every set but the last, 0 for an ordinary rest between the sets. */
    val setRestSeconds: Int = 0,
    /** The seconds each round works more than the one before (less when below 0), starting over with each set. */
    val workStepSeconds: Int = 0,
) {
    init {
        require(workSeconds in IntervalConfig.WORK_RANGE) { "Work of $workSeconds s" }
        require(restSeconds in IntervalConfig.REST_RANGE) { "Rest of $restSeconds s" }
        require(rounds in IntervalConfig.ROUNDS_RANGE) { "$rounds rounds" }
        require(sets in IntervalConfig.SETS_RANGE) { "$sets sets" }
        require(setRestSeconds in IntervalConfig.SET_REST_RANGE) { "Set rest of $setRestSeconds s" }
        require(workStepSeconds in IntervalConfig.WORK_STEP_RANGE) { "Ladder step of $workStepSeconds s" }
        require(workOf(rounds) in IntervalConfig.WORK_RANGE) { "Last round's work of ${workOf(rounds)} s" }
    }

    /** A ladder: the rounds' work climbs or falls. */
    val isLadder: Boolean get() = workStepSeconds != 0 && rounds > 1

    /** The work of [round] (1-based) of each set: [workSeconds], and a [workStepSeconds] more each round after. */
    fun workOf(round: Int): Int = workSeconds + (round - 1) * workStepSeconds

    /** The value of [field], one of [FIELDS]. */
    fun valueOf(field: ConfigField): Int = when (field) {
        ConfigField.Work -> workSeconds
        ConfigField.Rest -> restSeconds
        ConfigField.Rounds -> rounds
        ConfigField.Sets -> sets
        ConfigField.SetRest -> setRestSeconds
        ConfigField.WorkStep -> workStepSeconds
        else -> error("$field is not a block's")
    }

    /** [field], one of [FIELDS], at [value], taken into its range as [IntervalConfig.withValue] takes it. */
    fun withValue(field: ConfigField, value: Int): Block {
        fun of(own: ConfigField, current: Int) = if (own == field) value else current
        return sanitized(
            workSeconds = of(ConfigField.Work, workSeconds),
            restSeconds = of(ConfigField.Rest, restSeconds),
            rounds = of(ConfigField.Rounds, rounds),
            sets = of(ConfigField.Sets, sets),
            setRestSeconds = of(ConfigField.SetRest, setRestSeconds),
            workStepSeconds = of(ConfigField.WorkStep, workStepSeconds),
        )
    }

    companion object {
        /** The fields a block has of its own; the rest are the whole workout's. */
        val FIELDS: Set<ConfigField> = setOf(
            ConfigField.Work,
            ConfigField.Rest,
            ConfigField.Rounds,
            ConfigField.Sets,
            ConfigField.SetRest,
            ConfigField.WorkStep,
        )

        /**
         * A block of these values, each taken to the nearest in its range, the ladder's step shortened so its last
         * round fits: for one read back from storage, which must still read.
         */
        fun sanitized(
            workSeconds: Int,
            restSeconds: Int,
            rounds: Int,
            sets: Int = 1,
            setRestSeconds: Int = 0,
            workStepSeconds: Int = 0,
        ): Block {
            val work = workSeconds.coerceIn(IntervalConfig.WORK_RANGE)
            val count = rounds.coerceIn(IntervalConfig.ROUNDS_RANGE)
            return Block(
                workSeconds = work,
                restSeconds = restSeconds.coerceIn(IntervalConfig.REST_RANGE),
                rounds = count,
                sets = sets.coerceIn(IntervalConfig.SETS_RANGE),
                setRestSeconds = setRestSeconds.coerceIn(IntervalConfig.SET_REST_RANGE),
                workStepSeconds = fittedStep(workStepSeconds.coerceIn(IntervalConfig.WORK_STEP_RANGE), work, count),
            )
        }

        /**
         * [step] as a ladder of [rounds] from [work] can take it: itself while the last round's work stays in
         * [IntervalConfig.WORK_RANGE], else shortened toward 0, to a multiple of [ConfigField.WorkStep]'s step,
         * until it does.
         */
        private fun fittedStep(step: Int, work: Int, rounds: Int): Int {
            val range = IntervalConfig.WORK_RANGE
            if (rounds <= 1 || work + (rounds - 1) * step in range) return step
            val room = if (step > 0) range.last - work else work - range.first
            val most = room / (rounds - 1)
            val fitted = most - most % ConfigField.WorkStep.step
            return if (step > 0) fitted else -fitted
        }
    }
}
