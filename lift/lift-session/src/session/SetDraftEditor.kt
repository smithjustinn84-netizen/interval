package dev.justinsmith.lift.session

import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.CustomExercise
import dev.justinsmith.lift.domain.DEFAULT_BELL_KG
import dev.justinsmith.lift.domain.Equipment
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.other
import dev.justinsmith.lift.domain.steppedBand
import dev.justinsmith.lift.domain.steppedKg
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the draft is worked out against, as things stand when it changes: the [bells] the sheet steps through
 * (the user's, or every standard size), their [bar], the [bands] on offer, the sets [logged] this session, every
 * set before them oldest first ([history], [logged] at its end), and the exercises the user [made].
 */
data class DraftContext(
    val bells: List<Double>,
    val bar: Double?,
    val bands: List<Band>,
    val history: List<SetLog>,
    val logged: List<SetLog>,
    val made: List<CustomExercise>,
)

/**
 * The Log sheet's draft: the set it will log, kept in range, starting a lift picked from what it was last done
 * with, stepping its load through what the user has; and a set logged opened on it to change, the draft for a new
 * set put aside meanwhile. Pure: what it goes by comes from [context], read at each change.
 */
class SetDraftEditor(private val context: () -> DraftContext) {
    private val current = MutableStateFlow(SetDraft())
    val draft: StateFlow<SetDraft> = current.asStateFlow()

    private val changing = MutableStateFlow<SetLog?>(null)

    /** A set logged, open on the sheet to change: the draft is it until saved or closed. */
    val editing: StateFlow<SetLog?> = changing.asStateFlow()

    /** The draft for a new set, put aside while a set logged is changed. */
    private var putAside: SetDraft? = null

    /** What was up next when the sheet last opened on it. */
    private var openedOn: UpNext? = null

    /** The user's exercises when the sheet went to add one: the first not among them is the one added. */
    private var awaitingAdd: Set<String>? = null

    /**
     * The draft a session opens on: [restored], the sheet as a killed process left it, else from the [last] set
     * logged, the other side for a one-sided lift; either way from the lightest of the user's [owned] bells at or
     * above the default.
     */
    fun start(owned: List<Double>, restored: SetDraft?, last: SetLog?) {
        val bell = owned.firstOrNull { it >= DEFAULT_BELL_KG } ?: owned.lastOrNull()
        val fresh = bell?.let { SetDraft(bellKg = it) } ?: SetDraft()
        current.value = restored ?: last?.let { fresh.from(it, flipSide = true) } ?: fresh
    }

    fun update(transform: (SetDraft) -> SetDraft) {
        val context = context()
        current.update { before ->
            val changed = transform(before)
            // A lift picked starts from what it was last done with: a snatch is no deadlift, a press no swing.
            val suggested =
                if (changed.exercise != before.exercise) {
                    changed.suggested(context.history, context.bar, context.bands)
                } else {
                    changed
                }
            suggested.clamped(context.bar)
        }
    }

    /**
     * Steps the load one [step] (−1 lighter, +1 heavier): the next of the user's bells, the bar's smallest pair of
     * plates, the next weight added to bodyweight, or the next band. Nothing on the mat, or at either end.
     */
    fun stepLoad(step: Int) {
        val (bells, bar, bands) = context()
        update { d ->
            when (val equipment = d.exercise.equipment) {
                Equipment.Kettlebell -> steppedKg(equipment, d.bellKg, step, bells, bar)?.let { d.copy(bellKg = it) }
                Equipment.Barbell -> steppedKg(equipment, d.barKg, step, bells, bar)?.let { d.copy(barKg = it) }
                Equipment.Bodyweight -> steppedKg(equipment, d.addedKg, step, bells, bar)?.let { d.copy(addedKg = it) }
                Equipment.Band -> steppedBand(d.band, step, bands)?.let { d.copy(band = it) }
                Equipment.Mat -> null
            } ?: d
        }
    }

    /**
     * The sheet opening on a program day: it starts on [next], as planned, unless it was opened on that same set
     * before and may have been changed since; or with [again], whatever it was opened on.
     */
    fun openOn(next: UpNext, again: Boolean = false) {
        if (changing.value != null || (next == openedOn && !again)) return
        openedOn = next
        current.update { it.planned(next) }
    }

    /** A one-sided lift usually goes on with the other side. */
    fun flipSide(set: SetDraft) {
        if (set.exercise.sided(set.doubled)) current.update { it.copy(side = set.side.other) }
    }

    /** Opens the set [id] logged this session to change: the draft becomes it, the one for a new set put aside. */
    fun edit(id: Long) {
        val set = context().logged.firstOrNull { it.id == id } ?: return
        if (changing.value == null) putAside = current.value
        changing.value = set
        current.update { it.from(set, flipSide = false) }
    }

    /** The set being changed as the draft now has it, ending the change; null when none is. */
    fun takeEdit(): SetLog? {
        val set = changing.value ?: return null
        val changed = current.value.appliedTo(set)
        endEdit()
        return changed
    }

    /** Leaves the set being changed as it was; the draft for a new set comes back. */
    fun endEdit() {
        if (changing.value == null) return
        changing.value = null
        putAside?.let { current.value = it }
        putAside = null
    }

    /** The sheet is off to add an exercise: the first the user then has that they did not is picked. */
    fun expectNewExercise() {
        awaitingAdd = context().made.map { it.uid }.toSet()
    }

    /** The user's exercises, as they change: one added since [expectNewExercise] is the one the sheet goes on with. */
    fun exercisesChanged(made: List<CustomExercise>) {
        val before = awaitingAdd ?: return
        val added = made.firstOrNull { it.uid !in before } ?: return
        awaitingAdd = null
        update { it.copy(exercise = added) }
    }
}
