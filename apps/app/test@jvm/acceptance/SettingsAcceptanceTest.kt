package dev.justinsmith.interval.acceptance

import dev.justinsmith.interval.fakes.FakeStorage
import kotlin.test.Test

/** `journeys/settings.xml`: change a cue and the theme, see both kept, then put the theme back. */
class SettingsAcceptanceTest {
    @Test
    fun `settings opens from every tab and back returns to that tab`() = acceptanceScenario {
        openSettings()
        goBack()
        seeTodayTab()

        openTab("Timer")
        seeTimer()
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
    fun `settings shows the plan's goal and reminder and changes them on the plan`() = acceptanceScenario {
        openSettings()
        seeGoalInSettings(null)
        seeReminderInSettings(null)

        openPlanFromSettings()
        raiseWeeklyGoal()
        seeWeeklyGoal(3)
        remindAt("18:00")
        goBack()

        seeGoalInSettings(3)
        seeReminderInSettings("18:00")
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
            seeTodayTab()
        }
        acceptanceScenario(storage = storage) {
            openSettings()
            seeVoice(on = false)
            seeTheme("Light")
            toggleVoice()
            pickTheme("Dark")
            pressSystemBack()
            seeTodayTab()
        }
        acceptanceScenario(storage = storage) {
            openSettings()
            seeVoice(on = true)
            seeTheme("Dark")
        }
    }
}
