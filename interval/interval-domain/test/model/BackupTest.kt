package dev.justinsmith.interval.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupTest {
    private val day = 86_400_000L

    @Test
    fun `a phone with nothing trained is never asked`() {
        assertFalse(backupDue(lastBackupAt = null, askedAt = null, trained = emptyList(), now = 400 * day))
    }

    @Test
    fun `never backed up it asks a month after the first thing trained`() {
        val trained = listOf(10 * day, 20 * day)
        assertFalse(backupDue(null, null, trained, now = 39 * day))
        assertTrue(backupDue(null, null, trained, now = 40 * day))
    }

    @Test
    fun `backed up it asks a month later once something new was trained`() {
        assertTrue(backupDue(lastBackupAt = 10 * day, askedAt = null, trained = listOf(12 * day), now = 40 * day))
        assertFalse(backupDue(lastBackupAt = 10 * day, askedAt = null, trained = listOf(12 * day), now = 39 * day))
    }

    @Test
    fun `with nothing new since the last backup it never asks`() {
        assertFalse(backupDue(lastBackupAt = 10 * day, askedAt = null, trained = listOf(5 * day), now = 100 * day))
    }

    @Test
    fun `not now holds it off another month`() {
        val trained = listOf(1 * day, 50 * day)
        assertFalse(backupDue(lastBackupAt = null, askedAt = 45 * day, trained = trained, now = 74 * day))
        assertTrue(backupDue(lastBackupAt = null, askedAt = 45 * day, trained = trained, now = 75 * day))
    }
}
