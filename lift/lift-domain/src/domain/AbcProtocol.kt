package dev.justinsmith.lift.domain

/**
 * The Armor Building Complex's two protocols: a complex [OnTheMinute] (an EMOM), or [AsManyAsYouCan] in a time cap
 * (a density block). A program plans one; today's plan can take the other for the same minutes.
 */
enum class AbcProtocol { OnTheMinute, AsManyAsYouCan }

/** The ABC protocol this block runs, or null for a block of anything else, or of sets or a ladder. */
val Block.abcProtocol: AbcProtocol?
    get() = when {
        exercise != BuiltInExercise.ArmorBuildingComplex -> null
        kind == BlockKind.Emom -> AbcProtocol.OnTheMinute
        kind == BlockKind.Density -> AbcProtocol.AsManyAsYouCan
        else -> null
    }

/**
 * This ABC block run the other way for the same minutes: on the minute as many as you can, or as many as you can
 * back on the minute (its cap kept within a density block's, [DensityClock.MIN_MINUTES] to
 * [DensityClock.MAX_MINUTES]); null for any block with no other protocol.
 */
fun Block.otherAbcProtocol(): Block? = when (abcProtocol) {
    AbcProtocol.OnTheMinute -> asKind(BlockKind.Density)
    AbcProtocol.AsManyAsYouCan -> asKind(BlockKind.Emom)
    null -> null
}

/**
 * The protocol [session] ran its ABC by: its plan as changed, else its program's day as planned, found among
 * [programs]; null for a free session, or a day with no ABC on the minute or in a cap.
 */
internal fun abcProtocolOf(session: LiftSession, programs: List<Program>): AbcProtocol? {
    val blocks = session.plan?.changedBlocks ?: run {
        val program = programs.firstOrNull { it.key == session.program } ?: return null
        program.day(session.day ?: return null).blocks
    }
    return blocks.firstNotNullOfOrNull { it.abcProtocol }
}
