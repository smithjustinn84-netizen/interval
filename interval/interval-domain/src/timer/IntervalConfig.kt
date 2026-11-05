package dev.justinsmith.interval.timer

import kotlin.math.abs

/**
 * One workout's shape. Every duration is in whole seconds, and each value is in its range (`PREP_RANGE` and so
 * on), or it can't be made: one built from what was stored is made with [sanitized].
 */
data class IntervalConfig(
    val prepSeconds: Int = 10,
    val workSeconds: Int = 30,
    val restSeconds: Int = 15,
    val rounds: Int = 8,
    /** An easier stretch before the first round, 0 for none. Part of the shape, unlike [prepSeconds]. */
    val warmUpSeconds: Int = 0,
    /** An easier stretch after the last round, 0 for none. */
    val coolDownSeconds: Int = 0,
    /** What the work intervals are spent doing, for the calorie estimate. Not part of the [sameShape]. */
    val activity: Activity = Activity.General,
    /** How many times the rounds run, 1 for a workout of one set. */
    val sets: Int = 1,
    /** The longer rest after every set but the last, 0 for an ordinary rest between the sets. */
    val setRestSeconds: Int = 0,
    /**
     * A ladder: the seconds each round works more than the one before (less when below 0), starting over with
     * each set, so round r works [workOf] it. 0 is every round alike.
     */
    val workStepSeconds: Int = 0,
    /**
     * The blocks after the first, which this config's own work, rest, rounds, sets and ladder are: a sequence, run one
     * block after another. Empty for a workout of one block.
     */
    val then: List<Block> = emptyList(),
    /** The rest between one block and the next, 0 for the block's ordinary rest. */
    val blockRestSeconds: Int = 0,
) {
    init {
        require(prepSeconds in PREP_RANGE) { "Get ready of $prepSeconds s" }
        require(workSeconds in WORK_RANGE) { "Work of $workSeconds s" }
        require(restSeconds in REST_RANGE) { "Rest of $restSeconds s" }
        require(rounds in ROUNDS_RANGE) { "$rounds rounds" }
        require(warmUpSeconds in WARM_UP_RANGE) { "Warm-up of $warmUpSeconds s" }
        require(coolDownSeconds in COOL_DOWN_RANGE) { "Cool-down of $coolDownSeconds s" }
        require(sets in SETS_RANGE) { "$sets sets" }
        require(setRestSeconds in SET_REST_RANGE) { "Set rest of $setRestSeconds s" }
        require(workStepSeconds in WORK_STEP_RANGE) { "Ladder step of $workStepSeconds s" }
        require(workOf(rounds) in WORK_RANGE) { "Last round's work of ${workOf(rounds)} s" }
        require(then.size < MAX_BLOCKS) { "${then.size + 1} blocks" }
        require(blockRestSeconds in BLOCK_REST_RANGE) { "Block rest of $blockRestSeconds s" }
    }

    /**
     * The blocks of rounds between the bookends, in the order they run: this config's own work, rest, rounds, sets
     * and ladder as the first, then [then]. Worked out on each read, as [init] reads it.
     */
    val blocks: List<Block>
        get() = listOf(Block(workSeconds, restSeconds, rounds, sets, setRestSeconds, workStepSeconds)) + then

    /** A workout of more than one block. */
    val isSequence: Boolean get() = then.isNotEmpty()

    /**
     * This config with [blocks] in place of its own: the first in its fields, the others [then]. One block keeps no
     * rest between blocks.
     */
    fun withBlocks(blocks: List<Block>): IntervalConfig {
        val first = blocks.first()
        return copy(
            workSeconds = first.workSeconds,
            restSeconds = first.restSeconds,
            rounds = first.rounds,
            sets = first.sets,
            setRestSeconds = first.setRestSeconds,
            workStepSeconds = first.workStepSeconds,
            then = blocks.drop(1),
            blockRestSeconds = if (blocks.size > 1) blockRestSeconds else 0,
        )
    }

    /** [interval] is the rest between one block and the next: a set rest after the last set of a block not the last. */
    fun isBlockRest(interval: Interval): Boolean =
        interval.phase == Phase.SetRest && interval.block < then.size && interval.set >= blocks[interval.block].sets

    /** A ladder: the rounds' work climbs or falls. */
    val isLadder: Boolean get() = blocks.first().isLadder

    /** The work of [round] (1-based) of each set of the first block, as [Block.workOf]. */
    fun workOf(round: Int): Int = blocks.first().workOf(round)

    /**
     * Wall-clock length of the whole session: prep, the warm-up, every work interval and the rests between
     * them, the rests between the sets, and the cool-down; its [Course]'s sum.
     */
    val totalSeconds: Int
        get() = Course.of(this).sumOf { it.seconds }

    companion object {
        val PREP_RANGE = 0..600
        val WORK_RANGE = 5..3600
        val REST_RANGE = 0..3600
        val ROUNDS_RANGE = 1..99
        val WARM_UP_RANGE = 0..3600
        val COOL_DOWN_RANGE = 0..3600
        val SETS_RANGE = 1..20
        val SET_REST_RANGE = 0..3600
        val WORK_STEP_RANGE = -300..300
        val BLOCK_REST_RANGE = 0..3600

        /** The most blocks a sequence runs. */
        const val MAX_BLOCKS = 10

        /**
         * A config of these values, each taken to the nearest in its range: for one read back from storage,
         * which a damaged file or an older build could have left out of range, and which must still read.
         */
        fun sanitized(
            prepSeconds: Int = 10,
            workSeconds: Int = 30,
            restSeconds: Int = 15,
            rounds: Int = 8,
            warmUpSeconds: Int = 0,
            coolDownSeconds: Int = 0,
            activity: Activity = Activity.General,
            sets: Int = 1,
            setRestSeconds: Int = 0,
            workStepSeconds: Int = 0,
            then: List<Block> = emptyList(),
            blockRestSeconds: Int = 0,
        ): IntervalConfig {
            val first = Block.sanitized(workSeconds, restSeconds, rounds, sets, setRestSeconds, workStepSeconds)
            return IntervalConfig(
                prepSeconds = prepSeconds.coerceIn(PREP_RANGE),
                warmUpSeconds = warmUpSeconds.coerceIn(WARM_UP_RANGE),
                coolDownSeconds = coolDownSeconds.coerceIn(COOL_DOWN_RANGE),
                activity = activity,
                blockRestSeconds = blockRestSeconds.coerceIn(BLOCK_REST_RANGE),
            ).withBlocks(listOf(first) + then.take(MAX_BLOCKS - 1))
        }

        // The one-tap values the edit sheet offers for each field.
        val PREP_PICKS = listOf(0, 5, 10, 15, 20, 30, 45, 60)
        val WORK_PICKS = listOf(10, 20, 30, 45, 60, 90, 120, 180)
        val REST_PICKS = listOf(0, 10, 15, 20, 30, 45, 60, 90)
        val ROUNDS_PICKS = listOf(4, 6, 8, 10, 12, 15, 20, 30)
        val BOOKEND_PICKS = listOf(0, 60, 120, 180, 300, 480, 600, 900)
        val SETS_PICKS = listOf(2, 3, 4, 5)
        val SET_REST_PICKS = listOf(30, 60, 90, 120, 180)
        val WORK_STEP_PICKS = listOf(-10, -5, 5, 10, 15, 30)

        /** The rest between sets a workout of sets is given when nothing names one: a minute. */
        const val DEFAULT_SET_REST_SECONDS = 60
    }
}

