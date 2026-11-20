package dev.justinsmith.lift.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "session", indices = [Index("started_at")])
data class SessionEntity(
    /** 32 hex digits, never reused: how History and its detail page name it. */
    @PrimaryKey val uid: String,
    @ColumnInfo(name = "started_at") val startedAtEpochMillis: Long,
    /** Null while it is in progress. */
    @ColumnInfo(name = "finished_at") val finishedAtEpochMillis: Long?,
    val title: String,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
    val deleted: Boolean = false,
    /** The program it was a day of, a [dev.justinsmith.lift.domain.ProgramKey] as stored; null for a free session. */
    @ColumnInfo(name = "program_id") val programId: String? = null,
    /** The day as the session changed it, in [encodePlan]'s shape; null while it is as programmed. */
    val plan: String? = null,
    /** Which day of its program it was, from 1; null for a free session, or one logged before sessions kept it. */
    @ColumnInfo(name = "program_day") val programDay: Int? = null,
    /** How hard it felt, a [dev.justinsmith.lift.domain.PerceivedEffort.level]; null until said. */
    val effort: Int? = null,
    /** A line on how it went; null for none. */
    val note: String? = null,
    /** The timer running in it while it is in progress, in [encodeTimer]'s shape; null for none. */
    val timer: String? = null,
    /** Its heart rate, average and peak in beats a minute, as read from the health store; null until read. */
    @ColumnInfo(name = "average_bpm") val averageBpm: Int? = null,
    @ColumnInfo(name = "peak_bpm") val peakBpm: Int? = null,
    /** 5-second BPM samples recorded live by a sensor, a JSON list; null before version 18. */
    @ColumnInfo(name = "heart_rate_samples") val heartRateSamples: String? = null,
)

@Entity(
    tableName = "lift_set",
    indices = [Index("session_uid")],
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["uid"],
            childColumns = ["session_uid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "session_uid") val sessionUid: String,
    /** An [dev.justinsmith.lift.domain.Exercise] by its key. */
    val exercise: String,
    @ColumnInfo(name = "bell_kg") val bellKg: Double,
    val reps: Int,
    /** A [dev.justinsmith.lift.domain.Side] by name, or null for a two-sided lift. */
    val side: String?,
    @ColumnInfo(name = "logged_at") val loggedAtEpochMillis: Long,
    /** Done with a bell in each hand, both of [bellKg]. */
    @ColumnInfo(defaultValue = "0") val doubled: Boolean = false,
    /**
     * What it was done with, a [dev.justinsmith.lift.domain.Equipment] by name; null for a set logged
     * before version 7, all kettlebell.
     */
    val equipment: String? = null,
    /** How long it was held; null for a set counted in reps. */
    val seconds: Int? = null,
    /** The band used, a [BandEntity]'s id (one every user starts with by its name); null for a set without one. */
    val band: String? = null,
)

/**
 * An exercise the user added. [equipment], [measure] and [focus] are their enums by name; [archived] keeps
 * it, and its sets, out of the picker.
 */
@Entity(tableName = "custom_exercise")
data class CustomExerciseEntity(
    /** 32 hex digits, never reused. */
    @PrimaryKey val uid: String,
    val name: String,
    val equipment: String,
    val measure: String,
    @ColumnInfo(name = "per_side") val perSide: Boolean,
    val focus: String,
    val cues: String,
    val archived: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)

/**
 * The program the user follows, a [dev.justinsmith.lift.domain.ProgramKey] as stored, from
 * [startedAtEpochMillis]: one row at most.
 */
@Entity(tableName = "following")
data class FollowingEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "program_id") val programId: String,
    @ColumnInfo(name = "started_at") val startedAtEpochMillis: Long,
    /** Days of it counted as done before [startedAtEpochMillis]: a copy followed from where its original was. */
    @ColumnInfo(name = "days_before", defaultValue = "0") val daysBefore: Int = 0,
)

/**
 * A band of the user's as they left it: one of the five every user starts with once changed (its [id] a
 * [dev.justinsmith.lift.domain.BandLevel] by name, [name] blank while unnamed), or one they added (32 hex
 * digits). [colour] is a [dev.justinsmith.lift.domain.BandColour] by name; [position] orders them, lightest
 * first. Sets name their band by [id], so a band with sets is archived, never deleted.
 */
@Entity(tableName = "band")
data class BandEntity(
    @PrimaryKey val id: String,
    val name: String,
    val colour: String,
    val position: Int,
    val archived: Boolean,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)

/** A bell the user owns, and whether they own a [pair] of it, two of the same. */
@Entity(tableName = "bell")
data class BellEntity(@PrimaryKey val kg: Double, @ColumnInfo(defaultValue = "0") val pair: Boolean = false)

/** The user's barbell, by what it weighs empty: one row at most, [id] 0. */
@Entity(tableName = "bar")
data class BarEntity(@PrimaryKey val id: Int = 0, val kg: Double)

/** How long the user rests between sets: one row at most, [id] 0; none until they change it. */
@Entity(tableName = "rest")
data class RestEntity(@PrimaryKey val id: Int = 0, val seconds: Int)

/** A program the user made. [days] is its days' blocks as JSON, in [encodeDays]'s shape. */
@Entity(tableName = "custom_program")
data class CustomProgramEntity(
    /** 32 hex digits, never reused. */
    @PrimaryKey val uid: String,
    val name: String,
    @ColumnInfo(name = "days_per_week") val daysPerWeek: Int,
    val weeks: Int,
    val days: String,
    @ColumnInfo(name = "updated_at") val updatedAtEpochMillis: Long,
)
