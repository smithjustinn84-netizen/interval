package dev.justinsmith.interval.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.model.ClassSample
import dev.justinsmith.interval.model.Sport
import dev.justinsmith.interval.model.SportClass
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.timer.detectRounds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// A class's samples as the database keeps them: one JSON object in a column, the heart rates and the movement each
// a list, in step.

private val json = Json { ignoreUnknownKeys = true }

/** A day of samples, far past any class: what a row is cut to. */
private const val MAX_SAMPLES = 24 * 60 * 60 / SportClass.SAMPLE_SECONDS

@Serializable
internal class StoredSamples(val bpm: List<Int> = emptyList(), val movement: List<Float> = emptyList())

/** [samples] as the text a column holds. */
internal fun encodeSamples(samples: List<ClassSample>): String =
    json.encodeToString(StoredSamples(samples.map { it.bpm }, samples.map { it.movement }))

/** The samples [text] holds, each heart rate one a heart can have or 0 for none; none when it can't be read. */
internal fun decodeSamples(text: String): List<ClassSample> =
    runCatching { json.decodeFromString<StoredSamples>(text) }
        .onFailure { Logger.withTag("Classes").w(it) { "Couldn't read a class's samples" } }
        .getOrNull()
        ?.let { stored ->
            stored.bpm.take(MAX_SAMPLES).mapIndexed { index, bpm ->
                val movement = stored.movement.getOrNull(index)?.takeIf { it.isFinite() && it > 0f } ?: 0f
                ClassSample(bpm.takeIf { it in HeartRate.BPM_RANGE } ?: 0, movement)
            }
        }
        .orEmpty()

/** The class this row is, its rounds found afresh; null for a sport this build doesn't know or one with no sample. */
internal fun SportClassEntity.toDomain(): SportClass? {
    val sport = sportOf(sport, name) ?: return null
    val samples = decodeSamples(samples)
    if (samples.isEmpty()) return null
    return SportClass(id, sport, epochDay, startedAtEpochMillis, samples, detectRounds(samples))
}

internal fun classEntity(sport: Sport, epochDay: Long, startedAtEpochMillis: Long, samples: List<ClassSample>) =
    SportClassEntity(
        epochDay = epochDay,
        sport = sport.key,
        name = sport.storedName,
        startedAtEpochMillis = startedAtEpochMillis,
        samples = encodeSamples(samples),
    )