/** Which value a stepper or edit sheet changes. [quickPicks] are the common values offered as one-tap choices. */
enum class ConfigField(val step: Int, val range: IntRange, val quickPicks: List<Int>) {
    Prep(step = 5, range = IntervalConfig.PREP_RANGE, quickPicks = IntervalConfig.PREP_PICKS),
    Work(step = 5, range = IntervalConfig.WORK_RANGE, quickPicks = IntervalConfig.WORK_PICKS),
    Rest(step = 5, range = IntervalConfig.REST_RANGE, quickPicks = IntervalConfig.REST_PICKS),
    Rounds(step = 1, range = IntervalConfig.ROUNDS_RANGE, quickPicks = IntervalConfig.ROUNDS_PICKS),
    WarmUp(step = 30, range = IntervalConfig.WARM_UP_RANGE, quickPicks = IntervalConfig.BOOKEND_PICKS),
    CoolDown(step = 30, range = IntervalConfig.COOL_DOWN_RANGE, quickPicks = IntervalConfig.BOOKEND_PICKS),
    Sets(step = 1, range = IntervalConfig.SETS_RANGE, quickPicks = IntervalConfig.SETS_PICKS),
    SetRest(step = 15, range = IntervalConfig.SET_REST_RANGE, quickPicks = IntervalConfig.SET_REST_PICKS),
    WorkStep(step = 5, range = IntervalConfig.WORK_STEP_RANGE, quickPicks = IntervalConfig.WORK_STEP_PICKS),
    BlockRest(step = 15, range = IntervalConfig.BLOCK_REST_RANGE, quickPicks = IntervalConfig.SET_REST_PICKS),
    ;

