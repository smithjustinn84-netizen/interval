@file:OptIn(ExperimentalTestApi::class)

package dev.justinsmith.interval.ui

import androidx.compose.ui.semantics.SemanticsProperties.ContentDescription
import androidx.compose.ui.semantics.SemanticsProperties.EditableText
import androidx.compose.ui.semantics.SemanticsProperties.Text
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.DesktopComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode

/**
 * What an acceptance driver builds its operations from: seeing and tapping by what a person reads, each
 * step waiting for the state to arrive (the real view models emit on Swing's thread, which the test's idling
 * doesn't track). A step that times out fails with what was expected and every text and description on
 * screen instead, so a failing spec says what it found.
 */
open class AcceptanceSteps(protected val ui: DesktopComposeUiTest) {
    fun see(text: String) = see(hasText(text))

    fun see(matcher: SemanticsMatcher) = waitFor("to see ${matcher.description}") {
        ui.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()
    }

    fun seeNo(text: String) = seeNo(hasText(text))

    fun seeNo(matcher: SemanticsMatcher) = waitFor("not to see ${matcher.description}") {
        ui.onAllNodes(matcher).fetchSemanticsNodes().isEmpty()
    }

    fun tap(text: String) = tap(hasText(text) and hasClickAction())

    fun tapDescribed(description: String) = tap(hasContentDescription(description) and hasClickAction())

    /**
     * Taps what [matcher] finds once it shows: the last drawn, where a sheet or dialog leaves its owner's
     * control of the same name underneath, scrolled into view first.
     */
    fun tap(matcher: SemanticsMatcher) {
        see(matcher)
        val node = ui.onAllNodes(matcher).onLast()
        if (hasAnyAncestor(hasScrollAction()).matches(node.fetchSemanticsNode())) node.performScrollTo()
        node.performClick()
    }

    /** Scrolls the page's list or grid, which composes only what is in view, until [text] is among what it shows. */
    fun scrollTo(text: String) = waitFor("to scroll to \"$text\"") {
        runCatching { ui.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text)) }.isSuccess
    }

    /** Waits for [condition]; on a timeout, fails saying what was [expected] and what the screen shows instead. */
    protected fun waitFor(expected: String, condition: () -> Boolean) {
        try {
            ui.waitUntil(timeoutMillis = TIMEOUT_MILLIS, condition = condition)
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Expected $expected. On screen: ${onScreen()}", timeout)
        }
    }

    /** Every text on screen, what is typed in a field in quotes, and every content description in brackets. */
    private fun onScreen(): List<String> =
        ui.onAllNodes(SemanticsMatcher.keyIsDefined(Text) or SemanticsMatcher.keyIsDefined(ContentDescription))
            .fetchSemanticsNodes()
            .flatMap { node ->
                node.config.getOrElse(Text) { emptyList() }.map { it.text } +
                    listOfNotNull(node.config.getOrNull(EditableText)?.text?.ifEmpty { null }?.let { "“$it”" }) +
                    node.config.getOrElse(ContentDescription) { emptyList() }.map { "[$it]" }
            }
            .distinct()

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
