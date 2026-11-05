package dev.justinsmith.interval.data

import dev.justinsmith.interval.model.HistoryEntry
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.timer.Activity
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.interval.timer.Preset

// Between the stored rows and the plain models, the same for every repository: what Room keeps stays here.

/** The [IntervalConfig] a row's columns hold, the same in every table, each value read into its range. */
@Suppress("detekt.LongParameterList")
private fun intervalConfig(
    prepSeconds: Int,
    workSeconds: Int,
    restSeconds: Int,
    rounds: Int,
    warmUpSeconds: Int,
    coolDownSeconds: Int,
    activity: String?,
    sets: Int,
    setRestSeconds: Int,
    workStepSeconds: Int,
    thenBlocks: String,
    blockRestSeconds: Int,
) = IntervalConfig.sanitized(
    prepSeconds = prepSeconds,
    workSeconds = workSeconds,
    restSeconds = restSeconds,
    rounds = rounds,
    warmUpSeconds = warmUpSeconds,
    coolDownSeconds = coolDownSeconds,
    activity = Activity.named(activity),
    sets = sets,
    setRestSeconds = setRestSeconds,
    workStepSeconds = workStepSeconds,
    then = decodeBlocks(thenBlocks),
    blockRestSeconds = blockRestSeconds,
)

internal const val LIFT_PROGRAM_DAY = "program_day"
internal const val LIFT_ANY_SESSION = "any_session"

internal fun HistoryEntity.toDomain() = HistoryEntry(
    finishedAtEpochMillis = finishedAtEpochMillis,
    config = intervalConfig(
        prepSeconds,
        workSeconds,
        restSeconds,
        rounds,
        warmUpSeconds,
        coolDownSeconds,
        activity,
        sets,
        setRestSeconds,
        workStepSeconds,
        thenBlocks,
        blockRestSeconds,
    ),
    elapsedSeconds = elapsedSeconds,
    endedEarly = endedEarly,
    workActiveSeconds = workActiveSeconds,
    id = id,
    effort = PerceivedEffort.of(effort),
    note = note,
    startedAtEpochMillis = startedAtEpochMillis,
    averageBpm = averageBpm,
    peakBpm = peakBpm,
)

internal fun HistoryEntry.toEntity(uid: String, updatedAtEpochMillis: Long) = HistoryEntity(
    id = id ?: 0,
    finishedAtEpochMillis = finishedAtEpochMillis,
    prepSeconds = config.prepSeconds,
    workSeconds = config.workSeconds,
    restSeconds = config.restSeconds,
    rounds = config.rounds,
    warmUpSeconds = config.warmUpSeconds,
    coolDownSeconds = config.coolDownSeconds,
    sets = config.sets,
    setRestSeconds = config.setRestSeconds,
    workStepSeconds = config.workStepSeconds,
    thenBlocks = encodeBlocks(config.then),
    blockRestSeconds = config.blockRestSeconds,
    elapsedSeconds = elapsedSeconds,
    endedEarly = endedEarly,
    workActiveSeconds = workActiveSeconds,
    activity = config.activity.name,
    uid = uid,
    updatedAtEpochMillis = updatedAtEpochMillis,
    effort = effort?.level,
    note = note,
    startedAtEpochMillis = startedAtEpochMillis,
    averageBpm = averageBpm,
    peakBpm = peakBpm,
)

internal fun PresetEntity.toDomain() =
    Preset(
        name,
        intervalConfig(
            prepSeconds,
            workSeconds,
            restSeconds,
            rounds,
            warmUpSeconds,
            coolDownSeconds,
            activity,
            sets,
            setRestSeconds,
            workStepSeconds,
            thenBlocks,
            blockRestSeconds,
        ),
        id = id,
    )

internal fun Preset.toEntity() = PresetEntity(
    id = id ?: 0,
    name = name,
    prepSeconds = config.prepSeconds,
    workSeconds = config.workSeconds,
    restSeconds = config.restSeconds,
    rounds = config.rounds,
    warmUpSeconds = config.warmUpSeconds,
    coolDownSeconds = config.coolDownSeconds,
    sets = config.sets,
    setRestSeconds = config.setRestSeconds,
    workStepSeconds = config.workStepSeconds,
    thenBlocks = encodeBlocks(config.then),
    blockRestSeconds = config.blockRestSeconds,
    activity = config.activity.name,
)

/**
 * A row with neither a weekday nor a day, or a kind of Lift session this build doesn't know, is unreadable: null,
 * and left out.
 */
internal fun PlannedEntity.toDomain(): PlannedWorkout? {
    val schedule = when {
        weekday != null -> PlanSchedule.Weekly(weekday)
        epochDay != null -> PlanSchedule.Once(epochDay)
        else -> return null
    }
    val workout = when (lift) {
        null -> PlanWorkout.Interval(
            name,
            intervalConfig(
                prepSeconds,
                workSeconds,
                restSeconds,
                rounds,
                warmUpSeconds,
                coolDownSeconds,
                activity,
                sets,
                setRestSeconds,
                workStepSeconds,
                thenBlocks,
                blockRestSeconds,
            ),
            presetId,
        )

        LIFT_PROGRAM_DAY -> PlanWorkout.Lift.ProgramDay

        LIFT_ANY_SESSION -> PlanWorkout.Lift.AnySession

        else -> return null
    }
    return PlannedWorkout(id, workout, schedule, createdAtEpochMillis)
}

internal fun PlannedWorkout.toEntity(): PlannedEntity {
    val interval = workout as? PlanWorkout.Interval
    val config = interval?.config
    return PlannedEntity(
        id = id,
        weekday = (schedule as? PlanSchedule.Weekly)?.weekday,
        epochDay = (schedule as? PlanSchedule.Once)?.epochDay,
        name = interval?.name.orEmpty(),
        prepSeconds = config?.prepSeconds ?: 0,
        workSeconds = config?.workSeconds ?: 0,
        restSeconds = config?.restSeconds ?: 0,
        rounds = config?.rounds ?: 0,
        warmUpSeconds = config?.warmUpSeconds ?: 0,
        coolDownSeconds = config?.coolDownSeconds ?: 0,
        sets = config?.sets ?: 1,
        setRestSeconds = config?.setRestSeconds ?: 0,
        workStepSeconds = config?.workStepSeconds ?: 0,
        thenBlocks = encodeBlocks(config?.then.orEmpty()),
        blockRestSeconds = config?.blockRestSeconds ?: 0,
        activity = config?.activity?.name,
        presetId = interval?.presetId,
        createdAtEpochMillis = createdAtEpochMillis,
        lift = when (workout) {
            is PlanWorkout.Interval -> null
            PlanWorkout.Lift.ProgramDay -> LIFT_PROGRAM_DAY
            PlanWorkout.Lift.AnySession -> LIFT_ANY_SESSION
        },
    )
}
