package dev.justinsmith.interval.platform

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Makes the id a new record is stored under, injected so that tests can know it. */
fun interface IdGenerator {
    fun newId(): String

    companion object {
        /** A random UUID as 32 hex digits, the form every stored uid takes. */
        @OptIn(ExperimentalUuidApi::class)
        val Random = IdGenerator { Uuid.random().toHexString() }
    }
}
