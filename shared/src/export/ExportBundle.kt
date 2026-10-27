package dev.justinsmith.interval.export

import dev.justinsmith.interval.timer.dateOfEpochDay
import kotlinx.serialization.Serializable

/**
 * Everything the app stores, as the export file holds it: primitives and names only, so the domain types
 * can change shape while an old file still reads. [schema] counts the file's own shape; a reader refuses a
 * newer one. Lift's sessions carry their sets but not how a day's plan was changed: the sets are the record.
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
) {
    companion object {
        const val SCHEMA = 1
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
)

@Serializable
data class CuesExport(val sound: Boolean, val voice: Boolean, val halfway: Boolean, val vibrate: Boolean)

@Serializable
data class ConfigExport(
    val prepSeconds: Int,
    val workSeconds: Int,
    val restSeconds: Int,
    val rounds: Int,
    val activity: String,
)

@Serializable
data class PresetExport(val id: Long?, val name: String, val config: ConfigExport)

@Serializable
data class WorkoutExport(
    val id: Long?,
    val finishedAtEpochMillis: Long,
    val config: ConfigExport,
    val elapsedSeconds: Int,
    val endedEarly: Boolean,
    val workActiveSeconds: Int?,
)

/** A planned workout: [weekday] (1 Monday to 7 Sunday) when weekly, [epochDay] when once. */
@Serializable
data class PlannedExport(
    val id: Long,
    val name: String,
    val config: ConfigExport,
    val weekday: Int?,
    val epochDay: Long?,
    val presetId: Long?,
    val createdAtEpochMillis: Long,
)

@Serializable
data class LiftExport(
    val bellsKg: List<Double>,
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
)
