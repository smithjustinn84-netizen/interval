package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import kotlin.test.assertEquals

// Building a flow of your own, apart from the driver they extend: it is as large as a class may be.

/** From Train's Mobility pillar, its "On your own" row, past the shipped flows. */
fun IntervalAcceptanceDriver.buildAFlow() {
    scrollTo("Build a flow")
    tap("Build a flow")
    see("Add a move")
}

/** Names the flow in the editor. */
fun IntervalAcceptanceDriver.nameFlow(name: String) {
    typeInto("Name of the flow", name)
}

/** When the flow is for: "Before training" or "After, or on a day off". */
fun IntervalAcceptanceDriver.flowIsFor(use: String) = tap(hasText(use) and isSelectable())

/** Adds one of the library's mat moves or holds, as it comes, and closes its sheet. */
fun IntervalAcceptanceDriver.addLibraryMove(name: String) {
    tap("Add a move")
    tap(hasText(name) and hasClickAction())
    see("A side at a time")
    tap("Done")
}

/** Adds a move typed in, [name]d, with its [cue], made [steps] five seconds longer than it starts. */
fun IntervalAcceptanceDriver.addOwnMove(name: String, cue: String, steps: Int = 0) {
    tap("Add a move")
    tap("Type a move")
    typeInto("Name of the move", name)
    typeInto("How it is done", cue)
    repeat(steps) { tapDescribed("Five seconds more") }
    tap("Done")
}

/** Opens the move named in the editor, to change it. */
fun IntervalAcceptanceDriver.openMove(name: String) {
    tap(hasText(name) and hasClickAction())
    see("A side at a time")
}

fun IntervalAcceptanceDriver.fiveSecondsMore() = tapDescribed("Five seconds more")

fun IntervalAcceptanceDriver.closeMove() = tap("Done")

/** Saves the flow, which lands back on the Mobility pillar with it there. */
fun IntervalAcceptanceDriver.saveFlow(name: String) {
    tap("Save flow")
    see("Flow saved")
    seeMobility()
    see(name)
}

/** Save is refused while the flow has the moves and times of another, and says which. */
fun IntervalAcceptanceDriver.seeSaveRefused(sameAs: String) {
    see(hasText("Same moves and times as $sameAs", substring = true))
    tap("Save flow")
    see("Add a move")
}

/** On a shipped flow's sheet: a copy to change, in the editor. */
fun IntervalAcceptanceDriver.makeItYours() {
    tap("Make it yours")
    see("Add a move")
}

/** On a flow of the user's sheet: its editor. */
fun IntervalAcceptanceDriver.editFlow() {
    tap("Edit")
    see("Delete flow")
}

fun IntervalAcceptanceDriver.deleteFlow() {
    tap("Delete flow")
    see("Flow deleted")
    seeMobility()
}

/** The flows the user has built, by name, as stored. */
fun IntervalAcceptanceDriver.flowsStored(vararg names: String) = waitFor("flows stored: ${names.toList()}") {
    app.repository.flows.customFlows.value.map { it.name } == names.toList()
}

/** The moves of the flow stored under [name], each with its seconds, as kept. */
fun IntervalAcceptanceDriver.flowHolds(name: String, vararg moves: Pair<String, Int>) {
    val flow = app.repository.flows.customFlows.value.single { it.name == name }
    assertEquals(moves.toList(), flow.moves.map { it.name to it.seconds })
}