    companion object {
        /** The fields the Timer and the editor list as their rows. Get ready is a setting. */
        val shape: List<ConfigField> = listOf(Work, Rest, Rounds)

        /** The ladder, offered under the rows: how much each round's work climbs or falls; 0 is off. */
        val ladder: List<ConfigField> = listOf(WorkStep)

        /** The rest of a preset's shape, either side of the rounds, offered under the rows; 0 is off. */
        val bookends: List<ConfigField> = listOf(WarmUp, CoolDown)

        /**
         * The rows under the shape for its sets: how many, and once there are more than one the rest between them.
         */
        fun setsOf(config: IntervalConfig): List<ConfigField> = setsOf(config.blocks.first())

        /** As [setsOf] a config, for one of its blocks. */
        fun setsOf(block: Block): List<ConfigField> = if (block.sets > 1) listOf(Sets, SetRest) else listOf(Sets)

        /** The rows of one block of a sequence: its shape, its ladder and its sets. */
        fun blockFields(block: Block): List<ConfigField> = shape + ladder + setsOf(block)
    }
}

/** The current value of [field]. */
fun IntervalConfig.valueOf(field: ConfigField): Int = when (field) {
    ConfigField.Prep -> prepSeconds
    ConfigField.Work -> workSeconds
    ConfigField.Rest -> restSeconds
    ConfigField.Rounds -> rounds
    ConfigField.WarmUp -> warmUpSeconds
    ConfigField.CoolDown -> coolDownSeconds
    ConfigField.Sets -> sets
    ConfigField.SetRest -> setRestSeconds
    ConfigField.WorkStep -> workStepSeconds
    ConfigField.BlockRest -> blockRestSeconds
}

/** The current value of [field] in [block] (0-based); a field of the whole workout, such as the warm-up, as it is. */
fun IntervalConfig.valueOf(field: ConfigField, block: Int): Int =
    if (block == 0 || field !in Block.FIELDS) valueOf(field) else blocks[block].valueOf(field)

/** [withValue], in [block] (0-based) for a field of a block's own. */
fun IntervalConfig.withValue(field: ConfigField, value: Int, block: Int): IntervalConfig =
    if (block == 0 || field !in Block.FIELDS) {
        withValue(field, value)
    } else {
        withBlocks(blocks.toMutableList().also { it[block] = it[block].withValue(field, value) })
    }

