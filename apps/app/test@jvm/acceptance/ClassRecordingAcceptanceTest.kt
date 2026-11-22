package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/**
 * `journeys/class-recording.xml`: a heart-rate sensor paired in Settings records a class on its own while the
 * phone is in the bag; synced after, the class is logged and opens with its rounds, each with its heart rate and
 * how far that fell after. The sensor is a phone's to reach over Bluetooth, so the JVM's is one in memory that is
 * told what it found and what it recorded.
 */
class ClassRecordingAcceptanceTest {
    // The app's clock starts on Thursday 1 January 1970.
    @Test
    fun `a class recorded on the sensor is synced into the log with its rounds`() = acceptanceScenario {
        haveSensorToPair()
        openSettings()
        pairSensor()
        pressSystemBack()

        openPlanFromToday()
        planSportEvery("BJJ", weekday = "Thursday")
        goBack()
        seeRecordClassOffered()
        recordClass()

        trainWithSensor(rounds = 3)
        syncClass()
        seeClassPage(rounds = 3)
        classesKept(1)
        sportDaysLogged(1)

        goBack()
        seeClassInLog(rounds = 3)
        openTab("Today")
        seeSportOnTheWeek("Today, done, BJJ logged")
        seeNoSensorOnToday()
    }

    @Test
    fun `a strap that only streams pairs and beats live and is never offered a class`() = acceptanceScenario {
        haveOtherStrapToPair()
        openSettings()
        pairOtherStrap()
        pressSystemBack()

        openPlanFromToday()
        planSportEvery("BJJ", weekday = "Thursday")
        goBack()
        seeNoSensorOnToday()

        heartBeatsAt(72)
        seeStrapPulse(bpm = 72, zone = 1)
    }

    @Test
    fun `with an armband and a strap both nearby pairing asks which`() = acceptanceScenario {
        haveSensorToPair()
        haveStrapNearbyToo()
        openSettings()
        pairArmbandAmongTwo()
    }

    @Test
    fun `a sensor out of reach says so and the class stays on it to sync later`() = acceptanceScenario {
        haveSensorToPair()
        openSettings()
        pairSensor()
        pressSystemBack()
        openPlanFromToday()
        planSportEvery("BJJ", weekday = "Thursday")
        goBack()
        recordClass()
        trainWithSensor(rounds = 2)

        sensorInReach(false)
        syncClass()
        seeSensorCouldNotBeFound()
        classesKept(0)
        sportDaysLogged(0)

        sensorInReach(true)
        syncClass()
        seeClassPage(rounds = 2)
        classesKept(1)
    }

    @Test
    fun `deleting a recording keeps its day logged`() = acceptanceScenario {
        haveSensorToPair()
        openSettings()
        pairSensor()
        pressSystemBack()
        openPlanFromToday()
        planSportEvery("BJJ", weekday = "Thursday")
        goBack()
        recordClass()
        trainWithSensor(rounds = 3)
        syncClass()
        seeClassPage(rounds = 3)

        deleteRecording()
        classesKept(0)
        sportDaysLogged(1)
        see("Logged")
    }

    @Test
    fun `with no sensor to reach nothing of one shows`() = acceptanceScenario {
        openPlanFromToday()
        planSportEvery("BJJ", weekday = "Thursday")
        goBack()
        seeNoSensorOnToday()
        openSettings()
        seeNoSensorInSettings()
    }
}
