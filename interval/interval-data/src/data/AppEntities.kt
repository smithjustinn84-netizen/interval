package dev.justinsmith.interval.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.justinsmith.interval.model.PerceivedEffort
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.timer.Activity

// Columns are flat rather than an embedded IntervalConfig, so the domain types carry no Room annotations.

@Entity(tableName = "history", indices = [Index("finished_at"), Index(value = ["uid"], unique = true)])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "finished_at") val finishedAtEpochMillis: Long,
    @ColumnInfo(name = "prep_seconds") val prepSeconds: Int,
    @ColumnInfo(name = "work_seconds") val workSeconds: Int,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int,
    val rounds: Int,
    @ColumnInfo(name = "elapsed_seconds") val elapsedSeconds: Int,
    @ColumnInfo(name = "ended_early") val endedEarly: Boolean,
    /** Of [elapsedSeconds], the time spent in work intervals. Null in rows from version 1. */
    @ColumnInfo(name = "work_active_seconds") val workActiveSeconds: Int? = null,
    /** An [Activity] by name. Null in rows from version 1, which read as [Activity.General]. */
    val activity: String? = null,
    /** Who it is across devices and apps: 32 hex digits, never reused. */
    @ColumnInfo(defaultValue = "''") val uid: String,
    /** When it was recorded or last changed, deletion included. 0 in rows from before version 4. */
    @ColumnInfo(name = "updated_at", defaultValue = "0") val updatedAtEpochMillis: Long,
    /** A deleted workout stays as a tombstone, its row kept with when it went. */
    @ColumnInfo(defaultValue = "0") val deleted: Boolean = false,
    /** How hard it felt, a [PerceivedEffort.level]; null until the user says, and in rows from before version 7. */
    val effort: Int? = null,
    /** A line on how it went; null for none. */
    val note: String? = null,
    /** 0 for none, and in rows from before version 8. */
    @ColumnInfo(name = "warm_up_seconds", defaultValue = "0") val warmUpSeconds: Int = 0,
    @ColumnInfo(name = "cool_down_seconds", defaultValue = "0") val coolDownSeconds: Int = 0,
    /** When it began; null in rows from before version 9, whose start is run back from [finishedAtEpochMillis]. */
    @ColumnInfo(name = "started_at") val startedAtEpochMillis: Long? = null,
    /** Its heart rate, average and peak in beats a minute, as read from the health store; null until read. */
    @ColumnInfo(name = "average_bpm") val averageBpm: Int? = null,
    @ColumnInfo(name = "peak_bpm") val peakBpm: Int? = null,
    /** 1 and 0, one set with no rest between, in rows from before version 10; no ladder before 11. */
    @ColumnInfo(defaultValue = "1") val sets: Int = 1,
    @ColumnInfo(name = "set_rest_seconds", defaultValue = "0") val setRestSeconds: Int = 0,
    @ColumnInfo(name = "work_step_seconds", defaultValue = "0") val workStepSeconds: Int = 0,
    /** A sequence's blocks after its first, a JSON list ([encodeBlocks]); "[]", one block, in rows from before 12. */
    @ColumnInfo(name = "then_blocks", defaultValue = "'[]'") val thenBlocks: String = "[]",
    @ColumnInfo(name = "block_rest_seconds", defaultValue = "0") val blockRestSeconds: Int = 0,
    /** About how many snatches a set and the bell, as the lifter counted them; both null until counted. */
    @ColumnInfo(name = "snatches_per_set") val snatchesPerSet: Int? = null,
    @ColumnInfo(name = "snatch_bell_kg") val snatchBellKg: Double? = null,
    /** The first block is a time cap; false in rows from before version 18. */
    @ColumnInfo(defaultValue = "0") val capped: Boolean = false,
    /** The rounds done in its caps, a JSON list ([encodeCapRounds]); null for none counted. */
    @ColumnInfo(name = "cap_rounds") val capRounds: String? = null,
    /** 5-second BPM samples recorded live by a sensor, a JSON list ([encodeHeartRateSamples]); null before 20. */
    @ColumnInfo(name = "heart_rate_samples") val heartRateSamples: String? = null,
)

