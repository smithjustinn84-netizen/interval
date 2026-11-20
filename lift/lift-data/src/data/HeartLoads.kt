package dev.justinsmith.lift.data

import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.heartLoad
import dev.justinsmith.lift.domain.HeartLoad
import dev.justinsmith.lift.domain.LiftSession

/**
 * What each of these sessions cost the heart, on the day [dayOf] its finish falls on: from the heart rate a
 * sensor read through it, zoned by [maxBpm] (null for none set) and [restingBpm]. A session with none read, or
 * not yet finished, gives none.
 */
fun List<LiftSession>.heartLoads(maxBpm: Int?, restingBpm: Int?, dayOf: (Long) -> Long): List<HeartLoad> =
    mapNotNull { session ->
        val finished = session.finishedAtEpochMillis ?: return@mapNotNull null
        val samples = session.heartRateSamples ?: return@mapNotNull null
        val load = heartLoad(samples, maxBpm ?: DEFAULT_MAX_BPM, restingBpm = restingBpm)
        HeartLoad(dayOf(finished), load).takeIf { load > 0 }
    }
