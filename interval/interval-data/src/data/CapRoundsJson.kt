package dev.justinsmith.interval.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.model.CapRound
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// The rounds done in a workout's time caps, as every store keeps them: one JSON list, in a column or the saved
// workout alike. Null is a workout whose rounds weren't counted, "[]" a cap with none done.

private val json = Json { ignoreUnknownKeys = true }

/** A [CapRound] as stored. */
@Serializable
internal class StoredCapRound(val block: Int, val atMillis: Long) {
    fun toDomain(): CapRound? = if (block >= 0 && atMillis >= 0) CapRound(block, atMillis) else null
}

internal fun CapRound.stored() = StoredCapRound(block, atMillis)

/** [rounds] as the text a column holds; null for none counted. */
internal fun encodeCapRounds(rounds: List<CapRound>?): String? =
    rounds?.let { json.encodeToString(it.map { round -> round.stored() }) }

/** The rounds [text] holds; null for none counted, or text that can't be read. */
internal fun decodeCapRounds(text: String?): List<CapRound>? {
    if (text == null) return null
    return runCatching { json.decodeFromString<List<StoredCapRound>>(text).mapNotNull { it.toDomain() } }
        .onFailure { Logger.withTag("CapRounds").w(it) { "Couldn't read a workout's rounds" } }
        .getOrNull()
}
