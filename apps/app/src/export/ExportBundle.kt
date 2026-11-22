package dev.justinsmith.interval.export

import dev.justinsmith.interval.timer.dateOfEpochDay
import kotlinx.serialization.Serializable

/**
 * Everything the app stores, as the export file holds it: primitives and names only, so the domain types
 * can change shape while an old file still reads. [schema] counts the file's own shape; a reader refuses a
 * newer one (schema 2 let a plan be a session on Lift; schema 3 added how a workout or a session felt, its
 * `effort` 1 to 5 and its `note`, every shape's `warmUpSeconds` and `coolDownSeconds`, and a workout's or a
 * session's heart rate, `averageBpm` and `peakBpm`; schema 4 every shape's `sets`, `setRestSeconds` and
 * `workStepSeconds`; schema 5 a sequence's blocks after its first, `then`, and `blockRestSeconds`; schema 6 a plan
 * of a day of a sport, `sport`, and the sport days logged, `sportDays`; schema 7 the session's length in the settings,
 * `sessionMinutes`; schema 8 the mobility flows the user built, `flows`; schema 9 whether a preset switches hands,
 * `alternateHands`, and the bells owned two of, `pairsKg`; schema 10 a block's own rest, `rest`; schema 11 a block
 * of a kind an older build would drop, `kind` "Density", and the block a stepped one goes back to at the next
 * bell, `origin`, and about how many snatches a set a workout held and the bell, `snatchesPerSet` and `snatchBellKg`;
 * schema 12 the Lift exercise a flow's move is, `exercise`, and the day a one-off plan was moved from, `movedFromDay`,
 * schema 13 a block that is a time cap, `capped`, and the rounds done in a workout's caps, `capRounds`; schema 14
 * the classes recorded on a heart-rate sensor, `classes`; schema 15 the max heart rate in the settings and a
 * workout's or a session's heart rate as a sensor read it live, `heartRateSamples`; schema 16 the built-in an
 * exercise of the user's own is shown by, `figure`, and a set logged as a warm-up, `warmUp`, the rest absent from
 * older files). [SCHEMA] moves at most once between releases: a field added after the cycle's bump joins that
 * number's entry. Lift's sessions carry their sets but not how a day's plan was changed: the sets are the record.
 */
@Serializable
data class ExportBundle(
    val schema: Int = SCHEMA,
    val app: String = APP,
    /** The build that wrote it, when it knew ([dev.justinsmith.interval.platform.AppInfo]). */
    val version: String?,
    val exportedAtEpochMillis: Long,
    val settings: SettingsExport,
    /** The Timer's current settings. */
    val timer: ConfigExport,
    val presets: List<PresetExport>,
    val history: List<WorkoutExport>,
    val plan: List<PlannedExport>,
    val lift: LiftExport,
    /** The days of a sport logged. */
    val sportDays: List<SportDayExport> = emptyList(),
    /** The mobility flows the user built, in the order they were saved. */
    val flows: List<FlowExport> = emptyList(),
    /** The classes recorded on a heart-rate sensor, the latest first. */
    val classes: List<ClassExport> = emptyList(),
) {
    companion object {
        const val SCHEMA = 16
        const val APP = "interval"
        const val MIME_TYPE = "application/json"

        /** The file's name on the day it is written: `interval-2026-10-27.json`. */
        fun fileName(epochDay: Long): String {
            val date = dateOfEpochDay(epochDay)
            val month = date.month.toString().padStart(2, '0')
            val day = date.day.toString().padStart(2, '0')
            return "$APP-${date.year}-$month-$day.json"
        }
    }
}

@Serializable
data class SettingsExport(
    val theme: String,
    val keepScreenOn: Boolean,
    val weightKg: Double?,
    val weightUnit: String,
    val effort: String,
    val weeklyGoal: Int?,
    val reminderMinuteOfDay: Int?,
    val cues: CuesExport,
    /** How long a session is, as Set up was answered. */
    val sessionMinutes: Int? = null,
    /** User-configured maximum heart rate for training zones; null before schema 15. */
    val maxHeartRate: Int? = null,
    /** The resting heart rate the zones' reserve is counted from; null for none set, and in a file before it. */
    val restingHeartRate: Int? = null,
)

