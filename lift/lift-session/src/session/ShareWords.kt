package dev.justinsmith.lift.session

import androidx.compose.runtime.Composable
import dev.justinsmith.interval.timer.formatClock
import dev.justinsmith.lift.domain.LiftSession
import dev.justinsmith.lift.domain.PersonalRecord
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.session.resources.Res
import dev.justinsmith.lift.session.resources.common_joined
import dev.justinsmith.lift.session.resources.kg_value
import dev.justinsmith.lift.session.resources.share_session_figures
import dev.justinsmith.lift.session.resources.share_session_record
import dev.justinsmith.lift.ui.exerciseName
import dev.justinsmith.lift.ui.recordLine
import org.jetbrains.compose.resources.stringResource

/**
 * A finished session in words to send: its title (and [moment], when it was, if given), then its time, the
 * weight moved and its reps, then each record it set on a line of its own:
 * "Two-hand swing" / "1:30 · 160 kg moved · 10 reps" / "Two-hand swing: Heaviest 16 kg × 10".
 */
@Composable
internal fun sessionShareText(session: LiftSession, records: List<PersonalRecord>, moment: String? = null): String {
    val title = moment?.let { stringResource(Res.string.common_joined, session.title, it) } ?: session.title
    val figures = stringResource(
        Res.string.share_session_figures,
        formatClock(session.elapsedSeconds(0)),
        stringResource(Res.string.kg_value, formatKg(session.tonnageKg)),
        session.reps,
    )
    val lines = records.map { record ->
        stringResource(Res.string.share_session_record, exerciseName(record.exercise), recordLine(record))
    }
    return (listOf(title, figures) + lines).joinToString("\n")
}
