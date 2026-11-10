package dev.justinsmith.lift.tests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.justinsmith.interval.ui.AccentButton
import dev.justinsmith.interval.ui.BackAction
import dev.justinsmith.interval.ui.Bar
import dev.justinsmith.interval.ui.Bars
import dev.justinsmith.interval.ui.GroupLabel
import dev.justinsmith.interval.ui.IntervalSheet
import dev.justinsmith.interval.ui.IntervalTheme
import dev.justinsmith.interval.ui.LinkRow
import dev.justinsmith.interval.ui.ListCard
import dev.justinsmith.interval.ui.RowDivider
import dev.justinsmith.interval.ui.ScrollPage
import dev.justinsmith.interval.ui.Spacing
import dev.justinsmith.lift.domain.Attempt
import dev.justinsmith.lift.domain.Retest
import dev.justinsmith.lift.domain.TestId
import dev.justinsmith.lift.domain.formatKg
import dev.justinsmith.lift.today.resources.Res
import dev.justinsmith.lift.today.resources.common_joined
import dev.justinsmith.lift.today.resources.kg_pair
import dev.justinsmith.lift.today.resources.kg_value
import dev.justinsmith.lift.today.resources.tests_attempts
import dev.justinsmith.lift.today.resources.tests_attempts_at
import dev.justinsmith.lift.today.resources.tests_bars_cd
import dev.justinsmith.lift.today.resources.tests_bell
import dev.justinsmith.lift.today.resources.tests_best_last
import dev.justinsmith.lift.today.resources.tests_days_ago
import dev.justinsmith.lift.today.resources.tests_due
import dev.justinsmith.lift.today.resources.tests_in_progress
import dev.justinsmith.lift.today.resources.tests_intro
import dev.justinsmith.lift.today.resources.tests_never
import dev.justinsmith.lift.today.resources.tests_none
import dev.justinsmith.lift.today.resources.tests_none_at
import dev.justinsmith.lift.today.resources.tests_pass
import dev.justinsmith.lift.today.resources.tests_resting
import dev.justinsmith.lift.today.resources.tests_start
import dev.justinsmith.lift.today.resources.tests_taxed
import dev.justinsmith.lift.today.resources.tests_title
import dev.justinsmith.lift.today.resources.tests_today
import dev.justinsmith.lift.today.resources.tests_too_soon
import dev.justinsmith.lift.today.resources.tests_warm_up
import dev.justinsmith.lift.today.resources.tests_weeks_ago
import dev.justinsmith.lift.today.resources.tests_yesterday
import dev.justinsmith.lift.ui.ChoiceRow
import dev.justinsmith.lift.ui.testCount
import dev.justinsmith.lift.ui.testName
import dev.justinsmith.lift.ui.testProtocol
import dev.justinsmith.lift.ui.testResultLine
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private const val DAYS_PER_WEEK = 7

/** From this many days on, "2 weeks ago" says it better than "14 days ago". */
private const val WEEKS_FROM_DAYS = 14

/** The attempts a test's sheet charts: the latest, which is what a trend is read from. */
private const val CHARTED_ATTEMPTS = 8

/** The tests, with the sheet of [open] up when one is named. [onStart] opens the session a test started. */
@Composable
fun TestsRoute(open: TestId?, onStart: () -> Unit, onBack: () -> Unit) {
    val viewModel = koinViewModel<TestsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    TestsScreen(
        state,
        onStart = { id, kg ->
            viewModel.start(id, kg)
            onStart()
        },
        onBack = onBack,
        open = open,
    )
}

/**
 * Each test with its last result and when, opened from Train's Strength pillar. A row opens the test's sheet:
 * how it is done, the bell, the attempts at that bell and whether today is a day for another, then Start.
 */
