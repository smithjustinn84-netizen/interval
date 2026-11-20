package dev.justinsmith.interval.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

/**
 * The five heart-rate training zones as a share of the user's max heart rate:
 * [Zone1] (<60%), [Zone2] (60–70%), [Zone3] (70–80%), [Zone4] (80–90%), and [Zone5] (≥90%).
 */
enum class HeartRateZone {
    Zone1,
    Zone2,
    Zone3,
    Zone4,
    Zone5,
    ;

    val number: Int get() = ordinal + 1
    val isHigh: Boolean get() = this == Zone4 || this == Zone5
    val isRecovery: Boolean get() = this == Zone1 || this == Zone2
}

/** Where a live heart-rate stream stands right now. */
sealed interface LiveHeartRateState {
    /** The platform has no live sensor, or none is paired. */
    data object Unavailable : LiveHeartRateState

    /** A sensor is paired and idle (not streaming). */
    data object Idle : LiveHeartRateState

    /** Connecting to the paired sensor for live readings. */
    data object Connecting : LiveHeartRateState

    /**
     * A live heart-rate reading from the sensor, in beats a minute. Without [contact] the sensor is connected and
     * reads no heart (a strap off the chest): [bpm] is then 0 and nothing to show or keep.
     */
    data class Reading(val bpm: Int, val contact: Boolean = true) : LiveHeartRateState

    /** The connection dropped, or never came; the sensor is being tried again. */
    data object Disconnected : LiveHeartRateState
}

/**
 * Streams live heart-rate readings from the user's paired sensor during an active workout or Lift session. The
 * one stream is shared: it runs while anyone holds it, each user starting and stopping it for itself.
 * Both sides consume this contract from `core-platform`; Android's `PolarClassSensor` implements it beside
 * offline class recording, while iOS and desktop bind [NoLiveHeartRate].
 */
interface LiveHeartRate {
    val reading: StateFlow<LiveHeartRateState>

    /**
     * [user] wants the heart rate live: streaming begins from the paired sensor, unless it already runs for
     * another. No-op when unavailable or unpaired, or when [user] holds it already.
     */
    fun start(user: Any)

    /** [user] is done with it; the stream stops and its connection is let go once no one holds it. */
    fun stop(user: Any)
}

/** No live heart-rate sensor on this platform. */
object NoLiveHeartRate : LiveHeartRate {
    override val reading: StateFlow<LiveHeartRateState> = MutableStateFlow(LiveHeartRateState.Unavailable)

    override fun start(user: Any) = Unit

    override fun stop(user: Any) = Unit
}

/** Default maximum heart rate used for zones when the user hasn't set their own. */
const val DEFAULT_MAX_BPM = 190

/** Accepted range for a user-configured maximum heart rate. */
val MAX_BPM_RANGE: IntRange = 120..230

/** Seconds covered by each persisted heart-rate sample bucket. */
const val HR_SAMPLE_SECONDS = 5

/** Classifies [bpm] into one of the five [HeartRateZone]s for [maxBpm]. */
fun heartRateZone(bpm: Int, maxBpm: Int = DEFAULT_MAX_BPM): HeartRateZone {
    val max = maxBpm.coerceIn(MAX_BPM_RANGE).toFloat()
    val ratio = bpm / max
    return when {
        ratio < ZONE2_THRESHOLD -> HeartRateZone.Zone1
        ratio < ZONE3_THRESHOLD -> HeartRateZone.Zone2
        ratio < ZONE4_THRESHOLD -> HeartRateZone.Zone3
        ratio < ZONE5_THRESHOLD -> HeartRateZone.Zone4
        else -> HeartRateZone.Zone5
    }
}

/** Whether a sensor is in use, so there is something to show of it: a reading, or why there is none yet. */
val LiveHeartRateState.inUse: Boolean
    get() = this != LiveHeartRateState.Unavailable && this != LiveHeartRateState.Idle

/**
 * The lowest heart rate in [zone] for [maxBpm], as [heartRateZone] sorts them, so one beat fewer is the zone
 * below; null for [HeartRateZone.Zone1], which has no floor.
 */
fun zoneFloor(zone: HeartRateZone, maxBpm: Int = DEFAULT_MAX_BPM): Int? =
    if (zone == HeartRateZone.Zone1) null else (1..MAX_BPM_RANGE.last).first { heartRateZone(it, maxBpm) >= zone }

/**
 * Where [bpm] sits along a scale of the five zones drawn a fifth each: 0 at half of [maxBpm], where the
 * scale's first zone opens, 1 at the max, and no further either way.
 */
fun zoneScaleFraction(bpm: Int, maxBpm: Int = DEFAULT_MAX_BPM): Float {
    val ratio = bpm / maxBpm.coerceIn(MAX_BPM_RANGE).toFloat()
    return ((ratio - ZONE_SCALE_FLOOR) / (1f - ZONE_SCALE_FLOOR)).coerceIn(0f, 1f)
}

