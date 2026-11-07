package dev.justinsmith.interval.fakes

import dev.justinsmith.interval.platform.AppInfo
import dev.justinsmith.interval.platform.FileOpener
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.HealthStore
import dev.justinsmith.interval.platform.HealthWorkout
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.IdGenerator
import dev.justinsmith.interval.platform.ShareableFile
import dev.justinsmith.interval.platform.Sharer
import dev.justinsmith.interval.platform.Speaker
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.timer.MINUTES_PER_HOUR
import dev.justinsmith.interval.timer.dateOfEpochDay
import dev.justinsmith.interval.timer.epochDayOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.time.TimeSource

const val DAY_MILLIS = 24L * 60 * 60 * 1000
private const val MINUTE_MILLIS = 60L * 1000

/** The tests' today: Thursday 15 October 2026, as an epoch day. */
val TODAY: Long = epochDayOf(2026, 10, 15)

/** Midday of [epochDay] in epoch millis, well clear of either midnight. */
fun noonOf(epochDay: Long): Long = epochDay * DAY_MILLIS + DAY_MILLIS / 2

/**
 * Days are epoch days in UTC, as the platforms count them, so tests don't depend on a time zone. Day 0,
 * 1 January 1970, is a Thursday.
 */
object FixedTimestampFormatter : TimestampFormatter {
    override fun date(epochMillis: Long): String = dateOfDay(dayNumber(epochMillis))

    override fun time(epochMillis: Long): String = "at $epochMillis"

    override fun dayNumber(epochMillis: Long): Long = epochMillis.floorDiv(DAY_MILLIS)

    override fun dateOfDay(epochDay: Long): String = "day $epochDay"

    override fun monthTitle(epochDay: Long): String = dateOfEpochDay(epochDay).let { "${it.month}/${it.year}" }

    /** On the 24-hour clock, "7:30", "18:00". */
    override fun timeOfDay(minuteOfDay: Int): String =
        "${minuteOfDay / MINUTES_PER_HOUR}:${(minuteOfDay % MINUTES_PER_HOUR).toString().padStart(2, '0')}"

    override fun minuteOfDay(epochMillis: Long): Int = (epochMillis.mod(DAY_MILLIS) / MINUTE_MILLIS).toInt()
}

/** Ids numbered from 1 ("id-1", "id-2", …), so a test can name what it stored. */
class SequentialIds(private val prefix: String = "id-") : IdGenerator {
    private var next = 1

    override fun newId(): String = "$prefix${next++}"
}

/** A build that calls itself [version]. */
object FakeAppInfo : AppInfo {
    override val version: String = "1.2.3"
}

/** Stands in for the system's file picker: hands over [next] when asked (null backs out), and records each ask. */
class RecordingFileOpener(var next: ShareableFile? = null) : FileOpener {
    val asked = mutableListOf<List<String>>()

    override suspend fun open(mimeTypes: List<String>): ShareableFile? {
        asked += mimeTypes
        return next
    }
}

/** Records what was offered to the system to share. */
class RecordingSharer : Sharer {
    val files = mutableListOf<ShareableFile>()
    val texts = mutableListOf<String>()

    override suspend fun shareFile(file: ShareableFile) {
        files += file
    }

    override fun shareText(text: String) {
        texts += text
    }
}

/**
 * A health store that records what was written to it, and stands as a test sets [status]: disconnected to
 * start with, as a fresh install is. [disconnects] counts the user's disconnections; [refreshes] the
 * times the screen asked for the status again.
 */
class RecordingHealthStore(initial: HealthStatus = HealthStatus.Disconnected) : HealthStore {
    override val status = MutableStateFlow(initial)
    val workouts = mutableListOf<HealthWorkout>()

    /** The body weight weighed in the store, in kilograms, or null for none. */
    var weightKg: Double? = null

    /** The heart rate recorded in the store over any window, or null for none; and each window asked for. */
    var heartRate: HeartRate? = null
    val heartRateReads = mutableListOf<LongRange>()
    var refreshes = 0
    var disconnects = 0

    override fun refresh() {
        refreshes++
    }

    override fun disconnect() {
        disconnects++
        status.value = HealthStatus.Disconnected
    }

    /** Keeps [workout] only while connected, as the real store writes nothing without its permissions. */
    override fun record(workout: HealthWorkout) {
        if (status.value == HealthStatus.Connected) workouts += workout
    }

    /** Gives [weightKg] only while connected, as the real store reads nothing without its permissions. */
    override suspend fun latestWeightKg(): Double? = weightKg.takeIf { status.value == HealthStatus.Connected }

    /** Gives [heartRate] only while connected, as the real store reads nothing without its permissions. */
    override suspend fun heartRate(startEpochMillis: Long, endEpochMillis: Long): HeartRate? {
        heartRateReads += startEpochMillis..endEpochMillis
        return heartRate.takeIf { status.value == HealthStatus.Connected }
    }
}

/** A [Speaker] that keeps each line said, in order, as a flow a test can wait on (lines arrive on the main thread). */
class RecordingSpeaker : Speaker {
    val lines = MutableStateFlow(emptyList<String>())

    override fun speak(text: String) {
        lines.update { it + text }
    }
}

/**
 * Stands in for core's `corePlatformModule`: a [clock] standing at 0 unless a test moves it, real monotonic
 * time unless a test passes a `TestTimeSource` as [timeSource], the [FixedTimestampFormatter], [FakeAppInfo], a
 * [sharer] that records, a [health] store and a [speaker] that do too, and random [ids] unless a test passes
 * [SequentialIds].
 */
fun fakeCorePlatformModule(
    clock: WallClock = WallClock { 0L },
    formatter: TimestampFormatter = FixedTimestampFormatter,
    sharer: Sharer = RecordingSharer(),
    health: HealthStore = RecordingHealthStore(),
    timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
    ids: IdGenerator = IdGenerator.Random,
    speaker: Speaker = RecordingSpeaker(),
): Module = module {
    single { clock }
    single { timeSource }
    single { formatter }
    single<AppInfo> { FakeAppInfo }
    single { sharer }
    single { health }
    single { ids }
    single { speaker }
}
