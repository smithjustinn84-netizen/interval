package dev.justinsmith.interval.timer

/**
 * A flow as the user lays it out, before it is saved: its [name], when it is for ([use]), an optional [note]
 * ("no bell"), and its [moves], none to begin with. [id] is the stored flow it edits; null for a new one.
 */
data class FlowDraft(
    val name: String = "",
    val use: FlowUse = FlowUse.After,
    val note: String = "",
    val moves: List<Move> = emptyList(),
    val id: Long? = null,
) {
    /** Whether it can take another move. */
    val roomForMore: Boolean get() = moves.size < Flow.MAX_MOVES

    /** The flow it saves as, under its [id] if it has one; null while it has no move or no name. */
    fun toFlow(): Flow? {
        val named = name.trim().take(Flow.NAME_MAX_LENGTH)
        if (named.isEmpty() || moves.isEmpty() || moves.size > Flow.MAX_MOVES) return null
        if (moves.any { it.name.isBlank() }) return null
        return Flow(
            key = id?.let(::ownFlowKey) ?: NEW_KEY,
            name = named,
            use = use,
            moves = moves,
            note = note.trim().take(Flow.NOTE_MAX_LENGTH).ifEmpty { null },
            id = id,
        )
    }

    /**
     * What already has this draft's shape, which a finished workout is named by: one of [flows] other than the
     * flow it edits, else one of [presets]. Saving it would make the two one, so it is refused while there is one.
     */
    fun clash(flows: List<Flow>, presets: List<Preset>): Clash? {
        val config = toFlow()?.config ?: return null
        val others = flows.filterNot { id != null && it.id == id }
        val flow = others.matching(config)
        return if (flow != null) Clash(flow.name) else presets.matching(config)?.let { Clash(it.name) }
    }

    /** The draft with [move] at the end. */
    fun adding(move: Move): FlowDraft = if (roomForMore) copy(moves = moves + move) else this

    /** The draft with the move at [index] made [move]. */
    fun replacing(index: Int, move: Move): FlowDraft =
        if (index in moves.indices) copy(moves = moves.toMutableList().apply { set(index, move) }) else this

    /** The draft without the move at [index]. */
    fun removing(index: Int): FlowDraft =
        if (index in moves.indices) copy(moves = moves.filterIndexed { i, _ -> i != index }) else this

    /** The draft with the move at [index] one place [by] (-1 up, 1 down), if there is a place to go. */
    fun moving(index: Int, by: Int): FlowDraft {
        val to = index + by
        if (index !in moves.indices || to !in moves.indices) return this
        return copy(moves = moves.toMutableList().apply { add(to, removeAt(index)) })
    }

    companion object {
        /** A copy of [flow] for the user to change, under [name] ("Hips and shoulders (yours)"). */
        fun copyOf(flow: Flow, name: String): FlowDraft =
            FlowDraft(name.take(Flow.NAME_MAX_LENGTH), flow.use, flow.note.orEmpty(), flow.moves)

        /** [flow], one of the user's own, to edit. */
        fun of(flow: Flow): FlowDraft = FlowDraft(flow.name, flow.use, flow.note.orEmpty(), flow.moves, flow.id)

        private const val NEW_KEY = "own-new"
    }
}

/** What a draft would share its shape with: the flow or preset of [name]. */
data class Clash(val name: String)

/** [move]'s seconds moved [by] steps of [Move.SECONDS_STEP], within [Move.SECONDS_RANGE]. */
fun Move.steppedSeconds(by: Int): Move =
    copy(seconds = (seconds + by * Move.SECONDS_STEP).coerceIn(Move.SECONDS_RANGE))
