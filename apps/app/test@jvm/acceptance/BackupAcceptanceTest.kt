package dev.justinsmith.interval.acceptance

import dev.justinsmith.interval.fakes.DAY_MILLIS
import dev.justinsmith.lift.data.SessionEntity
import dev.justinsmith.lift.data.SetEntity
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.fakes.FakeLiftStorage
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * `journeys/backup.xml`: a month after the first thing trained with no backup, Today asks to back up the log; Export
 * offers the export file and the ask goes, and Settings says when it was last backed up. Settings' Export a
 * spreadsheet offers the log as a CSV, a row a session and a set, which is no backup.
 */
class BackupAcceptanceTest {
    @Test
    fun `a month on Today asks to back up and an export answers it`() = acceptanceScenario(lift = aSessionAMonthAgo()) {
        see("Back up your log")
        tap("Export")
        sharedFile("interval-1970-01-01.json")
        seeNo("Back up your log")

        openSettings()
        scrollToPart("Last backed up")
    }

    @Test
    fun `not now puts the ask away`() = acceptanceScenario(lift = aSessionAMonthAgo()) {
        see("Back up your log")
        tap("Not now")
        seeNo("Back up your log")
    }

    @Test
    fun `settings exports the log as a spreadsheet`() = acceptanceScenario(lift = aSessionAMonthAgo()) {
        openSettings()
        scrollTo("Export a spreadsheet")
        tap("Export a spreadsheet")
        val sheet = sharedFile("interval-log-1970-01-01.csv")
        val lines = sheet.lines()
        assertTrue(lines.first().contains("Date,Time,Kind,Name,Exercise"))
        assertTrue(lines.any { it.contains("Lift session,Swings") })
        assertTrue(lines.any { it.contains("Set,Swings,Two-hand swing,,24,kg,15") })
        // A spreadsheet is no backup.
        scrollToPart("Not backed up yet")
    }
}

/** A session of two-hand swings finished 40 days before the clock's start. */
private fun aSessionAMonthAgo() = FakeLiftStorage().apply {
    val finished = -40 * DAY_MILLIS
    val started = finished - 10 * 60_000L
    sessions.sessions.value += SessionEntity("old", started, finished, "Swings", finished)
    sessions.sets.value += SetEntity(0, "old", BuiltInExercise.TwoHandSwing.key, 24.0, 15, null, started + 60_000L)
}
