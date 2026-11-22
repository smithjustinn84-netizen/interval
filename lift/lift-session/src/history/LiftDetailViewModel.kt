package dev.justinsmith.lift.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HealthStore
import dev.justinsmith.interval.platform.TimestampFormatter
import dev.justinsmith.lift.data.GearStore
import dev.justinsmith.lift.data.IntervalActivity
import dev.justinsmith.lift.data.ProgramStore
import dev.justinsmith.lift.data.SessionStore
import dev.justinsmith.lift.data.SetStore
import dev.justinsmith.lift.domain.Band
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PerceivedEffort
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.Program
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.newRecords
import dev.justinsmith.lift.domain.offeredBands
import dev.justinsmith.lift.domain.shippedPrograms
import dev.justinsmith.lift.domain.standardBells
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One finished session, or null once it is gone (deleted here or never there), and when it finished. */
data class LiftDetailUiState(
    val session: LiftSession? = null,
    /** As the platform formats them: "Sunday, Oct 11" and "7:42 AM". */
    val date: String = "",
    val time: String = "",
    /** The records it set, against every session finished before it. */
    val records: List<PersonalRecord> = emptyList(),
    /** The max heart rate its zones are worked out from: the one set in Settings, or the default. */
    val maxHeartRate: Int = DEFAULT_MAX_BPM,
    /** The program it was a day of, while that program is still there. */
    val program: Program? = null,
    /** Gear offered when editing a logged set on the detail page. */
    val bells: List<Double> = standardBells,
    val ownsBells: Boolean = false,
    val bar: Double? = null,
    val bands: List<Band> = offeredBands(emptyList()),
)

private data class GearSnapshot(
    val bells: List<Double>,
    val bar: Double?,
    val bands: List<Band>,
)

/** The session stored under [uid], as History opens it, with inline editing and deletion. */
class LiftDetailViewModel(
    private val uid: String,
    private val sessionStore: SessionStore,
    private val setStore: SetStore,
    private val gearStore: GearStore,
    private val programStore: ProgramStore,
    private val timestamps: TimestampFormatter,
    private val health: HealthStore,
    private val intervals: IntervalActivity,
) : ViewModel() {
    private val gear = combine(gearStore.bells, gearStore.bar, gearStore.bands, ::GearSnapshot)

    val state: StateFlow<LiftDetailUiState> =
        combine(sessionStore.sessions, programStore.customPrograms, intervals.maxHeartRate, gear, ::build)
            .stateIn(
                viewModelScope,
                SharingStarted.Eagerly,
                build(
                    sessionStore.sessions.value,
                    programStore.customPrograms.value,
                    intervals.maxHeartRate.value,
                    GearSnapshot(gearStore.bells.value, gearStore.bar.value, gearStore.bands.value),
                ),
            )

    /**
     * Reads the session's heart rate from the health store as the page comes into view, unless it has one, and keeps
     * it with the session once found: a watch may have synced since the summary. Only the page on show may read.
     */
    fun readHeartRate() {
        val session = sessionStore.sessions.value.firstOrNull { it.uid == uid }?.takeIf { it.averageBpm == null }
        val end = session?.finishedAtEpochMillis ?: return
        viewModelScope.launch {
            val heartRate = health.heartRate(session.startedAtEpochMillis, end) ?: return@launch
            sessionStore.keepHeartRate(uid, heartRate)
        }
    }

    /** How hard the session felt, edited here after its summary; null clears it. */
    fun setEffort(effort: PerceivedEffort?) {
        val session = sessionStore.sessions.value.firstOrNull { it.uid == uid } ?: return
        sessionStore.reflectOnSession(uid, effort, session.note.orEmpty())
    }

    /** A note on the session, edited here after its summary; blank clears it. */
    fun setNote(note: String) {
        val session = sessionStore.sessions.value.firstOrNull { it.uid == uid } ?: return
        sessionStore.reflectOnSession(uid, session.effort, note)
    }

    /** Updates one logged set in this session: its load, reps or seconds, and side. */
    fun updateSet(set: SetLog) {
        setStore.updateSet(set)
    }

    /**
     * Removes one logged set from this session, returning it for Undo ([restoreSet]); null when not found or
     * when it is the session's only remaining set.
     */
    fun removeSet(id: Long): SetLog? {
        val session = sessionStore.sessions.value.firstOrNull { it.uid == uid } ?: return null
        if (session.sets.size <= 1) return null
        val set = session.sets.firstOrNull { it.id == id } ?: return null
        setStore.removeSet(id)
        return set
    }

    /** Brings back a set removed by [removeSet]: Undo. */
    fun restoreSet(set: SetLog) {
        setStore.restoreSetTo(uid, set)
    }

    fun delete() {
        sessionStore.deleteSession(uid)
    }

    fun restore() {
        sessionStore.restoreSession(uid)
    }

    private fun build(
        sessions: List<LiftSession>,
        custom: List<Program>,
        maxHeartRate: Int?,
        gear: GearSnapshot,
    ): LiftDetailUiState {
        val session = sessions.firstOrNull { it.uid == uid } ?: return LiftDetailUiState()
        val at = session.finishedAtEpochMillis ?: session.startedAtEpochMillis
        // Newest first, so the ones finished before it are those after it in the list.
        val before = sessions.dropWhile { it.uid != uid }.drop(1).flatMap { it.sets }
        return LiftDetailUiState(
            session = session,
            date = timestamps.date(at),
            time = timestamps.time(at),
            records = newRecords(before, session),
            maxHeartRate = maxHeartRate ?: DEFAULT_MAX_BPM,
            program = session.program?.let { key -> (shippedPrograms + custom).firstOrNull { it.key == key } },
            bells = gear.bells.ifEmpty { standardBells },
            ownsBells = gear.bells.isNotEmpty(),
            bar = gear.bar,
            bands = offeredBands(gear.bands),
        )
    }
}