@Serializable
data class CuesExport(
    val sound: Boolean,
    val voice: Boolean,
    val halfway: Boolean,
    val vibrate: Boolean,
    val heartRateAlerts: Boolean = true,
)

@Serializable
data class ConfigExport(
    val prepSeconds: Int,
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
    val activity: String,
    /** 0 for none, and in schema 1 and 2. */
    val warmUpSeconds: Int = 0,
    val coolDownSeconds: Int = 0,
    /** One set with no rest between and no ladder, and in schema 1 to 3. */
    val sets: Int = 1,
    val setRestSeconds: Int = 0,
    val workStepSeconds: Int = 0,
    /** A sequence's blocks after its first, which is the fields above; none, one block, and in schema 1 to 4. */
    val then: List<IntervalBlockExport> = emptyList(),
    val blockRestSeconds: Int = 0,
    /** The first block is a time cap; false, and in files before schema 13. */
    val capped: Boolean = false,
)

/** One block of a sequence after its first, as [ConfigExport] holds the first. */
@Serializable
data class IntervalBlockExport(
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
    val sets: Int = 1,
    val setRestSeconds: Int = 0,
    val workStepSeconds: Int = 0,
    /** As [ConfigExport.capped]. */
    val capped: Boolean = false,
)

@Serializable
data class PresetExport(
    val id: Long?,
    val name: String,
    val config: ConfigExport,
    /** False, and in schema 1 to 8. */
    val alternateHands: Boolean = false,
)

/** A flow the user built: [use] a `FlowUse`'s name, its moves in order. */
@Serializable
data class FlowExport(val name: String, val use: String, val note: String? = null, val moves: List<MoveExport>)

@Serializable
data class MoveExport(
    val name: String,
    val seconds: Int,
    val cue: String = "",
    val sided: Boolean = false,
    val held: Boolean = true,
    /** The Lift exercise it is, by key; null for none, and in files before schema 12. */
    val exercise: String? = null,
)

@Serializable
data class WorkoutExport(
    val id: Long?,
    val finishedAtEpochMillis: Long,
    val config: ConfigExport,
    val elapsedSeconds: Int,
    val endedEarly: Boolean,
    val workActiveSeconds: Int?,
    /** How hard it felt, 1 (easy) to 5 (all out), and a note on it; null for none, and in schema 1 and 2. */
    val effort: Int? = null,
    val note: String? = null,
    /** Its heart rate as a watch recorded it, average and peak in beats a minute; null for none, and in older files. */
    val averageBpm: Int? = null,
    val peakBpm: Int? = null,
    /** About how many snatches a set and the bell, both or neither; null for none, and in files before schema 11. */
    val snatchesPerSet: Int? = null,
    val snatchBellKg: Double? = null,
    /** The rounds done in its time caps; null for none counted, and in files before schema 13. */
    val capRounds: List<CapRoundExport>? = null,
    /** 5-second BPM samples recorded live by a sensor; null before schema 15. */
    val heartRateSamples: List<Int>? = null,
)

/** A round done in a time cap: the cap's block (0-based), and how far into it, pauses left out. */
@Serializable
data class CapRoundExport(val block: Int, val atMillis: Long)

/** A planned workout: [weekday] (1 Monday to 7 Sunday) when weekly, [epochDay] when once. */
@Serializable
data class PlannedExport(
    val id: Long,
    /** An interval workout's name and shape; null for a session on Lift. A sport the user named has its name here. */
    val name: String?,
    val config: ConfigExport?,
    val weekday: Int?,
    val epochDay: Long?,
    val presetId: Long?,
    val createdAtEpochMillis: Long,
    /** A session on Lift: `ProgramDay`, the program's next day, or `AnySession`; null for an interval workout. */
    val lift: String? = null,
    /** A day of a sport: `Bjj`, `Climbing`, `Running`, or `Custom` with its name in [name]; null for a workout. */
    val sport: String? = null,
    /** The day a one-off was moved from, when the user moved it; null otherwise, and in files before schema 12. */
    val movedFromDay: Long? = null,
)

