package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasText
import dev.justinsmith.interval.fakes.RecordingClassSensor
import dev.justinsmith.interval.fakes.SENSOR_NAME
import dev.justinsmith.interval.fakes.sparringRecording
import dev.justinsmith.interval.platform.ClassSensor
import dev.justinsmith.interval.platform.SensorFailure
import dev.justinsmith.interval.platform.SensorStatus

// A class recorded on the heart-rate sensor, beside the driver, which is at its size limit. Only a phone reaches a
// real sensor, so the JVM's is one in memory: these steps say what it finds and what it holds.

private val IntervalAcceptanceDriver.sensor: RecordingClassSensor
    get() = app.koin.koin.get<ClassSensor>() as RecordingClassSensor

/** The device can reach a sensor, and none is paired yet: Settings offers to pair one. */
fun IntervalAcceptanceDriver.haveSensorToPair() {
    sensor.status.value = SensorStatus.Unpaired
}

/** In Settings: pairs the sensor nearby, which then stands by its name. */
fun IntervalAcceptanceDriver.pairSensor() {
    scrollTo("Pair a sensor")
    tap("Pair a sensor")
    see(SENSOR_NAME)
    waitFor("the sensor paired") { sensor.status.value == SensorStatus.Paired(SENSOR_NAME) }
}

fun IntervalAcceptanceDriver.seeNoSensorInSettings() = seeNo("Heart-rate sensor")

/** Today, on a class's day, offers to record it. */
fun IntervalAcceptanceDriver.seeRecordClassOffered() {
    scrollTo("Record class")
    see("Record the class")
}

fun IntervalAcceptanceDriver.seeNoSensorOnToday() {
    seeNo("Record class")
    seeNo("Sync class")
}

fun IntervalAcceptanceDriver.recordClass() {
    scrollTo("Record class")
    tap("Record class")
    see(hasText("Recording since", substring = true))
    waitFor("the sensor recording") { sensor.status.value is SensorStatus.Recording }
}

/** The class happens, away from the phone: the sensor now holds it, [rounds] rounds of sparring in it. */
fun IntervalAcceptanceDriver.trainWithSensor(rounds: Int) {
    sensor.recorded = sparringRecording(rounds)
}

/** The sensor in the bag, switched off, which nothing reaches; or back on the arm, in [reach]. */
fun IntervalAcceptanceDriver.sensorInReach(reach: Boolean) {
    sensor.failing = SensorFailure.NotFound.takeUnless { reach }
}

fun IntervalAcceptanceDriver.syncClass() {
    scrollTo("Sync class")
    tap("Sync class")
}

/** The class's page, opened by its sync: its rounds, each with its heart rate. */
fun IntervalAcceptanceDriver.seeClassPage(rounds: Int) {
    see("Avg heart rate")
    see(hasText("Round $rounds", substring = true))
    seeNo(hasText("Round ${rounds + 1}", substring = true))
    see(hasText("170 peak", substring = true))
}

fun IntervalAcceptanceDriver.classesKept(count: Int) = waitFor("$count class(es) kept") {
    app.repository.history.classes.value.size == count
}

/** The log's row of the class: its sport, saying its rounds, which opens the page again. */
fun IntervalAcceptanceDriver.seeClassInLog(rounds: Int) {
    see("Done this week")
    see(hasText("BJJ class") and hasText("$rounds rounds", substring = true))
}

fun IntervalAcceptanceDriver.openClassFromLog() {
    tap(hasText("BJJ class") and hasText("rounds", substring = true))
    see("Avg heart rate")
}

fun IntervalAcceptanceDriver.deleteRecording() {
    tapDescribed("More options")
    tap("Delete recording")
    tap("Delete")
    see("Recording deleted")
}

fun IntervalAcceptanceDriver.seeSensorCouldNotBeFound() = see(hasText("Couldn’t find the sensor", substring = true))
