package dev.justinsmith.lift.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import dev.justinsmith.core.resources.heart_rate_unit
import dev.justinsmith.interval.platform.DEFAULT_MAX_BPM
import dev.justinsmith.interval.platform.HeartRate
import dev.justinsmith.interval.timer.WeekStanding
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.FittedShareCard
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalWeightUnit
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.ShareCard
import dev.justinsmith.interval.ui.ShareCardConfig
import dev.justinsmith.interval.ui.ShareCardSheet
import dev.justinsmith.interval.ui.ShareHeartRate
import dev.justinsmith.interval.ui.ShareStat
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.formatWeightNumber
import dev.justinsmith.lift.domain.newRecords
import dev.justinsmith.lift.domain.primaryExercise
import dev.justinsmith.lift.domain.timeUnderTensionSeconds
import dev.justinsmith.lift.domain.tonnage
import dev.justinsmith.lift.domain.working
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.record_tag
import dev.justinsmith.lift.session.resources.session_time
import dev.justinsmith.lift.session.resources.share_card_more_lifts
import dev.justinsmith.lift.session.resources.share_card_more_records
import dev.justinsmith.lift.session.resources.share_card_peak_bpm
import dev.justinsmith.lift.session.resources.share_card_sets
import dev.justinsmith.lift.session.resources.share_card_tut
import dev.justinsmith.lift.session.resources.share_card_volume
import dev.justinsmith.lift.session.resources.summary_reps
import dev.justinsmith.lift.ui.demo.rep
import dev.justinsmith.lift.ui.exerciseName
import dev.justinsmith.lift.ui.recordLine
import dev.justinsmith.lift.ui.unitLabel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import dev.justinsmith.core.resources.Res as CoreRes

/**
 * A finished session as a printed telemetry card: [eyebrow] over its title, its [week] standing dial badge, a
 * subtle `DemoPainter` watermark silhouette of its primary movement, the records it set (or lifts performed),
 * its `HeartRateChart` with zone-banding shader, and tabular Barlow Condensed stats (total volume, peak BPM,
 * time under tension, and duration).
 */
@Composable
internal fun SessionShareCard(
    eyebrow: String,
    session: LiftSession,
    records: List<PersonalRecord>,
    week: WeekStanding? = null,
    maxHeartRate: Int = DEFAULT_MAX_BPM,
    config: ShareCardConfig = ShareCardConfig(),
) {
    val unit = LocalWeightUnit.current
    val heartRate = session.heartRateSamples?.let { ShareHeartRate(it, maxHeartRate) }?.takeIf { it.hasChart }
    val peakBpm = heartRate?.peakBpm ?: session.peakBpm?.takeIf { it in HeartRate.BPM_RANGE }
    val tonnage = session.tonnage(unit)
    val volumeStat = if (tonnage > 0.0) {
        ShareStat(
            value = formatTabularWeight(tonnage),
            label = stringResource(Res.string.share_card_volume),
            unit = unitLabel(unit),
        )
    } else {
        ShareStat(session.reps.toString(), stringResource(Res.string.summary_reps))
    }
    val tutSeconds = session.timeUnderTensionSeconds
    val stats = buildList {
        add(volumeStat)
        peakBpm?.let {
            add(
                ShareStat(
                    value = it.toString(),
                    label = stringResource(Res.string.share_card_peak_bpm),
                    unit = stringResource(CoreRes.string.heart_rate_unit),
                    accent = true,
                ),
            )
        }
        if (tutSeconds > 0) {
            add(ShareStat(formatClock(tutSeconds), stringResource(Res.string.share_card_tut)))
        }
        add(ShareStat(formatClock(session.elapsedSeconds(0)), stringResource(Res.string.session_time)))
    }
    val showHeart = heartRate != null && config.showHeartRate
    ShareCard(
        eyebrow = eyebrow,
        title = session.title,
        stats = stats,
        week = week,
        heartRate = heartRate,
        movement = session.primaryExercise?.rep,
        config = config,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(if (showHeart) Spacing.space4 else Spacing.space8),
        ) {
            if (records.isNotEmpty()) {
                val maxRows = if (showHeart) RECORD_ROWS_WITH_HEART else ROWS_SHOWN
                RecordRows(records, maxRows = maxRows, compact = showHeart)
            } else {
                LiftRows(session.sets)
            }
        }
    }
}

/** Share's interactive sheet for a session: its [SessionShareCard] under [eyebrow], to send as PNG or [text]. */
@Composable
internal fun SessionShareSheet(
    eyebrow: String,
    session: LiftSession,
    records: List<PersonalRecord>,
    text: String,
    onShareText: (String) -> Unit,
    onSharePicture: suspend (ByteArray) -> Unit,
    onDismiss: () -> Unit,
    week: WeekStanding? = null,
    maxHeartRate: Int = DEFAULT_MAX_BPM,
) {
    val hasHeart = session.heartRateSamples?.count { it in HeartRate.BPM_RANGE }?.let { it >= 2 } == true
    val hasMovement = session.primaryExercise?.rep != null
    ShareCardSheet(
        text = text,
        onShareText = onShareText,
        onSharePicture = onSharePicture,
        onDismiss = onDismiss,
        hasHeartRate = hasHeart,
        hasWeekBadge = week != null,
        hasMovement = hasMovement,
    ) { config ->
        SessionShareCard(eyebrow, session, records, week, maxHeartRate, config)
    }
}

