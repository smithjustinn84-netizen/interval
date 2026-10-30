package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/**
 * `journeys/presets.xml`: save the Timer's settings as a preset, put a built-in on the Timer, start one from
 * its sheet, edit and delete the saved one, and open a new preset.
 */
class PresetsAcceptanceTest {
    @Test
    fun `a preset saved from the timer is started edited and deleted`() = acceptanceScenario {
        seeTimerTotalFor("Custom")
        openAllPresets()
        seeUnsavedTimerCard("30s on · 15s off · ×8")
        seeNothingSavedYet()

        startSavingTimerSettings()
        seeNameCount("0 / 24")
        namePreset("Tabata")
        seeSavesAs("Tabata 2")
        namePreset("Journey")
        seeNameCount("7 / 24")
        savePresetSheet()
        seePresetOnTimer("Journey")
        seeNoUnsavedTimerCard()
        savedPresets("Journey")

        openPreset("Tabata")
        putOnTimer()
        seeTimerTotalFor("Tabata")
        openAllPresets()
        seePresetOnTimer("Tabata")
        openPreset("Tabata")
        seeNoPutOnTimer()
        dismissSheet()

        openPreset("Journey")
        startWorkout()
        seePhase("Get ready")
        askToEnd()
        confirmEnd()
        seePresetOnTimer("Journey")
        nothingIsSaved()

        openPreset("Journey")
        editPresetRounds(12)
        seePresetReading("Journey", "30s on · 15s off · ×12")
        seePresetOnTimer("Journey")
        seeNoUnsavedTimerCard()

        openPreset("Journey")
        askToDeletePreset("Journey")
        cancelDialog()
        see("Edit")
        askToDeletePreset("Journey")
        confirmDelete()
        seeNothingSavedYet()
        seeUnsavedTimerCard("30s on · 15s off · ×12")
        savedPresets()

        openNewPreset(placeholder = "30/15 ×12")
        cancelSheet()
        goBack()
        seeTimerTotalFor("Custom")
        seeSaveAsPresetOnTimer()
    }
}
