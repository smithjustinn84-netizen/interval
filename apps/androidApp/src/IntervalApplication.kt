package dev.justinsmith.interval

import android.app.Application
import androidx.glance.appwidget.updateAll
import co.touchlab.kermit.Logger
import dev.justinsmith.interval.data.WeekStandingSource
import dev.justinsmith.interval.di.initAppKoin
import dev.justinsmith.interval.widget.WeekWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Starts the app's container once per process, before any Activity, service or receiver runs, so all of them
 * share it and it outlives each Activity: backing out and reopening keeps the same databases open and the
 * same workout running. Then keeps the home-screen widget ([WeekWidget]) drawn as the week changes: a workout
 * recorded, a session finished, the goal moved. With none placed, drawing is nothing.
 */
class IntervalApplication : Application() {
    private val log = Logger.withTag("IntervalApplication")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        val koin = initAppKoin(this).koin
        scope.launch {
            koin.get<WeekStandingSource>().standing.filterNotNull().collect {
                runCatching { WeekWidget().updateAll(this@IntervalApplication) }
                    .onFailure { log.w(it) { "Couldn't draw the widget" } }
            }
        }
    }
}