/** The records, the first [maxRows] each on a row under its tag, then how many more there were. */
@Composable
private fun RecordRows(records: List<PersonalRecord>, maxRows: Int = ROWS_SHOWN, compact: Boolean = false) {
    val palette = IntervalTheme.palette
    val tagStyle = if (compact) {
        IntervalTheme.type.caption.copy(fontSize = COMPACT_TAG_SP.sp, lineHeight = COMPACT_TAG_LINE_SP.sp)
    } else {
        IntervalTheme.type.caption
    }
    records.take(maxRows).forEach { record ->
        Column {
            Text(
                stringResource(Res.string.record_tag).uppercase(),
                style = tagStyle,
                color = palette.accent,
            )
            ShareRow(exerciseName(record.exercise), recordLine(record), compact = compact)
        }
    }
    val more = records.size - maxRows
    if (more > 0) MoreLine(pluralStringResource(Res.plurals.share_card_more_records, more, more), compact = compact)
}

/** Each lift the session held, in the order it came, with its working sets, then how many more there were. */
@Composable
private fun LiftRows(sets: List<SetLog>) {
    val lifts = sets.working().groupBy { it.exercise }.toList()
    lifts.take(ROWS_SHOWN).forEach { (exercise, done) ->
        ShareRow(exerciseName(exercise), pluralStringResource(Res.plurals.share_card_sets, done.size, done.size))
    }
    val more = lifts.size - ROWS_SHOWN
    if (more > 0) MoreLine(pluralStringResource(Res.plurals.share_card_more_lifts, more, more))
}

@Composable
private fun ShareRow(name: String, detail: String, compact: Boolean = false) {
    val palette = IntervalTheme.palette
    val nameStyle = if (compact) {
        IntervalTheme.type.label.copy(fontSize = COMPACT_ROW_SP.sp, lineHeight = COMPACT_ROW_LINE_SP.sp)
    } else {
        IntervalTheme.type.label
    }
    val detailStyle = if (compact) {
        IntervalTheme.type.meta.copy(fontSize = COMPACT_DETAIL_SP.sp, lineHeight = COMPACT_ROW_LINE_SP.sp)
    } else {
        IntervalTheme.type.meta
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            name,
            Modifier.weight(1f),
            style = nameStyle,
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(detail, style = detailStyle, color = palette.textDim, maxLines = 1)
    }
}

@Composable
private fun MoreLine(text: String, compact: Boolean = false) {
    val style = if (compact) {
        IntervalTheme.type.meta.copy(fontSize = COMPACT_DETAIL_SP.sp, lineHeight = COMPACT_ROW_LINE_SP.sp)
    } else {
        IntervalTheme.type.meta
    }
    Text(text, style = style, color = IntervalTheme.palette.textDim)
}

/** Formats total session volume with thousands grouping ("1,440") for crisp tabular display on the card. */
internal fun formatTabularWeight(value: Double): String {
    val raw = formatWeightNumber(value)
    val dot = raw.indexOf('.')
    val whole = if (dot < 0) raw else raw.substring(0, dot)
    if (whole.length <= THOUSANDS_GROUP) return raw
    val grouped = buildString {
        whole.forEachIndexed { index, ch ->
            if (index > 0 && (whole.length - index) % THOUSANDS_GROUP == 0) append(',')
            append(ch)
        }
        if (dot >= 0) append(raw.substring(dot))
    }
    return grouped
}

/** The name a session shared as a picture goes by, from its summary or its page in the Log. */
internal const val SHARED_PICTURE = "session.png"

/** The rows a card has room for when no heart-rate chart sits below them, and when one does. */
private const val ROWS_SHOWN = 3
private const val RECORD_ROWS_WITH_HEART = 2
private const val THOUSANDS_GROUP = 3
private const val COMPACT_TAG_SP = 10f
private const val COMPACT_TAG_LINE_SP = 12f
private const val COMPACT_ROW_SP = 14f
private const val COMPACT_ROW_LINE_SP = 17f
private const val COMPACT_DETAIL_SP = 12.5f

/** Swings, goblet squats and presses, a set every two minutes, for the previews. */
private val previewSets = List(PREVIEW_SETS) { index ->
    val exercise = when (index / PREVIEW_SETS_A_LIFT) {
        0 -> BuiltInExercise.TwoHandSwing
        1 -> BuiltInExercise.GobletSquat
        else -> BuiltInExercise.Press
    }
    SetLog.of(index + 1L, exercise, kg = PREVIEW_KG, reps = PREVIEW_REPS, side = null, index * PREVIEW_GAP_MILLIS)
}

private val previewSession = LiftSession(
    "s",
    startedAtEpochMillis = 0,
    finishedAtEpochMillis = PREVIEW_LENGTH_MILLIS,
    title = "Swings and squats",
    sets = previewSets,
)

@PreviewFormFactors
@Composable
private fun SessionShareCardPreview() = FittedShareCard {
    SessionShareCard(
        "Done",
        previewSession,
        emptyList(),
        week = WeekStanding(count = 3, goal = 4, streak = 2),
    )
}

@PreviewFormFactors
@Composable
private fun SessionShareCardRecordsPreview() = FittedShareCard {
    SessionShareCard(
        "Sunday, Nov 22 · 7:05 AM",
        previewSession.copy(heartRateSamples = previewHeart),
        newRecords(emptyList(), previewSession),
        week = WeekStanding(count = 4, goal = 4, streak = 3),
    )
}

/** A heart rate up through each set and down through each rest. */
private val previewHeart = listOf(92, 118, 141, 152, 128, 112, 139, 156, 131, 114, 143, 160, 134, 118, 128, 109)

private const val PREVIEW_SETS = 9
private const val PREVIEW_SETS_A_LIFT = 3
private const val PREVIEW_KG = 16.0
private const val PREVIEW_REPS = 10
private const val PREVIEW_GAP_MILLIS = 120_000L
private const val PREVIEW_LENGTH_MILLIS = 1_500_000L
