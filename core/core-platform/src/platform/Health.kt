package dev.justinsmith.interval.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * How the platform's health store stands: there is none on this platform (desktop), it is yet to be
 * installed (Android before 14 without the Health Connect app), it is there but the app may not write
 * to it, or the user has let the app write workouts to it. Reading the body weight is a leave of its own,
 * asked with the rest, and doesn't change the status: see [HealthStore.latestWeightKg].
 */
enum class HealthStatus { Unavailable, NotInstalled, Disconnected, Connected }

/**
 * A finished workout as the health store takes it: its [kind], its [title] in the user's words or null
 * for the store's own name for the kind, when it ran, what each stretch of it was spent on
 * ([segments], in order, none overlapping, all within the workout), and the [calories] it is estimated
 * to have burned, null without a body weight. [notes] are the user's words on it, null for none. [id] is the
 * same each time the same workout is written, and a write replaces the record only with a higher [version]:
 * one repeated after a failure keeps 0 and replaces it, as nothing was stored; one adding the summary's
 * [notes] later passes the time it is written.
 */
data class HealthWorkout(
    val id: String,
    val kind: HealthWorkoutKind,
    val title: String?,
    val startedAtEpochMillis: Long,
    val endedAtEpochMillis: Long,
    val segments: List<HealthSegment> = emptyList(),
    val calories: Int? = null,
    val notes: String? = null,
    val version: Long = 0,
)

/** An interval workout, or a Lift session. */
enum class HealthWorkoutKind { Intervals, Strength }

/** One stretch of a [HealthWorkout]: what it was spent on, and for a set of a lift, how many reps. */
data class HealthSegment(
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val move: HealthMove,
    val repetitions: Int = 0,
)

/**
 * What a segment was spent on, in the words the health stores share: an interval's work of no
 * particular kind ([Work]), a rest, a pause, a machine, or a lift. What the stores have no word for
 * is [Other].
 */
enum class HealthMove {
    Work,
    Rest,
    Pause,
    Biking,
    Rowing,
    Running,
    JumpRope,
    KettlebellSwing,
    Weightlifting,
    Burpee,
    Squat,
    Deadlift,
    ShoulderPress,
    BenchPress,
    Row,
    PullUp,
    Plank,
    Lunge,
    MountainClimber,
    HipThrust,
    Other,
}

/**
 * The platform's health store, which keeps the user's workouts beside every other app's: Health Connect on
 * Android. The app writes its workouts to it and reads one thing back, the latest body weight, which
 * Settings offers to take. Connecting takes a permission sheet, which only a screen can open (core's
 * `rememberHealthConnector`); the store hears of the answer through [refresh]. Injected, so tests record
 * what was written.
 */
interface HealthStore {
    /** How the store stands now, as last read: see [refresh]. */
    val status: StateFlow<HealthStatus>

    /** Reads the store's state again: as Settings opens, and as the permission sheet closes. */
    fun refresh()

    /** Gives the leave to write back, so nothing more is written until the user connects again. */
    fun disconnect()

    /**
     * Writes [workout] to the store, in the background, replacing one written under its id before. Nothing
     * is written while the store is not [HealthStatus.Connected]; a write that fails is logged and dropped.
     */
    fun record(workout: HealthWorkout)

    /**
     * The body weight last weighed in the store, by any app, in kilograms: null when the app may not read it,
     * or none was weighed in the last 30 days (an older weight says little of today's), or the store can't
     * be read.
     */
    suspend fun latestWeightKg(): Double?
}

/** The platform has no health store: desktop, and iOS until HealthKit is wired. */
object NoHealthStore : HealthStore {
    override val status: StateFlow<HealthStatus> = MutableStateFlow(HealthStatus.Unavailable)

    override fun refresh() = Unit

    override fun disconnect() = Unit

    override fun record(workout: HealthWorkout) = Unit

    override suspend fun latestWeightKg(): Double? = null
}
