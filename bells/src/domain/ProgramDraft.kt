package dev.justinsmith.bells.domain

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
        val NEW_BLOCK = Block(Exercise.TwoHandSwing, rounds = 5, reps = 10)

        /** The rungs a block gets on becoming a ladder. */
        val NEW_RUNGS = listOf(1, 2, 3)

        /** Reps a set gets when a ladder becomes sets or an EMOM. */
        const val NEW_REPS = 5

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
)
