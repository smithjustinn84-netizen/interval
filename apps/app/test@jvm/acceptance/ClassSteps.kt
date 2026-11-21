package dev.justinsmith.interval.acceptance

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import dev.justinsmith.interval.fakes.FakeLiveHeartRate
import dev.justinsmith.interval.fakes.RecordingClassSensor
import dev.justinsmith.interval.fakes.SENSOR_NAME
import dev.justinsmith.interval.fakes.sparringRecording
import dev.justinsmith.interval.platform.ClassSensor
import dev.justinsmith.interval.platform.LiveHeartRate
import dev.justinsmith.interval.platform.LiveHeartRateState
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

/** A chest strap is in reach beside the armband, as with both in the bag. */
fun IntervalAcceptanceDriver.haveStrapNearbyToo() {
    sensor.alsoNearby = STRAP_NAME
}

/** In Settings, with two in reach: asked which, picks the armband, which then stands by its name. */
fun IntervalAcceptanceDriver.pairArmbandAmongTwo() {
    scrollTo("Pair a sensor")
    tap("Pair a sensor")
    see("Which sensor?")
    see(STRAP_NAME)
    tap(SENSOR_NAME)
    waitFor("the armband paired") { sensor.status.value == SensorStatus.Paired(SENSOR_NAME) }
    seeNo("Which sensor?")
}

private const val STRAP_NAME = "Polar H10 5E6F7A8B"

private val IntervalAcceptanceDriver.heart: FakeLiveHeartRate
    get() = app.koin.koin.get<LiveHeartRate>() as FakeLiveHeartRate

/** A sensor is the app's already, within reach but not yet read, its battery as it last said. */
fun IntervalAcceptanceDriver.haveSensorPaired(battery: Int? = null) {
    heart.emit(LiveHeartRateState.Idle)
    sensor.battery.value = battery
    sensor.status.value = SensorStatus.Paired(SENSOR_NAME)
}

/** The sensor reads the heart at [bpm], once the app has reached for it. */
fun IntervalAcceptanceDriver.heartBeatsAt(bpm: Int) {
    heartRateIsStreamed()
    heart.emitBpm(bpm)
}

/** The sensor goes out of reach. */
fun IntervalAcceptanceDriver.sensorDropsOut() = heart.emit(LiveHeartRateState.Disconnected)

/** The app holds the sensor's stream for as long as it is on show with one paired. */
fun IntervalAcceptanceDriver.heartRateIsStreamed(streamed: Boolean = true) =
    waitFor("the heart rate streamed: $streamed") { heart.started == streamed }

/** The title bar of the tab on show has the sensor's pulse, reading [bpm] in [zone]. */
fun IntervalAcceptanceDriver.seePulse(bpm: Int, zone: Int) =
    see(hasContentDescription("Heart rate $bpm, zone $zone, $SENSOR_NAME") and hasClickAction())

/** The pulse stands dim: the sensor is paired and not read. */
fun IntervalAcceptanceDriver.seePulseNotConnected() =
    see(hasContentDescription("Heart-rate sensor $SENSOR_NAME, not connected") and hasClickAction())

fun IntervalAcceptanceDriver.seePulseBatteryLow() =
    see(hasContentDescription("Heart-rate sensor $SENSOR_NAME, not connected, battery low"))

fun IntervalAcceptanceDriver.seeNoPulse() = seeNo(hasContentDescription("Heart-rate sensor", substring = true))

/** Taps the pulse: the sensor's sheet, by its name. */
fun IntervalAcceptanceDriver.openSensorSheet() {
    tap(hasContentDescription(SENSOR_NAME, substring = true) and hasClickAction())
    see(SENSOR_NAME)
}

/** The sheet's heart rate, read as one line: the number, and its zone by number and name. */
fun IntervalAcceptanceDriver.seeSheetReading(bpm: Int, zone: String) =
    see(hasContentDescription("$bpm beats per minute, $zone"))

/** The sheet's way to Settings, where the sensor is forgotten. */
fun IntervalAcceptanceDriver.openSensorSettings() {
    tap("Sensor settings")
    seeNo("Sensor settings")
    scrollTo(SENSOR_NAME)
    see(hasText("Tap to forget it", substring = true))
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
