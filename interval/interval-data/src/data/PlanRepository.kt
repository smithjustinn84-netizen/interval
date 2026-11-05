package dev.justinsmith.interval.data

import co.touchlab.kermit.Logger
import dev.justinsmith.interval.model.PlanSchedule
import dev.justinsmith.interval.model.PlanWorkout
import dev.justinsmith.interval.model.PlannedWorkout
import dev.justinsmith.interval.platform.WallClock
import dev.justinsmith.interval.storage.SerialWriter
import dev.justinsmith.interval.storage.orEmptyOnError
import dev.justinsmith.interval.timer.DAYS_PER_WEEK
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The planned workouts, weekly and one-off, which follow the database: the [PlanStore]. Written through [writer],
 * the queue every interval repository shares.
 */
class PlanRepository(
    private val plannedDao: PlannedDao,
    private val writer: SerialWriter,
    private val clock: WallClock,
    scope: CoroutineScope,
) : PlanStore {
    private val log = Logger.withTag("PlanRepository")

    private val _ready = MutableStateFlow(false)

    /** True once the plans have been read. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _planned = MutableStateFlow(emptyList<PlannedWorkout>())

    /** In the order they were planned. */
    override val planned: StateFlow<List<PlannedWorkout>> = _planned.asStateFlow()

    init {
        // Mapped upstream of its fallback, so a row that fails to map counts as a failed read.
        scope.launch {
            plannedDao.observeAll()
                .map { rows -> rows.mapNotNull { it.toDomain() } }
                .orEmptyOnError("plans", log)
                .collect { plans ->
                    _planned.value = plans
                    _ready.value = true
                }
        }
    }

    /** Plans [workout] for every week on each of [weekdays] (1 for Monday through 7): one plan per day. */
    override fun planWeekly(workout: PlanWorkout, weekdays: Set<Int>) {
        val days = weekdays.filter { it in 1..DAYS_PER_WEEK }.sorted()
        if (days.isEmpty()) return
        writer.persist { plannedDao.insert(days.map { workout.planned(PlanSchedule.Weekly(it)).toEntity() }) }
    }

    /** Plans [workout] once, on [epochDay]. */
    override fun planOnce(workout: PlanWorkout, epochDay: Long) {
        writer.persist { plannedDao.insert(listOf(workout.planned(PlanSchedule.Once(epochDay)).toEntity())) }
    }

    override fun removePlanned(id: Long) {
        writer.persist { plannedDao.delete(id) }
    }

    /** Puts a plan [removePlanned] took off back as it was, its id and the day it was made included: Undo. */
    override fun restorePlanned(plan: PlannedWorkout) {
        writer.persist { plannedDao.insert(listOf(plan.toEntity())) }
    }

    private fun PlanWorkout.planned(schedule: PlanSchedule) = PlannedWorkout(
        id = 0,
        workout = this,
        schedule = schedule,
        createdAtEpochMillis = clock.nowMillis(),
    )
}
