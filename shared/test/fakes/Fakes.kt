package dev.justinsmith.interval.fakes

import dev.justinsmith.interval.data.KeyValueStore
import dev.justinsmith.interval.di.appModules
import dev.justinsmith.interval.platform.Beep
import dev.justinsmith.interval.platform.CuePlayer
import dev.justinsmith.interval.platform.Haptic
import dev.justinsmith.interval.platform.TimestampFormatter
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication
import org.koin.dsl.module

/** An in-memory [KeyValueStore]. */
class FakeKeyValueStore(val values: MutableMap<String, String> = mutableMapOf()) : KeyValueStore {
    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }
}

/** A [CuePlayer] that plays nothing and remembers every cue it was asked for. */
class RecordingCuePlayer : CuePlayer {
    val beeps = mutableListOf<Beep>()
    val spoken = mutableListOf<String>()
    val haptics = mutableListOf<Haptic>()

    override fun beep(beep: Beep) {
        beeps += beep
    }

    override fun speak(text: String) {
        spoken += text
    }

    override fun vibrate(haptic: Haptic) {
        haptics += haptic
    }

    override fun release() = Unit
}

const val DAY_MILLIS = 24L * 60 * 60 * 1000

/** Days are numbered from the epoch in UTC, so tests don't depend on a time zone. Day 0 is a Monday. */
object FixedTimestampFormatter : TimestampFormatter {
    override fun day(epochMillis: Long, nowMillis: Long): String = "day ${(nowMillis - epochMillis) / DAY_MILLIS}"

    override fun time(epochMillis: Long): String = "at $epochMillis"

    override fun dayNumber(epochMillis: Long): Long = epochMillis / DAY_MILLIS

    override fun weekday(epochMillis: Long): Int = (dayNumber(epochMillis) % 7).toInt() + 1
}

/**
 * Stands in for `rememberPlatformModule()`. Tests must never load the real one: on the JVM it writes
 * to the user's preferences and plays sound.
 */
fun fakePlatformModule(
    store: KeyValueStore = FakeKeyValueStore(),
    player: CuePlayer = RecordingCuePlayer(),
    formatter: TimestampFormatter = FixedTimestampFormatter,
): Module = module {
    single { store }
    single { player }
    single { formatter }
}

/** The app's real container over fake platform services, for `AppContent`. */
fun testKoin(platformModule: Module = fakePlatformModule()): KoinApplication =
    koinApplication { modules(appModules(platformModule)) }
