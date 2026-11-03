package dev.justinsmith.lift.data

import co.touchlab.kermit.Logger
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.Exercise
import dev.justinsmith.lift.domain.Load
import dev.justinsmith.lift.domain.SavedTimer
import dev.justinsmith.lift.domain.Side
import dev.justinsmith.lift.domain.band
import dev.justinsmith.lift.domain.bandOf
import dev.justinsmith.lift.domain.double
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A [SavedTimer] as [SessionEntity.timer] stores it: its [kind] (`rest`, `emom` or `hold`), the lift by key, the
 * side by name and the band by id, with only the fields that kind uses.
 */
@Serializable
internal data class StoredTimer(
    val kind: String,
    val startedAt: Long,
    val seconds: Int = 0,
    val exercise: String? = null,
    val bellKg: Double = 0.0,
    val addedKg: Double = 0.0,
    val barKg: Double = 0.0,
    val reps: Int = 0,
    val minutes: Int = 0,
    val side: String? = null,
    val double: Boolean = false,
    val band: String? = null,
    val done: List<Int> = emptyList(),
    val firstSide: Boolean = true,
)

private val json = Json { ignoreUnknownKeys = true }
private val log = Logger.withTag("StoredTimer")

private const val REST = "rest"
private const val EMOM = "emom"
private const val HOLD = "hold"

/** [timer] as [SessionEntity.timer] holds it. */
internal fun encodeTimer(timer: SavedTimer): String = json.encodeToString(
    when (timer) {
        is SavedTimer.Rest -> StoredTimer(REST, timer.startedAtEpochMillis, seconds = timer.seconds)

        is SavedTimer.Emom -> StoredTimer(
            EMOM,
            timer.startedAtEpochMillis,
            exercise = timer.exercise.key,
            bellKg = timer.load.kg,
            reps = timer.reps,
            minutes = timer.minutes,
            side = timer.firstSide.name,
            double = timer.load.double,
            band = timer.load.band?.id,
            done = timer.done.sorted(),
        )

        is SavedTimer.Hold -> StoredTimer(
            HOLD,
            timer.startedAtEpochMillis,
            seconds = timer.seconds,
            exercise = timer.exercise.key,
            bellKg = timer.bellKg,
            addedKg = timer.addedKg,
            barKg = timer.barKg,
            side = timer.side.name,
            double = timer.double,
            band = timer.band.id,
            firstSide = timer.firstSide,
        )
    },
)

/**
 * The timer [stored] holds, its lift a built-in or one of [made] and its band one of [bands]; null, no timer, if
 * it can't be read or names a lift this build doesn't know.
 */
internal fun decodeTimer(stored: String, made: List<CustomExercise>, bands: List<Band>): SavedTimer? {
    val timer = try {
        json.decodeFromString<StoredTimer>(stored)
    } catch (e: IllegalArgumentException) {
        // A SerializationException included: JSON this build can't read.
        log.w(e) { "Couldn't read a session's timer" }
        return null
    }
    if (timer.kind == REST) return SavedTimer.Rest(timer.startedAt, timer.seconds).takeIf { timer.seconds > 0 }
    val exercise = timer.exercise?.let { Exercise.parse(it, made) } ?: return null
    val side = Side.entries.firstOrNull { it.name == timer.side } ?: Side.Left
    return when (timer.kind) {
        EMOM -> SavedTimer.Emom(
            timer.startedAt,
            exercise,
            Load.of(exercise.equipment, timer.bellKg, timer.double, timer.band?.let { bandOf(it, bands) }),
            timer.reps,
            timer.minutes,
            side,
            timer.done.toSet(),
        ).takeIf { timer.minutes > 0 }

        HOLD -> SavedTimer.Hold(
            timer.startedAt,
            exercise,
            timer.bellKg,
            timer.addedKg,
            timer.barKg,
            side,
            timer.double,
            timer.seconds,
            timer.band?.let { bandOf(it, bands) } ?: Band.Default,
            timer.firstSide,
        ).takeIf { timer.seconds > 0 }

        else -> null
    }
}
