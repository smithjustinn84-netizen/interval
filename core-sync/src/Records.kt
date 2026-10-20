@file:OptIn(ExperimentalSerializationApi::class)

package dev.justinsmith.core.sync

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** The apps that share workouts. [packageId] is the Android application id and the iOS bundle id. */
@Serializable
enum class AppId(val packageId: String) {
    @SerialName("interval")
    Interval("dev.justinsmith.interval"),

    @SerialName("bells")
    Bells("dev.justinsmith.kettlebell"),
    ;

    /** The other app of the pair. */
    val sibling: AppId get() = if (this == Interval) Bells else Interval
}

/**
 * One finished workout as its app shares it. Only the app named by [source] changes it; a sibling keeps a
 * copy. Everything a sibling needs to list it, count it and estimate its calories is top level, so a
 * workout of a kind it doesn't know ([detail] it can't read) still shows.
 */
@Serializable
data class SessionRecord(
    /** 32 hex digits, never reused. */
    val uid: String,
    val source: AppId,
    val finishedAtEpochMillis: Long,
    val elapsedSeconds: Int,
    /** Of [elapsedSeconds], the time spent working, when the app knows it. */
    val activeSeconds: Int? = null,
    /** Interval's activity by name ("Kettlebell", "Rower"), for calorie estimates; null for none. */
    val activity: String? = null,
    /** What the row is called, in the source app's words: a preset's name, a program day. */
    val title: String,
    /** The source app's own description of it: a [SessionDetail], kept whole. */
    val detail: JsonObject = JsonObject(emptyMap()),
    /** When it was recorded or last changed. */
    val updatedAtEpochMillis: Long,
    /** A deleted workout stays as a tombstone until the sibling has seen it go. */
    val deleted: Boolean = false,
) {
    /** [detail] as a [SessionDetail], or null for a kind this build doesn't know. */
    fun typedDetail(): SessionDetail? = runCatching {
        FeedCodec.json.decodeFromJsonElement(SessionDetail.serializer(), detail)
    }
        .getOrNull()
}

/** Each app's own description of a workout. */
@Serializable
sealed interface SessionDetail {
    /** An interval workout, as Interval ran it. */
    @Serializable
    @SerialName("interval")
    data class Interval(
        val prepSeconds: Int,
        val workSeconds: Int,
        val restSeconds: Int,
        val rounds: Int,
        val endedEarly: Boolean = false,
    ) : SessionDetail

    /** A kettlebell session, as Bells logged it. */
    @Serializable
    @SerialName("kettlebell")
    data class Kettlebell(val sets: List<SetRecord>, val tonnageKg: Double) : SessionDetail

    /** As [SessionRecord.detail]. */
    fun toJson(): JsonObject = FeedCodec.json.encodeToJsonElement(serializer(), this).jsonObject
}

/** One logged set: [reps] of [exercise] with a [bellKg] bell, on one [side] or both (null). */
@Serializable
data class SetRecord(val exercise: String, val bellKg: Double, val reps: Int, val side: String? = null)

/** Everything one app shares: its own workouts, tombstones included. */
@Serializable
data class FeedSnapshot(
    // Always written, default or not, so a reader can tell which schema it has.
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val schemaVersion: Int = FeedCodec.SCHEMA_VERSION,
    val source: AppId,
    val sessions: List<SessionRecord>,
)

/** Turns a [FeedSnapshot] into the text an app publishes, and back. */
object FeedCodec {
    /** Raised only when a change would break an older reader; adding fields does not. */
    const val SCHEMA_VERSION = 1

    internal val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
    }

    fun encode(snapshot: FeedSnapshot): String = json.encodeToString(FeedSnapshot.serializer(), snapshot)

    /** The snapshot in [text], or null when it is unreadable or from a newer schema than this build knows. */
    fun decode(text: String): FeedSnapshot? = runCatching { json.decodeFromString(FeedSnapshot.serializer(), text) }
        .getOrNull()
        ?.takeIf { it.schemaVersion <= SCHEMA_VERSION }
}