@Composable
fun TestsScreen(
    state: TestsUiState,
    onStart: (TestId, kg: Double?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    open: TestId? = null,
) {
    val palette = IntervalTheme.palette
    // The test whose sheet is up, by its id's name.
    var opened by rememberSaveable { mutableStateOf(open?.name) }
    ScrollPage(stringResource(Res.string.tests_title), modifier, navigation = { BackAction(onBack) }) {
        Text(
            stringResource(Res.string.tests_intro),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
            modifier = Modifier.padding(bottom = Spacing.space12),
        )
        ListCard {
            state.tests.forEachIndexed { index, row ->
                if (index > 0) RowDivider()
                LinkRow(testName(row.test.id), lastLine(row), { opened = row.test.id.name })
            }
        }
    }
    state.tests.firstOrNull { it.test.id.name == opened }?.let { row ->
        TestSheet(
            row,
            state.taxed,
            state.inProgress,
            onStart = { onStart(row.test.id, it) },
            onDismiss = { opened = null },
        )
    }
}

/** "97 reps with 24 kg · 3 weeks ago", or that it hasn't been done. */
@Composable
private fun lastLine(row: TestRow): String {
    val last = row.last ?: return stringResource(Res.string.tests_never)
    return stringResource(
        Res.string.common_joined,
        testResultLine(row.test, last.result, last.load),
        daysAgo(row.daysSince ?: 0),
    )
}

/** "today", "yesterday", "5 days ago", "3 weeks ago". */
@Composable
private fun daysAgo(days: Int): String = when {
    days <= 0 -> stringResource(Res.string.tests_today)
    days == 1 -> stringResource(Res.string.tests_yesterday)
    days < WEEKS_FROM_DAYS -> pluralStringResource(Res.plurals.tests_days_ago, days, days)
    else -> (days / DAYS_PER_WEEK).let { pluralStringResource(Res.plurals.tests_weeks_ago, it, it) }
}

/**
 * A test before it is started: how it is done, the bell (opened on the last attempt's), the attempts at that
 * bell, and what a coach would say of today: too soon after the last, a hard day carried, warm up. None of it
 * stops the test; a session in progress does.
 */
@Composable
private fun TestSheet(
    row: TestRow,
    taxed: Boolean,
    inProgress: Boolean,
    onStart: (kg: Double?) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = IntervalTheme.palette
    val test = row.test
    IntervalSheet(testName(test.id), onDismiss = onDismiss) {
        var kg by rememberSaveable(test.id) { mutableStateOf(row.bellKg) }
        Column(
            Modifier.fillMaxWidth().padding(vertical = Spacing.space8),
            verticalArrangement = Arrangement.spacedBy(Spacing.space8),
        ) {
            // The sheet's own title is only read aloud on a phone: the test is named over how it is done.
            Text(testName(test.id), style = IntervalTheme.type.section, color = palette.text)
            Text(testProtocol(test.id), style = IntervalTheme.type.body, color = palette.text)
            if (row.bells.isNotEmpty()) {
                GroupLabel(stringResource(Res.string.tests_bell))
                ChoiceRow(row.bells, kg, { bellLabel(it, test.block.double) }, { kg = it })
            }
            Attempts(row, kg)
            Advice(row, taxed)
            if (inProgress) {
                Text(
                    stringResource(Res.string.tests_in_progress),
                    style = IntervalTheme.type.meta,
                    color = palette.textDim,
                )
            }
            AccentButton(
                stringResource(Res.string.tests_start),
                { onStart(kg) },
                Modifier.fillMaxWidth(),
                enabled = !inProgress,
            )
        }
    }
}

/** "24 kg", or for a test done with two "2 × 24 kg". */
@Composable
private fun bellLabel(kg: Double, double: Boolean): String =
    stringResource(if (double) Res.string.kg_pair else Res.string.kg_value, formatKg(kg))

/** The attempts at the bell picked: bars of the latest against the target, then the best and the last in words. */
@Composable
private fun ColumnScope.Attempts(row: TestRow, kg: Double?) {
    val palette = IntervalTheme.palette
    val test = row.test
    val at: List<Attempt> = row.attemptsAt(kg).takeLast(CHARTED_ATTEMPTS)
    GroupLabel(
        if (kg == null) {
            stringResource(Res.string.tests_attempts)
        } else {
            stringResource(Res.string.tests_attempts_at, bellLabel(kg, test.block.double))
        },
    )
    if (at.isEmpty()) {
        Text(
            stringResource(if (kg == null) Res.string.tests_none else Res.string.tests_none_at),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
    } else {
        val best = at.maxOf { it.result }
        val bestWords = testCount(test, best)
        Bars(
            at.map { Bar(it.result.toFloat()) },
            max = maxOf(best, test.target ?: 0).toFloat(),
            description = pluralStringResource(Res.plurals.tests_bars_cd, at.size, at.size, bestWords),
            line = test.target?.toFloat(),
        )
        Text(
            stringResource(Res.string.tests_best_last, bestWords, testCount(test, at.last().result)),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
    }
    test.target?.let {
        Text(
            stringResource(Res.string.tests_pass, testCount(test, it)),
            style = IntervalTheme.type.meta,
            color = palette.textDim,
        )
    }
}

/** What to weigh before starting: how long since the last attempt, a hard day carried, and the warm-up. */
@Composable
private fun ColumnScope.Advice(row: TestRow, taxed: Boolean) {
    val palette = IntervalTheme.palette
    val since = row.daysSince?.let { daysAgo(it) }
    val retest = when (row.retest) {
        Retest.Never -> null
        Retest.TooSoon -> Res.string.tests_too_soon
        Retest.Resting -> Res.string.tests_resting
        Retest.Due -> Res.string.tests_due
    }
    if (retest != null && since != null) {
        Text(stringResource(retest, since), style = IntervalTheme.type.meta, color = palette.textDim)
    }
    if (taxed) Text(stringResource(Res.string.tests_taxed), style = IntervalTheme.type.meta, color = palette.text)
    Text(stringResource(Res.string.tests_warm_up), style = IntervalTheme.type.meta, color = palette.textDim)
}
