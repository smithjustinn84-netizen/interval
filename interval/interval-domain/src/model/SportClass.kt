package dev.justinsmith.interval.model

/**
 * A stretch of a class as a sensor on the arm read it: the heart rate, and how much the arm moved (the spread of
 * the accelerometer's magnitude over the stretch, in g). A [bpm] of 0 is a stretch the sensor read nothing in.
 */
data class ClassSample(val bpm: Int, val movement: Float) {
    val read: Boolean get() = bpm > 0
}

/**
 * A round of sparring found in a class ([dev.justinsmith.interval.timer.detectRounds]): where it sits in the
 * recording, its heart rate, and how far that fell in the 30 and 60 seconds after it, null where the next round
 * or the recording's end came first.
 */
data class ClassRound(
    val startSecond: Int,
    val endSecond: Int,
    val averageBpm: Int,
    val peakBpm: Int,
    val drop30: Int? = null,
    val drop60: Int? = null,
) {
    val seconds: Int get() = endSecond - startSecond
}

/**
 * What a sensor hands over as a class is synced: when it started, and a [ClassSample] every
 * [SportClass.SAMPLE_SECONDS].
 */
data class ClassRecording(val startedAtEpochMillis: Long, val samples: List<ClassSample>) {
    val seconds: Int get() = samples.size * SportClass.SAMPLE_SECONDS

    val endedAtEpochMillis: Long get() = startedAtEpochMillis + seconds * MILLIS_PER_SECOND
}

/**
 * A class of [sport] recorded on a sensor and kept beside the day it was logged on ([epochDay], as a
 * [LoggedSport]'s): its samples, and the [rounds] found in them as it was synced.
 */
data class SportClass(
    val id: Long,
    val sport: Sport,
    val epochDay: Long,
    val startedAtEpochMillis: Long,
    val samples: List<ClassSample>,
    val rounds: List<ClassRound>,
) {
    val seconds: Int get() = samples.size * SAMPLE_SECONDS

    val endedAtEpochMillis: Long get() = startedAtEpochMillis + seconds * MILLIS_PER_SECOND

    /** The whole class's heart rate, null when the sensor read none. */
    val averageBpm: Int? get() = samples.filter { it.read }.takeIf { it.isNotEmpty() }?.let { read ->
        (read.sumOf { it.bpm }.toDouble() / read.size + HALF).toInt()
    }

    val peakBpm: Int? get() = samples.filter { it.read }.maxOfOrNull { it.bpm }

    companion object {
        /** How long a [ClassSample] covers. */
        const val SAMPLE_SECONDS = 5
    }
}

private const val MILLIS_PER_SECOND = 1000L
private const val HALF = 0.5
