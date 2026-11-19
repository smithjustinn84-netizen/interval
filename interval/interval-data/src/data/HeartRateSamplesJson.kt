package dev.justinsmith.interval.data

import kotlinx.serialization.json.Json

internal fun encodeHeartRateSamples(samples: List<Int>?): String? =
    samples?.takeIf { it.isNotEmpty() }?.let { Json.encodeToString(it) }

internal fun decodeHeartRateSamples(json: String?): List<Int>? {
    if (json.isNullOrBlank()) return null
    return runCatching { Json.decodeFromString<List<Int>>(json) }.getOrNull()?.takeIf { it.isNotEmpty() }
}
