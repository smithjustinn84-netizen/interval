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
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.interval.ui.FittedShareCard
import dev.justinsmith.interval.ui.HeartRateChart
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LocalWeightUnit
import dev.justinsmith.interval.ui.PreviewFormFactors
import dev.justinsmith.interval.ui.ShareCard
import dev.justinsmith.interval.ui.ShareCardSheet
import dev.justinsmith.interval.ui.ShareStat
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.BuiltInExercise
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.SetLog
import dev.justinsmith.lift.domain.formatWeightNumber
import dev.justinsmith.lift.domain.newRecords
import dev.justinsmith.lift.domain.tonnage
import dev.justinsmith.lift.domain.working
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.record_tag
import dev.justinsmith.lift.session.resources.session_time
import dev.justinsmith.lift.session.resources.share_card_more_lifts
import dev.justinsmith.lift.session.resources.share_card_more_records
import dev.justinsmith.lift.session.resources.share_card_moved
import dev.justinsmith.lift.session.resources.share_card_sets
import dev.justinsmith.lift.session.resources.summary_reps
import dev.justinsmith.lift.ui.exerciseName
import dev.justinsmith.lift.ui.recordLine
import dev.justinsmith.lift.ui.unitLabel
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * A finished session as a picture: [eyebrow] (Done on its summary, when it was in the Log) over its title, then
 * the records it set, each tagged, or with none the lifts it held and their sets, then the heart rate a sensor
 * read through it, then its time, the weight moved and its reps, as the summary counts them (warm-ups left out).
 */
@Composable
internal fun SessionShareCard(eyebrow: String, session: LiftSession, records: List<PersonalRecord>) {
    val unit = LocalWeightUnit.current
    val stats = listOf(
        ShareStat(formatClock(session.elapsedSeconds(0)), stringResource(Res.string.session_time)),
        // The unit in the label: "1440 kg" is wider than a third of the card in the stats' size.
        ShareStat(
            formatWeightNumber(session.tonnage(unit)),
            stringResource(Res.string.share_card_moved, unitLabel(unit)),
        ),
        ShareStat(session.reps.toString(), stringResource(Res.string.summary_reps)),
    )
    ShareCard(eyebrow, session.title, stats) {
        val samples = session.heartRateSamples?.takeIf { it.isNotEmpty() }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.space12)) {
            if (records.isNotEmpty()) RecordRows(records) else LiftRows(session.sets)
            samples?.let { HeartRateChart(it, Modifier.fillMaxWidth()) }
        }
    }
}

/** Share's sheet for a session: its [SessionShareCard] under [eyebrow], to send as a picture or as [text]. */
@Composable
internal fun SessionShareSheet(
    eyebrow: String,
    session: LiftSession,
    records: List<PersonalRecord>,
    text: String,
    onShareText: (String) -> Unit,
    onSharePicture: suspend (ByteArray) -> Unit,
    onDismiss: () -> Unit,
) {
    ShareCardSheet(text, onShareText, onSharePicture, onDismiss) { SessionShareCard(eyebrow, session, records) }
}

/** The records, the first few each on a row under its tag, then how many more there were. */
@Composable
private fun RecordRows(records: List<PersonalRecord>) {
    val palette = IntervalTheme.palette
    records.take(ROWS_SHOWN).forEach { record ->
        Column {
            Text(
                stringResource(Res.string.record_tag).uppercase(),
                style = IntervalTheme.type.caption,
                color = palette.accent,
            )
            ShareRow(exerciseName(record.exercise), recordLine(record))
        }
    }
    val more = records.size - ROWS_SHOWN
    if (more > 0) MoreLine(pluralStringResource(Res.plurals.share_card_more_records, more, more))
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
private fun ShareRow(name: String, detail: String) {
    val palette = IntervalTheme.palette
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.space12),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            name,
            Modifier.weight(1f),
            style = IntervalTheme.type.label,
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(detail, style = IntervalTheme.type.meta, color = palette.textDim, maxLines = 1)
    }
}

@Composable
private fun MoreLine(text: String) {
    Text(text, style = IntervalTheme.type.meta, color = IntervalTheme.palette.textDim)
}

/** The name a session shared as a picture goes by, from its summary or its page in the Log. */
internal const val SHARED_PICTURE = "session.png"

/** The rows a card has room for beside its heart rate. */
private const val ROWS_SHOWN = 3

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
    SessionShareCard("Done", previewSession, emptyList())
}

@PreviewFormFactors
@Composable
private fun SessionShareCardRecordsPreview() = FittedShareCard {
    SessionShareCard(
        "Sunday, Nov 22 · 7:05 AM",
        previewSession.copy(heartRateSamples = previewHeart),
        newRecords(emptyList(), previewSession),
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