/** [stepped], in [block] (0-based) for a field of a block's own. */
fun IntervalConfig.stepped(field: ConfigField, direction: Int, block: Int): IntervalConfig =
    withValue(field, valueOf(field, block) + direction * field.step, block)

/** A block more at the end, as the last one is, so it reads as more of the same; none past the most, MAX_BLOCKS. */
fun IntervalConfig.withBlockAdded(): IntervalConfig =
    if (blocks.size >= IntervalConfig.MAX_BLOCKS) this else withBlocks(blocks + blocks.last())

/** Without [block] (0-based); the last one left stays. */
fun IntervalConfig.withoutBlock(block: Int): IntervalConfig =
    if (blocks.size <= 1) this else withBlocks(blocks.filterIndexed { index, _ -> index != block })

/** [block] (0-based) moved [by] places, -1 earlier or +1 later, as far as the ends allow. */
fun IntervalConfig.withBlockMoved(block: Int, by: Int): IntervalConfig {
    val to = (block + by).coerceIn(blocks.indices)
    if (to == block) return this
    val moved = blocks.toMutableList()
    moved.add(to, moved.removeAt(block))
    return withBlocks(moved)
}

/**
 * Sets [field] to [value], clamped to the field's range. A ladder's step is shortened when the change would take
 * its last round's work out of range (as [IntervalConfig.sanitized] does), so less work or more rounds on a
 * falling ladder fall less steeply rather than fail.
 */
fun IntervalConfig.withValue(field: ConfigField, value: Int): IntervalConfig {
    fun of(own: ConfigField, current: Int) = if (own == field) value else current
    return IntervalConfig.sanitized(
        prepSeconds = of(ConfigField.Prep, prepSeconds),
        workSeconds = of(ConfigField.Work, workSeconds),
        restSeconds = of(ConfigField.Rest, restSeconds),
        rounds = of(ConfigField.Rounds, rounds),
        warmUpSeconds = of(ConfigField.WarmUp, warmUpSeconds),
        coolDownSeconds = of(ConfigField.CoolDown, coolDownSeconds),
        activity = activity,
        sets = of(ConfigField.Sets, sets),
        setRestSeconds = of(ConfigField.SetRest, setRestSeconds),
        workStepSeconds = of(ConfigField.WorkStep, workStepSeconds),
        then = then,
        blockRestSeconds = of(ConfigField.BlockRest, blockRestSeconds),
    )
}

/** A count, the rounds or the sets, rather than a duration. */
val ConfigField.isCount: Boolean get() = this == ConfigField.Rounds || this == ConfigField.Sets

/** "0:30" for durations, "8" for rounds and sets, and a ladder's step with its sign: "+0:10", "−0:05". */
fun ConfigField.format(value: Int): String = when {
    isCount -> value.toString()
    this == ConfigField.WorkStep && value != 0 -> (if (value > 0) "+" else "\u2212") + formatClock(abs(value))
    else -> formatClock(value)
}

/**
 * Same work, rest and rounds, ladder, warm-up and cool-down, sets and the rest between them, the blocks after the
 * first and the rest between blocks. The get-ready countdown is
 * an app setting rather than part of a workout's shape, so presets match and apply regardless of it.
 */
fun IntervalConfig.sameShape(other: IntervalConfig): Boolean =
    workSeconds == other.workSeconds && restSeconds == other.restSeconds && rounds == other.rounds &&
        warmUpSeconds == other.warmUpSeconds && coolDownSeconds == other.coolDownSeconds &&
        sets == other.sets && setRestSeconds == other.setRestSeconds && workStepSeconds == other.workStepSeconds &&
        then == other.then && blockRestSeconds == other.blockRestSeconds

/** Moves [field] one step in [direction] (+1 or -1), clamped to the field's range. */
fun IntervalConfig.stepped(field: ConfigField, direction: Int): IntervalConfig =
    withValue(field, valueOf(field) + direction * field.step)
