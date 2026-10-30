package dev.justinsmith.interval.acceptance

import dev.justinsmith.interval.fakes.FakeStorage
import kotlin.test.Test

/** `journeys/settings.xml`: change a cue and the theme, see both kept, then put the theme back. */
class SettingsAcceptanceTest {
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
            goBack()
            seeTimer()
        }
        acceptanceScenario(storage = storage) {
            openSettings()
            seeVoice(on = false)
            seeTheme("Light")
            toggleVoice()
            pickTheme("Dark")
            goBack()
            seeTimer()
        }
        acceptanceScenario(storage = storage) {
            openSettings()
            seeVoice(on = true)
            seeTheme("Dark")
        }
    }
}
