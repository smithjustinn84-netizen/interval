package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/**
 * `journeys/train.xml`: Train holds what there is to train, by pillar: Strength, Lift's side, and Conditioning,
 * the presets. It reopens on the pillar last shown, and Back from either returns to Today.
 */
class TrainAcceptanceTest {
    @Test
    fun `train opens on strength and conditioning lists the presets`() = acceptanceScenario {
        openTab("Train")
        seeStrength()

        pickPillar("Conditioning")
        seeConditioning()

        pickPillar("Strength")
        seeStrength()
    }

    @Test
    fun `train reopens on the pillar last shown and back returns to today`() = acceptanceScenario {
        openTab("Train")
        pickPillar("Conditioning")
        seeConditioning()

        openTab("History")
        seeHistoryTab()
        openTab("Train")
        seeConditioning()

        pressSystemBack()
        seeTodayTab()
    }
}
