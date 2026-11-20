package dev.justinsmith.interval.platform

import kotlin.math.roundToInt

/**
 * How far the heart rate fell in [seconds] from the sample at [index] of [samples] (each covering
 * [sampleSeconds]): the first marker of fitness a heart rate gives for nothing, and of fatigue as it shrinks
 * within a session. Null when either sample was not read, or the later one is at or past [before], where the
 * next effort starts.
 */
fun dropAfter(
    samples: List<Int>,
    index: Int,
    seconds: Int,
    before: Int = samples.size,
    sampleSeconds: Int = HR_SAMPLE_SECONDS,
): Int? {
    val later = index + seconds / sampleSeconds
    val from = samples.getOrNull(index)?.takeIf { it in HeartRate.BPM_RANGE } ?: return null
    val to = samples.getOrNull(later)?.takeIf { later < before && it in HeartRate.BPM_RANGE } ?: return null
    return from - to
}

/** Whether the heart came down after the last efforts as it did after the first. */
enum class RecoveryTrend {
    /** The last rest brought it down about as far as the first. */
    Holding,

    /** The last rest brought it down markedly less: the session's fatigue showing. */
    Fading,
}

/**
 * A workout's recovery: how far the heart rate fell in each rest that was long enough to tell, over [seconds]
 * of it, in the order they came.
 */
data class Recovery(val drops: List<Int>, val seconds: Int) {
    /** The usual drop, rounded. */
    val average: Int get() = drops.average().roundToInt()

    /** How the last drop stands against the first; null with fewer than [TREND_RESTS] to compare. */
    val trend: RecoveryTrend?
        get() {
            if (drops.size < TREND_RESTS) return null
            val first = drops.first()
            val last = drops.last()
            val fading = first > 0 && last <= first * FADING_SHARE && first - last >= FADING_BPM
            return if (fading) RecoveryTrend.Fading else RecoveryTrend.Holding
        }

    companion object {
        const val TREND_RESTS = 3
        private const val FADING_SHARE = 0.75f
        private const val FADING_BPM = 5
    }
}

/**
 * The [Recovery] in [samples] over [rests], each a rest's first and last second counted from the first sample:
 * the drop over a minute of each rest that long, else over the half minute of one that long, and nothing from a
 * shorter one. All the drops are over the same span, the minute when any rest allows it. Null when no rest
 * gave one.
 */
fun recovery(samples: List<Int>, rests: List<IntRange>, sampleSeconds: Int = HR_SAMPLE_SECONDS): Recovery? {
    fun drops(seconds: Int): List<Int> = rests.mapNotNull { rest ->
        val length = rest.last - rest.first + 1
        if (length < seconds) return@mapNotNull null
        // The sample the work ended in is the one before the rest's first.
        dropAfter(samples, rest.first / sampleSeconds - 1, seconds, sampleSeconds = sampleSeconds)
    }
    val long = drops(RECOVERY_LONG_SECONDS)
    if (long.isNotEmpty()) return Recovery(long, RECOVERY_LONG_SECONDS)
    return drops(RECOVERY_SHORT_SECONDS).takeIf { it.isNotEmpty() }?.let { Recovery(it, RECOVERY_SHORT_SECONDS) }
}

/**
 * What [samples] cost the heart, as one number to set a day against another: the minutes in each zone times the
 * zone's number, summed (Edwards' training impulse). An hour easy is 60, a hard half hour of intervals about 100.
 */
fun heartLoad(
    samples: List<Int>,
    maxBpm: Int = DEFAULT_MAX_BPM,
    sampleSeconds: Int = HR_SAMPLE_SECONDS,
    restingBpm: Int? = null,
): Int = zoneSeconds(samples, maxBpm, sampleSeconds, restingBpm).entries
    .sumOf { (zone, seconds) -> zone.number * seconds } / SECONDS_PER_MINUTE

/**
 * The highest of [samples] when it is over [maxBpm] and still a heart rate a person has: the max heart rate
 * to offer in place of the one set. A sample is five seconds' average, so one stray beat is never it.
 */
fun peakAbove(samples: List<Int>, maxBpm: Int): Int? =
    samples.filter { it in HeartRate.BPM_RANGE }.maxOrNull()?.takeIf { it > maxBpm && it in MAX_BPM_RANGE }

const val RECOVERY_LONG_SECONDS = 60
const val RECOVERY_SHORT_SECONDS = 30
private const val SECONDS_PER_MINUTE = 60
