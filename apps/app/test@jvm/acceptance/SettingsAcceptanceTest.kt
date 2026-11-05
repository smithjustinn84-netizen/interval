package dev.justinsmith.interval.acceptance

import dev.justinsmith.interval.fakes.FakeStorage
import kotlin.test.Test

/** `journeys/settings.xml`: change a cue and the theme, see both kept, then put the theme back. */
class SettingsAcceptanceTest {
    @Test
    fun `settings opens from every tab and back returns to that tab`() = acceptanceScenario {
        openSettings()
        goBack()
        seeTimer()

        openTab("Lift")
        seeLiftTab()
        openSettings()
        goBack()
        seeLiftTab()

        openTab("History")
        seeHistoryTab()
        openSettings()
        goBack()
        seeHistoryTab()
    }

    @Test
    fun `a cue and the theme changed in settings are kept after a restart`() {
        val storage = FakeStorage()
        acceptanceScenario(storage = storage) {
            openSettings()
            seeVoice(on = true)
            seeTheme("Dark")
            toggleVoice()
            seeVoice(on = false)
            pickTheme("Light")
            seeTheme("Light")
            // The theme is down the page, its title bar and Back scrolled away above it.
            pressSystemBack()
            seeTimer()
        }
        acceptanceScenario(storage = storage) {
            openSettings()
            seeVoice(on = false)
            seeTheme("Light")
            toggleVoice()
            pickTheme("Dark")
            pressSystemBack()
            seeTimer()
        }
        acceptanceScenario(storage = storage) {
            openSettings()
            seeVoice(on = true)
            seeTheme("Dark")
        }
    }
}