/** A day of a sport the user logged, on [epochDay]: [sport] and [name] as [PlannedExport]'s. */
@Serializable
data class SportDayExport(
    val epochDay: Long,
    val sport: String,
    val name: String? = null,
    val loggedAtEpochMillis: Long,
)

/**
 * A class recorded on a heart-rate sensor: its day and sport as [SportDayExport]'s, the moment it started, and a
 * reading every five seconds, [bpm] (0 where the sensor read none) with [movement] in step, how much the arm moved.
 * Its rounds are worked out from these, so the file holds none.
 */
@Serializable
data class ClassExport(
    val epochDay: Long,
    val sport: String,
    val name: String? = null,
    val startedAtEpochMillis: Long,
    val bpm: List<Int>,
    val movement: List<Float> = emptyList(),
)

@Serializable
data class LiftExport(
    val bellsKg: List<Double>,
    /** Of [bellsKg], those owned two of; none, and in schema 1 to 8. */
    val pairsKg: List<Double> = emptyList(),
    val barKg: Double?,
    val restSeconds: Int,
    val bands: List<BandExport>,
    val exercises: List<ExerciseExport>,
    val programs: List<ProgramExport>,
    val following: FollowedExport?,
    val sessions: List<SessionExport>,
)

@Serializable
data class BandExport(val id: String, val name: String, val colour: String, val position: Int, val archived: Boolean)

/** An exercise of the user's own; a built-in needs no row, its [key] names it in a set. */
@Serializable
data class ExerciseExport(
    val uid: String,
    val name: String,
    val equipment: String,
    val measure: String,
    val perSide: Boolean,
    val focus: String,
    val cues: String,
    val archived: Boolean,
    /** The built-in it is shown by, by key; null for none, and in files before schema 16. */
    val figure: String? = null,
)

/** A program of the user's own, by its stored [key]; a built-in needs no row. */
@Serializable
data class ProgramExport(
    val key: String,
    val name: String?,
    val daysPerWeek: Int,
    val weeks: Int,
    val days: List<List<BlockExport>>,
)

@Serializable
data class BlockExport(
    val exercise: String,
    val rounds: Int,
    val reps: Int,
    val kind: String,
    val rungs: List<Int>,
    val double: Boolean,
    val kg: Double?,
    val band: String?,
    val rangeBottom: Int?,
    val rangeTop: Int?,
    /** Its own rest in seconds; null for Lift's, and in schema 1 to 9. */
    val rest: Int? = null,
    /** The block it goes back to at the next bell, its origin; null for none, and in files from before it was kept. */
    val origin: BlockExport? = null,
)

@Serializable
data class FollowedExport(val program: String, val startedAtEpochMillis: Long, val daysBefore: Int)

@Serializable
data class SessionExport(
    val uid: String,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long,
    val title: String,
    val program: String?,
    val day: Int?,
    val sets: List<SetExport>,
    /** As [WorkoutExport.effort], [WorkoutExport.note] and [WorkoutExport.averageBpm]. */
    val effort: Int? = null,
    val note: String? = null,
    val averageBpm: Int? = null,
    val peakBpm: Int? = null,
    /** 5-second BPM samples recorded live by a sensor; null before schema 15. */
    val heartRateSamples: List<Int>? = null,
)

@Serializable
data class SetExport(
    val id: Long,
    val exercise: String,
    val equipment: String,
    val bellKg: Double,
    val reps: Int,
    val side: String?,
    val double: Boolean,
    val seconds: Int?,
    val band: String?,
    val loggedAtEpochMillis: Long,
    /** Logged as a warm-up; false in a file before it. */
    val warmUp: Boolean = false,
)
