package dev.justinsmith.lift.domain

/** A program the user is making or changing: what the editor holds until it is saved. */
data class ProgramDraft(
    val name: String = "",
    val days: List<List<Block>> = listOf(emptyList()),
    val daysPerWeek: Int = DEFAULT_DAYS_PER_WEEK,
    val weeks: Int = DEFAULT_WEEKS,
) {
    /** A name, and a block on every day: what a [Program] needs. */
    val canSave: Boolean get() = name.isNotBlank() && days.isNotEmpty() && days.none { it.isEmpty() }

    /** With an empty day at the end, up to [MAX_DAYS]. */
    fun addDay(): ProgramDraft = if (days.size < MAX_DAYS) copy(days = days + listOf(emptyList())) else this

    /** Without day [day]; the last day stays. */
    fun removeDay(day: Int): ProgramDraft =
        if (days.size > 1) copy(days = days.filterIndexed { i, _ -> i != day }) else this

    /** With [block] at the end of day [day]. */
    fun addBlock(day: Int, block: Block = NEW_BLOCK): ProgramDraft = mapDay(day) { it + block }

    /** With block [index] of day [day] changed by [transform]. */
    fun changeBlock(day: Int, index: Int, transform: (Block) -> Block): ProgramDraft =
        mapDay(day) { blocks -> blocks.mapIndexed { i, b -> if (i == index) transform(b) else b } }

    fun removeBlock(day: Int, index: Int): ProgramDraft =
        mapDay(day) { blocks -> blocks.filterIndexed { i, _ -> i != index } }

    private fun mapDay(day: Int, transform: (List<Block>) -> List<Block>) =
        copy(days = days.mapIndexed { i, blocks -> if (i == day) transform(blocks) else blocks })

    companion object {
        const val DEFAULT_DAYS_PER_WEEK = 3
        const val DEFAULT_WEEKS = 4
        const val MAX_DAYS_PER_WEEK = 7
        const val MAX_WEEKS = 52
        const val MAX_DAYS = 7
        const val MAX_NAME_LENGTH = 40
        const val MAX_ROUNDS = 60
        const val MAX_RUNGS = 10

        /** A block as one is added: 5 sets of 10 two-hand swings. */
        val NEW_BLOCK = Block(BuiltInExercise.TwoHandSwing, rounds = 5, reps = 10)

        /** The rungs a block gets on becoming a ladder. */
        val NEW_RUNGS = listOf(1, 2, 3)

        /** Reps a set gets when a ladder becomes sets or an EMOM. */
        const val NEW_REPS = 5

        /** How far a range climbs when one is turned on, from the reps a block had. */
        const val RANGE_SPAN = 5

        /** Seconds a hold gets when a block becomes one. */
        const val NEW_SECONDS = 30

        /** [program]'s draft, to change it. */
        fun of(program: Program): ProgramDraft =
            ProgramDraft(program.name.orEmpty(), program.days, program.daysPerWeek, program.weeks)
    }
}

/** This block run as [kind]: a ladder gets rungs and sets or an EMOM reps if it had none. */
fun Block.asKind(kind: BlockKind): Block = copy(
    kind = kind,
    rungs = if (kind == BlockKind.Ladder && rungs.isEmpty()) ProgramDraft.NEW_RUNGS else rungs,
    reps = if (kind != BlockKind.Ladder && reps < 1) ProgramDraft.NEW_REPS else reps,
    range = range.takeIf { kind != BlockKind.Ladder },
)

/**
 * This block of [exercise]: still with two bells if it was and [exercise] can be, and with them if it must. Its
 * load stays while the equipment does; a hold is only ever sets, and a change between reps and time starts the
 * count afresh.
 */
fun Block.withExercise(exercise: Exercise): Block {
    val sameLoad = exercise.equipment == this.exercise.equipment
    val sameMeasure = exercise.measure == this.exercise.measure
    val held = exercise.measure == Measure.Time
    return copy(
        exercise = exercise,
        double = exercise.doubled(double),
        kind = if (held) BlockKind.Sets else kind,
        reps = when {
            sameMeasure -> reps
            held -> ProgramDraft.NEW_SECONDS
            else -> ProgramDraft.NEW_REPS
        },
        kg = kg.takeIf { sameLoad },
        band = band.takeIf { sameLoad },
        range = range.takeIf { !held },
    )
}

/** This block building within a range from its reps, up to [maxReps]; or, [on] false, at fixed reps again. */
fun Block.withRange(on: Boolean, maxReps: Int): Block {
    if (!on) return copy(range = null)
    val bottom = reps.coerceIn(1, maxReps - 1)
    return withRange(RepRange(bottom, (bottom + ProgramDraft.RANGE_SPAN).coerceAtMost(maxReps)))
}

/** This block building within [range], its reps kept within it. */
fun Block.withRange(range: RepRange): Block = copy(range = range, reps = reps.coerceIn(range.bottom, range.top))
