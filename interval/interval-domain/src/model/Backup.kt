package dev.justinsmith.interval.model

/** Days between asks to back up: about a month. */
const val BACKUP_DAYS = 30

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Whether Today asks to back up at [now]: something was trained since the log was last exported to keep
 * ([lastBackupAt], null for never), and [BACKUP_DAYS] have passed since that export, or with none since the first
 * thing trained, and since the user last said Not now ([askedAt]). [trained] are the moments things were trained
 * (workouts and sessions finished, sport days logged), any order. A phone with nothing on it is never asked.
 */
fun backupDue(lastBackupAt: Long?, askedAt: Long?, trained: List<Long>, now: Long): Boolean {
    val first = trained.minOrNull() ?: return false
    val newSince = lastBackupAt == null || trained.any { it > lastBackupAt }
    val since = maxOf(lastBackupAt ?: first, askedAt ?: Long.MIN_VALUE)
    return newSince && now - since >= BACKUP_DAYS * MILLIS_PER_DAY
}
