@file:OptIn(ExperimentalCoroutinesApi::class)

package dev.justinsmith.interval.workout

import dev.justinsmith.interval.fakes.FakeLiveHeartRate
import dev.justinsmith.interval.fakes.FakeStorage
import dev.justinsmith.interval.fakes.FixedTimestampFormatter
import dev.justinsmith.interval.fakes.MainDispatcherTest
import dev.justinsmith.interval.fakes.RecordingBackgroundWorkout
import dev.justinsmith.interval.fakes.RecordingCuePlayer
import dev.justinsmith.interval.fakes.RecordingHealthStore
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.platform.HealthStatus
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.platform.LiveHeartRateState
import dev.justinsmith.interval.timer.IntervalConfig
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutSessionHeartRateTest : MainDispatcherTest(StandardTestDispatcher()) {
    private var now = 10_000L
    private val repository = FakeStorage().repository(now = { now })
    private val lifts = FakeLiftStorage().repository()
    private val player = RecordingCuePlayer()
    private val background = RecordingBackgroundWorkout()
    private val health = RecordingHealthStore(HealthStatus.Connected)
    private val liveHr = FakeLiveHeartRate(LiveHeartRateState.Idle)
    private val tickers = mutableListOf<WorkoutTicker>()

    @AfterTest
    fun stop() {
        tickers.forEach(WorkoutTicker::close)
    }

    private fun TestScope.newSession(): WorkoutSession {
        repository.settings.updateConfig {
            IntervalConfig(prepSeconds = 0, workSeconds = 10, restSeconds = 5, rounds = 1)
        }
        return WorkoutSession(
            repository.settings,
            repository.settings,
            player,
            { now },
            background,
            WorkoutRecorder(
                repository.history,
                repository.presets,
                repository.flows,
                repository.settings,
                lifts.sessions,
                FixedTimestampFormatter,
                { now },
                health,
            ),
            WorkoutTicker(testScheduler.timeSource, CoroutineScope(SupervisorJob() + coroutineContext))
                .also { tickers += it },
            repository.flows,
            repository.presets,
            liveHr,
        )
    }

    @Test
    fun `a workout that ends lets the stream go for itself alone`() = runTest {
        // Lift's session holds the same stream.
        liveHr.start("lift")
        val session = newSession()
        session.open(1L)
        runCurrent()
        assertEquals(2, liveHr.users.size)

        session.skip()
        runCurrent()

        assertEquals(setOf<Any>("lift"), liveHr.users)
    }

    @Test
    fun `streaming starts with workout records 5s samples to history and stops on finish`() = runTest {
        val session = newSession()
        session.open(1L)
        runCurrent()
        assertTrue(liveHr.started)

        // Emit readings across two 5-second buckets
        liveHr.emitBpm(120)
        runCurrent()
        now += 5_000L
        liveHr.emitBpm(168)
        runCurrent()

        // Skip to finish the 1-round workout
        session.skip()
        runCurrent()

        assertFalse(liveHr.started)
        val finished = session.state.value
        assertEquals(HeartRate(averageBpm = 144, peakBpm = 168), finished?.heartRate)
        assertEquals(listOf(120, 168), finished?.heartRateSamples)

        val saved = repository.history.history.value.single()
        assertEquals(144, saved.averageBpm)
        assertEquals(168, saved.peakBpm)
        assertEquals(listOf(120, 168), saved.heartRateSamples)
    }

    @Test
    fun `sustained zone transitions play zone beep and heartbeat haptic when enabled`() = runTest {
        repository.settings.updateAppSettings { it.copy(body = it.body.copy(maxHeartRate = 200)) }
        val session = newSession()
        session.open(1L)
        runCurrent()
        player.beeps.clear()
        player.haptics.clear()

        // Establish Recovery band
        liveHr.emitBpm(110)
        runCurrent()

        // Sustain High band (>= 160 bpm) for 5 seconds
        now += 1_000L
        liveHr.emitBpm(170)
        runCurrent()
        now += 5_000L
        liveHr.emitBpm(174)
        runCurrent()

        assertTrue(Beep.ZoneUp in player.beeps, "Expected Beep.ZoneUp in ${player.beeps}")
        assertTrue(Haptic.Heartbeat in player.haptics, "Expected Haptic.Heartbeat in ${player.haptics}")

        // Advance past cooldown (15s) and sustain Recovery band for 5 seconds
        player.beeps.clear()
        player.haptics.clear()
        now += 16_000L
        liveHr.emitBpm(112)
        runCurrent()
        now += 5_000L
        liveHr.emitBpm(108)
        runCurrent()

        assertTrue(Beep.ZoneDown in player.beeps, "Expected Beep.ZoneDown in ${player.beeps}")
        assertTrue(Haptic.Heartbeat in player.haptics, "Expected Haptic.Heartbeat in ${player.haptics}")

        session.leave()
        assertFalse(liveHr.started)
    }

    @Test
    fun `zone alerts are suppressed when heartRateAlerts is turned off`() = runTest {
        repository.settings.updateAppSettings { it.copy(body = it.body.copy(maxHeartRate = 200)) }
        repository.settings.updateCueSettings { it.copy(heartRateAlerts = false) }
        val session = newSession()
        session.open(1L)
        runCurrent()
        player.beeps.clear()
        player.haptics.clear()

        liveHr.emitBpm(110)
        runCurrent()
        now += 1_000L
        liveHr.emitBpm(172)
        runCurrent()
        now += 6_000L
        liveHr.emitBpm(176)
        runCurrent()

        assertFalse(Beep.ZoneUp in player.beeps)
        assertFalse(Haptic.Heartbeat in player.haptics)
        session.leave()
    }
}
