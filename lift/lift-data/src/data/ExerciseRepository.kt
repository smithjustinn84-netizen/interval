package dev.justinsmith.lift.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.platform.IdGenerator
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.storage.SerialWriter
import dev.justinsmith.interval.storage.orEmptyOnError
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.ExerciseDraft
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/**
 * The exercises the user added, archived ones too: Lift's [ExerciseStore]. Read with the database, on [scope],
 * and written through [writer], the queue every Lift repository shares.
 */
class ExerciseRepository(
    private val dao: CustomExerciseDao,
    private val writer: SerialWriter,
    private val clock: WallClock,
    private val ids: IdGenerator,
    scope: CoroutineScope,
) : ExerciseStore {
    private val log = Logger.withTag("ExerciseRepository")

    /**
     * Every exercise the user added, as stored, from the first read on: what the sessions and programs name theirs
     * by, shared so each reads the one list. Unlike [customExercises], it shows nothing until read.
     */
    internal val made: SharedFlow<List<CustomExercise>> =
        dao.observeAll().orEmptyOnError("exercises", log)
            .map { rows -> rows.mapNotNull { it.toDomain() } }
            .shareIn(scope, SharingStarted.Eagerly, replay = 1)

    private val _customExercises = MutableStateFlow(emptyList<CustomExercise>())

    /** The exercises the user added, archived ones too, by name. */
    override val customExercises: StateFlow<List<CustomExercise>> = _customExercises.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** True once [customExercises] has been read. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        scope.launch {
            made.collect {
                _customExercises.value = it
                _ready.value = true
            }
        }
    }

    /**
     * Saves [draft] as the exercise [uid], or as a new one with null; returns its uid. Its name is kept as
     * written, trimmed, and an archived one stays archived.
     */
    override fun saveExercise(uid: String?, draft: ExerciseDraft): String {
        val id = uid ?: ids.newId()
        val made = CustomExercise(
            id,
            draft.name.trim(),
            draft.equipment,
            draft.measure,
            draft.perSide,
            draft.focus,
            draft.cues.trim(),
            archived = _customExercises.value.any { it.uid == id && it.archived },
        )
        writer.persist { dao.upsert(made.toEntity(clock.nowMillis())) }
        return id
    }

    /**
     * Archives the user's exercise [uid], which the Log sheet then no longer offers, or with false brings it
     * back. Its sets keep it either way.
     */
    override fun setArchived(uid: String, archived: Boolean) {
        val made = _customExercises.value.firstOrNull { it.uid == uid } ?: return
        writer.persist { dao.upsert(made.copy(archived = archived).toEntity(clock.nowMillis())) }
    }
}
