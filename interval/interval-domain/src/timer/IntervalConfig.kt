package dev.justinsmith.interval.timer

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
) {
    init {
        require(prepSeconds in PREP_RANGE) { "Get ready of $prepSeconds s" }
        require(workSeconds in WORK_RANGE) { "Work of $workSeconds s" }
        require(restSeconds in REST_RANGE) { "Rest of $restSeconds s" }
        require(rounds in ROUNDS_RANGE) { "$rounds rounds" }
        require(warmUpSeconds in WARM_UP_RANGE) { "Warm-up of $warmUpSeconds s" }
        require(coolDownSeconds in COOL_DOWN_RANGE) { "Cool-down of $coolDownSeconds s" }
    }

    /**
     * Wall-clock length of the whole session: prep, the warm-up, every work interval and the rests between
     * them, and the cool-down; its [Course]'s sum.
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
        ) = IntervalConfig(
            prepSeconds = prepSeconds.coerceIn(PREP_RANGE),
            workSeconds = workSeconds.coerceIn(WORK_RANGE),
            restSeconds = restSeconds.coerceIn(REST_RANGE),
            rounds = rounds.coerceIn(ROUNDS_RANGE),
            warmUpSeconds = warmUpSeconds.coerceIn(WARM_UP_RANGE),
            coolDownSeconds = coolDownSeconds.coerceIn(COOL_DOWN_RANGE),
            activity = activity,
        )

        // The one-tap values the edit sheet offers for each field.
        val PREP_PICKS = listOf(0, 5, 10, 15, 20, 30, 45, 60)
        val WORK_PICKS = listOf(10, 20, 30, 45, 60, 90, 120, 180)
        val REST_PICKS = listOf(0, 10, 15, 20, 30, 45, 60, 90)
        val ROUNDS_PICKS = listOf(4, 6, 8, 10, 12, 15, 20, 30)
        val BOOKEND_PICKS = listOf(0, 60, 120, 180, 300, 480, 600, 900)
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
    ;

    companion object {
        /** The fields the Timer and the editor list as their rows. Get ready is a setting. */
        val shape: List<ConfigField> = listOf(Work, Rest, Rounds)

        /** The rest of a preset's shape, either side of the rounds, offered under the rows; 0 is off. */
        val bookends: List<ConfigField> = listOf(WarmUp, CoolDown)
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
}

/** Sets [field] to [value], clamped to the field's range. */
fun IntervalConfig.withValue(field: ConfigField, value: Int): IntervalConfig {
    val clamped = value.coerceIn(field.range)
    return when (field) {
        ConfigField.Prep -> copy(prepSeconds = clamped)
        ConfigField.Work -> copy(workSeconds = clamped)
        ConfigField.Rest -> copy(restSeconds = clamped)
        ConfigField.Rounds -> copy(rounds = clamped)
        ConfigField.WarmUp -> copy(warmUpSeconds = clamped)
        ConfigField.CoolDown -> copy(coolDownSeconds = clamped)
    }
}

/** "0:30" for durations, "8" for rounds. */
fun ConfigField.format(value: Int): String = if (this == ConfigField.Rounds) value.toString() else formatClock(value)

/**
 * Same work, rest and rounds, warm-up and cool-down. The get-ready countdown is an app setting rather than
 * part of a workout's shape, so presets match and apply regardless of it.
 */
fun IntervalConfig.sameShape(other: IntervalConfig): Boolean =
    workSeconds == other.workSeconds && restSeconds == other.restSeconds && rounds == other.rounds &&
        warmUpSeconds == other.warmUpSeconds && coolDownSeconds == other.coolDownSeconds

/** Moves [field] one step in [direction] (+1 or -1), clamped to the field's range. */
fun IntervalConfig.stepped(field: ConfigField, direction: Int): IntervalConfig =
    withValue(field, valueOf(field) + direction * field.step)