/** How many seconds of [samples] (each covering [sampleSeconds]) fell in each [HeartRateZone]. */
fun zoneSeconds(
    samples: List<Int>,
    maxBpm: Int = DEFAULT_MAX_BPM,
    sampleSeconds: Int = HR_SAMPLE_SECONDS,
): Map<HeartRateZone, Int> {
    val counts = HeartRateZone.entries.associateWith { 0 }.toMutableMap()
    for (bpm in samples) {
        if (bpm in HeartRate.BPM_RANGE) {
            val zone = heartRateZone(bpm, maxBpm)
            counts[zone] = (counts[zone] ?: 0) + sampleSeconds
        }
    }
    return counts
}

/** A debounced zone-band crossing worth cueing during a workout. */
enum class ZoneTransition {
    /** Entered high-intensity effort (Zone 4 or 5). */
    EnteredHigh,

    /** Returned to recovery (Zone 1 or 2) after being in tempo/high effort. */
    EnteredRecovery,
}

/**
 * Tracks live BPM readings and emits [ZoneTransition] cues when the user sustains a move into high effort
 * (Zone 4–5) or back down to recovery (Zone 1–2), with sustain and cooldown windows to prevent chattering on
 * a zone boundary.
 */
class ZoneTracker(
    private val maxBpm: Int = DEFAULT_MAX_BPM,
    private val sustainMillis: Long = DEFAULT_SUSTAIN_MILLIS,
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
) {
    private var currentBand: Band? = null
    private var pendingBand: Band? = null
    private var pendingSince: Long = 0L
    private var lastCueAt: Long = Long.MIN_VALUE

    private enum class Band { Recovery, Mid, High }

    fun reset() {
        currentBand = null
        pendingBand = null
        pendingSince = 0L
        lastCueAt = Long.MIN_VALUE
    }

    /** Records [bpm] at [nowMillis] and returns a [ZoneTransition] if a debounced cue should fire. */
    fun record(bpm: Int, nowMillis: Long): ZoneTransition? {
        if (bpm !in HeartRate.BPM_RANGE) return null
        val zone = heartRateZone(bpm, maxBpm)
        val band = when {
            zone.isHigh -> Band.High
            zone.isRecovery -> Band.Recovery
            else -> Band.Mid
        }
        val established = currentBand
        if (established == null) {
            currentBand = band
            pendingBand = null
            return null
        }
        if (band == established) {
            pendingBand = null
            return null
        }
        if (pendingBand != band) {
            pendingBand = band
            pendingSince = nowMillis
            return null
        }
        if (nowMillis - pendingSince < sustainMillis) return null
        currentBand = band
        pendingBand = null
        val cooledDown = lastCueAt == Long.MIN_VALUE || nowMillis - lastCueAt >= cooldownMillis
        if (!cooledDown) return null
        return when {
            band == Band.High -> {
                lastCueAt = nowMillis
                ZoneTransition.EnteredHigh
            }

            band == Band.Recovery && established != Band.Recovery -> {
                lastCueAt = nowMillis
                ZoneTransition.EnteredRecovery
            }

            else -> null
        }
    }

    companion object {
        const val DEFAULT_SUSTAIN_MILLIS = 5_000L
        const val DEFAULT_COOLDOWN_MILLIS = 15_000L
    }
}

/**
 * Accumulates live BPM readings over a workout into [sampleSeconds]-wide buckets, producing compact
 * persisted samples plus [HeartRate] average and peak figures.
 */
class HeartRateSampler(private val sampleSeconds: Int = HR_SAMPLE_SECONDS) {
    private var startedAt: Long? = null
    private val buckets = mutableMapOf<Int, MutableList<Int>>()
    private var count = 0
    private var sum = 0L
    private var peak = 0

    fun reset() {
        startedAt = null
        buckets.clear()
        count = 0
        sum = 0L
        peak = 0
    }

    /** Records [bpm] at [nowMillis]; readings outside [HeartRate.BPM_RANGE] are ignored. */
    fun add(bpm: Int, nowMillis: Long) {
        if (bpm !in HeartRate.BPM_RANGE) return
        val origin = startedAt ?: nowMillis.also { startedAt = it }
        val bucket = ((nowMillis - origin).coerceAtLeast(0L) / (sampleSeconds * MILLIS_PER_SECOND)).toInt()
        buckets.getOrPut(bucket) { mutableListOf() }.add(bpm)
        count++
        sum += bpm
        if (bpm > peak) peak = bpm
    }

    val hasReadings: Boolean get() = count > 0

    /** Average and peak BPM across every valid reading, or null if none arrived. */
    fun summary(): HeartRate? = if (count == 0) {
        null
    } else {
        HeartRate(averageBpm = (sum.toDouble() / count).roundToInt(), peakBpm = peak)
    }

    /**
     * One averaged BPM per [sampleSeconds] bucket from the first reading to the last (0 for any bucket
     * with no reading), or empty when nothing was recorded.
     */
    fun samples(): List<Int> {
        val last = buckets.keys.maxOrNull() ?: return emptyList()
        return List(last + 1) { index ->
            val readings = buckets[index]
            if (readings.isNullOrEmpty()) 0 else readings.average().roundToInt()
        }
    }
}

private const val ZONE_SCALE_FLOOR = 0.5f
private const val ZONE2_THRESHOLD = 0.6f
private const val ZONE3_THRESHOLD = 0.7f
private const val ZONE4_THRESHOLD = 0.8f
private const val ZONE5_THRESHOLD = 0.9f
private const val MILLIS_PER_SECOND = 1000L
