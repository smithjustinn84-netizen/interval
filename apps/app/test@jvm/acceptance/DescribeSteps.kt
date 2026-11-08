package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog

// A workout described in words on the Timer, beside the driver, which is at its size limit.

/** Opens the Timer's Describe sheet and types [words] into it. */
fun IntervalAcceptanceDriver.describeOnTimer(words: String) {
    tapDescribed("Describe a workout")
    see(hasText("Describe a workout") and hasAnyAncestor(isDialog()))
    typeInto("Workout in words", words)
}

/** The sheet's preview of a block: "Block 2" beside how it reads. */
fun IntervalAcceptanceDriver.seeDescribedBlock(block: Int, reading: String) {
    see(hasText("Block $block") and hasAnyAncestor(isDialog()))
    see(hasText(reading) and hasAnyAncestor(isDialog()))
}

/** Use, which puts the described workout on the Timer: [blocks] of them, with [blockRest] seconds between. */
fun IntervalAcceptanceDriver.useDescription(blocks: Int, blockRest: Int) {
    tap("Use")
    waitFor("the Timer's workout of $blocks blocks, $blockRest s between") {
        val config = app.repository.settings.config.value
        config.blocks.size == blocks && config.blockRestSeconds == blockRest
    }
    see("Edit blocks")
}