@Entity(tableName = "preset")
data class PresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "prep_seconds") val prepSeconds: Int,
    @ColumnInfo(name = "work_seconds") val workSeconds: Int,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int,
    val rounds: Int,
    /** As [HistoryEntity.activity]. */
    val activity: String? = null,
    /** As [HistoryEntity.warmUpSeconds]. */
    @ColumnInfo(name = "warm_up_seconds", defaultValue = "0") val warmUpSeconds: Int = 0,
    @ColumnInfo(name = "cool_down_seconds", defaultValue = "0") val coolDownSeconds: Int = 0,
    /** As [HistoryEntity.sets]. */
    @ColumnInfo(defaultValue = "1") val sets: Int = 1,
    @ColumnInfo(name = "set_rest_seconds", defaultValue = "0") val setRestSeconds: Int = 0,
    @ColumnInfo(name = "work_step_seconds", defaultValue = "0") val workStepSeconds: Int = 0,
    /** As [HistoryEntity.thenBlocks]. */
    @ColumnInfo(name = "then_blocks", defaultValue = "'[]'") val thenBlocks: String = "[]",
    @ColumnInfo(name = "block_rest_seconds", defaultValue = "0") val blockRestSeconds: Int = 0,
    /** As [dev.justinsmith.interval.timer.Preset.alternateHands]. */
    @ColumnInfo(name = "alternate_hands", defaultValue = "0") val alternateHands: Boolean = false,
    /** As [HistoryEntity.capped]. */
    @ColumnInfo(defaultValue = "0") val capped: Boolean = false,
)

/**
 * A [PlannedWorkout]: exactly one of [weekday] (every week) and [epochDay] (once) is set. [lift] names a session
 * on Lift ([LIFT_PROGRAM_DAY] or [LIFT_ANY_SESSION]), whose row leaves the name and the shape empty; [sport] a day
 * of a sport (see [sportOf]), whose row leaves the shape empty and holds in [name] the user's own name for one they
 * named. With both null it is an interval workout.
 */
@Entity(tableName = "planned")
data class PlannedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val weekday: Int?,
    @ColumnInfo(name = "epoch_day") val epochDay: Long?,
    val name: String,
    @ColumnInfo(name = "prep_seconds") val prepSeconds: Int,
    @ColumnInfo(name = "work_seconds") val workSeconds: Int,
    @ColumnInfo(name = "rest_seconds") val restSeconds: Int,
    val rounds: Int,
    /** As [HistoryEntity.activity]. */
    val activity: String?,
    @ColumnInfo(name = "preset_id") val presetId: Long?,
    @ColumnInfo(name = "created_at") val createdAtEpochMillis: Long,
    val lift: String? = null,
    /** As [HistoryEntity.warmUpSeconds]. */
    @ColumnInfo(name = "warm_up_seconds", defaultValue = "0") val warmUpSeconds: Int = 0,
    @ColumnInfo(name = "cool_down_seconds", defaultValue = "0") val coolDownSeconds: Int = 0,
    /** As [HistoryEntity.sets]. */
    @ColumnInfo(defaultValue = "1") val sets: Int = 1,
    @ColumnInfo(name = "set_rest_seconds", defaultValue = "0") val setRestSeconds: Int = 0,
    @ColumnInfo(name = "work_step_seconds", defaultValue = "0") val workStepSeconds: Int = 0,
    /** As [HistoryEntity.thenBlocks]. */
    @ColumnInfo(name = "then_blocks", defaultValue = "'[]'") val thenBlocks: String = "[]",
    @ColumnInfo(name = "block_rest_seconds", defaultValue = "0") val blockRestSeconds: Int = 0,
    /** Null in rows from before version 13, none of which is a sport. */
    val sport: String? = null,
    /** A one-off's `PlanSchedule.Once.movedFromDay`; null in rows from before version 17. */
    @ColumnInfo(name = "moved_from_day") val movedFromDay: Long? = null,
    /** As [HistoryEntity.capped]. */
    @ColumnInfo(defaultValue = "0") val capped: Boolean = false,
)

/**
 * A day of a sport the user logged: one of a sport a day, which the index keeps. [sport] and [name] are as
 * [PlannedEntity]'s, the name empty but for a sport the user named.
 */
@Entity(tableName = "sport_log", indices = [Index(value = ["epoch_day", "sport", "name"], unique = true)])
data class SportLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
    val sport: String,
    val name: String,
    @ColumnInfo(name = "logged_at") val loggedAtEpochMillis: Long,
)

/**
 * A mobility flow the user built: [use] is a [dev.justinsmith.interval.timer.FlowUse]'s name, [moves] the moves in
 * order as one JSON list (`FlowsJson.kt`), each its name, seconds, sides, hold and cue.
 */
@Entity(tableName = "flow")
data class FlowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val use: String,
    val note: String? = null,
    val moves: String,
)

/**
 * A class recorded on a heart-rate sensor: its day and sport as a [SportLogEntity] has them, the moment it started,
 * which tells it from every other, and [samples], its heart rate and movement every five seconds as JSON
 * (`ClassesJson.kt`). Its rounds are worked out from those as it is read.
 */
@Entity(tableName = "sport_class", indices = [Index(value = ["started_at"], unique = true)])
data class SportClassEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "epoch_day") val epochDay: Long,
    val sport: String,
    val name: String,
    @ColumnInfo(name = "started_at") val startedAtEpochMillis: Long,
    val samples: String,
)
