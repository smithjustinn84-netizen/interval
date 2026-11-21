package dev.justinsmith.interval.acceptance

import kotlin.test.Test

/**
 * The paired heart-rate sensor on every tab: its heart rate beside the gear, and a tap away the sheet that says
 * the rest (`journeys/class-recording.xml` has it on a real sensor). The JVM's sensor and its heart rate are in
 * memory, told what they read.
 */
class SensorPulseAcceptanceTest {
    @Test
    fun `the paired sensor's heart rate is on every tab`() = acceptanceScenario {
        haveSensorPaired()
        heartRateIsStreamed()
        seePulseNotConnected()

        heartBeatsAt(72)
        seePulse(bpm = 72, zone = 1)
        openTab("Train")
        seePulse(bpm = 72, zone = 1)
        pickPillar("Conditioning")
        seePulse(bpm = 72, zone = 1)
        pickPillar("Mobility")
        seePulse(bpm = 72, zone = 1)
        openTab("Log")
        heartBeatsAt(160)
        seePulse(bpm = 160, zone = 4)

        sensorDropsOut()
        seePulseNotConnected()
    }

    @Test
    fun `a tap on the pulse opens the sensor's sheet which leads to its settings`() = acceptanceScenario {
        haveSensorPaired(battery = 64)
        heartBeatsAt(142)
        openSensorSheet()
        seeSheetReading(bpm = 142, zone = "Zone 3, Tempo")
        see("Battery 64%")

        openSensorSettings()
    }

    @Test
    fun `a sensor not read says why and a low battery shows on the pulse`() = acceptanceScenario {
        haveSensorPaired(battery = 12)
        sensorDropsOut()
        seePulseBatteryLow()
        openSensorSheet()
        see("Not connected. Wear it and keep it close.")
        see("Battery low, 12%: charge it before a class")
    }

    @Test
    fun `with no sensor paired no tab shows a pulse and nothing is streamed`() = acceptanceScenario {
        seeNoPulse()
        heartRateIsStreamed(false)
        openTab("Train")
        seeNoPulse()
    }
}
