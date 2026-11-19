package dev.justinsmith.interval.timer

import dev.justinsmith.interval.model.ClassRound
import dev.justinsmith.interval.model.ClassSample
import dev.justinsmith.interval.model.SportClass.Companion.SAMPLE_SECONDS
import kotlin.math.sqrt

// The detector's numbers are a first reading, to be tuned on classes recorded on the mat.

/** A round is at least this long: anything shorter is a drill or a scramble to the water. */
const val MIN_ROUND_SECONDS = 120

/** A lull shorter than this neither ends a round nor splits one. */
const val ROUND_GAP_SECONDS = 30

/** The class's heart rate must range at least this far for any of it to stand out as a round. */
private const val MIN_SPREAD_BPM = 20f

/** The class's easy and hard heart rates: these shares of its samples lie below each. */
private const val LOW_SHARE = 0.2f
private const val HIGH_SHARE = 0.95f

/** How far from easy to hard the heart rate must be to count as working. */
private const val EFFORT_SHARE = 0.5f

/** The arm's still and busy movement, and how far from still to busy still counts as still. */
private const val STILL_SHARE = 0.1f
private const val BUSY_SHARE = 0.9f
private const val STILL_REACH = 0.25f

/** Busy must be this many times still for the movement to say anything. */
private const val MOVEMENT_CONTRAST = 2f

/** Samples either side of one that it is smoothed over. */
private const val SMOOTH_REACH = 1

private const val RECOVERY_SHORT_SECONDS = 30
private const val RECOVERY_LONG_SECONDS = 60
private const val HALF = 0.5f

/**
 * The rounds of sparring in a class's [samples]: the stretches of [MIN_ROUND_SECONDS] or more with the heart rate
 * in the upper half of the class's own range. A dip shorter than [ROUND_GAP_SECONDS] is part of its round. A minute
 * between rounds rarely lets the heart rate fall that far, so a round also ends where the arm goes still for
 * [ROUND_GAP_SECONDS], when the recording has movement to go by. A class at one steady effort has no rounds.
 */
fun detectRounds(samples: List<ClassSample>): List<ClassRound> {
    val bpm = smoothed(samples.map { it.bpm.toFloat() })
    val read = bpm.filter { it > 0f }.sorted()
    val minSamples = MIN_ROUND_SECONDS / SAMPLE_SECONDS
    if (read.size < minSamples) return emptyList()
    val low = read.share(LOW_SHARE)
    val high = read.share(HIGH_SHARE)
    if (high - low < MIN_SPREAD_BPM) return emptyList()
    val threshold = low + (high - low) * EFFORT_SHARE
    val gap = ROUND_GAP_SECONDS / SAMPLE_SECONDS

    val working = BooleanArray(samples.size) { bpm[it] >= threshold }
    // A short dip is bridged, unless it is the recording's start or end.
    working.runs(of = false).filter { it.first > 0 && it.last < samples.lastIndex && it.count() < gap }
        .forEach { run -> run.forEach { working[it] = true } }
    val still = stillness(samples)
    val lulls = BooleanArray(samples.size) { working[it] && still[it] }
    lulls.runs(of = true).filter { it.count() >= gap }.forEach { run -> run.forEach { working[it] = false } }

    val found = working.runs(of = true).filter { it.count() >= minSamples }
    return found.mapIndexed { index, run ->
        val next = found.getOrNull(index + 1)?.first ?: samples.size
        round(samples, run, next)
    }
}

private fun round(samples: List<ClassSample>, run: IntRange, next: Int): ClassRound {
    val read = samples.subList(run.first, run.last + 1).filter { it.read }
    val end = read.lastOrNull()?.bpm

    fun drop(seconds: Int): Int? {
        val at = run.last + seconds / SAMPLE_SECONDS
        val then = samples.getOrNull(at)?.takeIf { at <= next && it.read } ?: return null
        return end?.let { it - then.bpm }
    }
    return ClassRound(
        startSecond = run.first * SAMPLE_SECONDS,
        endSecond = (run.last + 1) * SAMPLE_SECONDS,
        averageBpm = if (read.isEmpty()) 0 else (read.sumOf { it.bpm }.toFloat() / read.size + HALF).toInt(),
        peakBpm = read.maxOfOrNull { it.bpm } ?: 0,
        drop30 = drop(RECOVERY_SHORT_SECONDS),
        drop60 = drop(RECOVERY_LONG_SECONDS),
    )
}

/** Where the arm is still, by the class's own movement; nowhere when the movement hardly varies. */
private fun stillness(samples: List<ClassSample>): BooleanArray {
    val movement = smoothed(samples.map { it.movement }, missing = -1f)
    val sorted = movement.sorted()
    if (sorted.isEmpty()) return BooleanArray(0)
    val low = sorted.share(STILL_SHARE)
    val high = sorted.share(BUSY_SHARE)
    if (high <= 0f || high < low * MOVEMENT_CONTRAST) return BooleanArray(samples.size)
    val threshold = low + (high - low) * STILL_REACH
    return BooleanArray(samples.size) { movement[it] < threshold }
}

/** Each value averaged with its neighbours, leaving out any that are [missing]; one with none left stays missing. */
private fun smoothed(values: List<Float>, missing: Float = 0f): List<Float> = values.indices.map { index ->
    val near = (index - SMOOTH_REACH..index + SMOOTH_REACH).mapNotNull { values.getOrNull(it) }.filter { it != missing }
    if (near.isEmpty()) missing else near.sum() / near.size
}

/** The value [share] of the way up a sorted list. */
private fun List<Float>.share(share: Float): Float = this[((size - 1) * share + HALF).toInt()]

/** The stretches of [of] in the array, in order. */
private fun BooleanArray.runs(of: Boolean): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = -1
    for (index in indices) {
        if (this[index] == of) {
            if (start < 0) start = index
        } else if (start >= 0) {
            runs += start until index
            start = -1
        }
    }
    if (start >= 0) runs += start until size
    return runs
}

/**
 * A class as it is kept, from what a sensor recorded: [bpm] a reading every [bpmSeconds] (0 where it read none),
 * and [magnitudes], the accelerometer's magnitude in g at [accelerometerHz], empty when it recorded none. Each
 * [ClassSample] is the mean heart rate of its [SAMPLE_SECONDS] and the spread of the magnitude over them.
 */
fun classSamples(
    bpm: List<Int>,
    magnitudes: List<Float> = emptyList(),
    accelerometerHz: Int = 0,
    bpmSeconds: Int = 1,
): List<ClassSample> {
    val perSample = (SAMPLE_SECONDS / bpmSeconds.coerceAtLeast(1)).coerceAtLeast(1)
    val perWindow = accelerometerHz * SAMPLE_SECONDS
    return bpm.chunked(perSample).mapIndexed { index, chunk ->
        val read = chunk.filter { it > 0 }
        val from = (index * perWindow).coerceAtMost(magnitudes.size)
        val window = magnitudes.subList(from, (from + perWindow).coerceAtMost(magnitudes.size))
        ClassSample(
            bpm = if (read.isEmpty()) 0 else (read.sum().toFloat() / read.size + HALF).toInt(),
            movement = spread(window),
        )
    }
}

private fun spread(values: List<Float>): Float {
    if (values.size < 2) return 0f
    val mean = values.sum() / values.size
    return sqrt(values.sumOf { ((it - mean) * (it - mean)).toDouble() }.toFloat() / values.size)
}
