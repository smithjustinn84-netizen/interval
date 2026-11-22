@file:OptIn(ExperimentalForeignApi::class)

package dev.justinsmith.interval.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.CoreBluetooth.CBManagerState
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateResetting
import platform.CoreBluetooth.CBManagerStateUnknown
import platform.CoreBluetooth.CBManagerStateUnsupported
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSUUID
import platform.darwin.NSObject
import platform.posix.memcpy

/**
 * The standard Heart Rate service over iOS's CoreBluetooth: any strap or armband that streams the heart rate,
 * Polar's among them. A sensor is known by its peripheral's identifier, which iOS keeps the same for this app. Each
 * [connect] is a connection of its own, set up a step at a time as CoreBluetooth answers: the services found, then
 * each one's characteristics, the battery read, the measurements' notifications switched on; then a frame for each
 * measurement until the connection drops, and cancelled as its collector goes.
 *
 * The one `CBCentralManager` is made only when first wanted, a pairing's scan or a stream, since making it is what
 * asks the user for Bluetooth. It answers on the main queue, so whatever collects these flows runs on the main
 * thread. Foreground only: the app declares no Bluetooth background mode, so a connection drops once iOS suspends
 * the app, and the stream tries again on its return.
 */
class IosHeartRateLink : HeartRateRadio {
    private val central = Central()
    private val manager by lazy { CBCentralManager(delegate = central, queue = null) }

    /** Each open connection's peripheral and its delegate, which CoreBluetooth holds only weakly. */
    private val held = mutableMapOf<String, Pair<CBPeripheral, PeripheralDelegate>>()

    /** Whether the device has Bluetooth LE, as far as is known: until the manager has said, it has. */
    val supported: Boolean get() = central.state.value != CBManagerStateUnsupported

    /** Whether the app may use Bluetooth, asking nothing: not yet asked counts, since a pairing asks. */
    val allowed: Boolean
        get() = when (CBManager.authorization) {
            CBManagerAuthorizationDenied, CBManagerAuthorizationRestricted -> false
            else -> true
        }

    /**
     * Whether Bluetooth is on and the app may use it, once iOS has said: the first time, that waits on the user's
     * answer to its prompt, which making the manager raises.
     */
    suspend fun ready(): Boolean {
        manager
        val settled = withTimeoutOrNull(READY_MILLIS) {
            central.state.filterNotNull().first { it != CBManagerStateUnknown && it != CBManagerStateResetting }
        }
        return settled == CBManagerStatePoweredOn
    }

    override fun connect(id: String): Flow<HeartRateFrame> = callbackFlow {
        check(ready()) { "Bluetooth is off, or the app may not use it" }
        val known = NSUUID(uUIDString = id)
        val peripheral = manager.retrievePeripheralsWithIdentifiers(listOf(known)).firstOrNull() as? CBPeripheral
            ?: throw IllegalArgumentException("No sensor known as $id")
        // The answers the set-up waits on, in turn; the measurements go straight to the flow.
        val answers = Channel<Answer>(Channel.UNLIMITED)
        var connected = false
        val delegate = PeripheralDelegate(answers) { bytes ->
            HeartRateService.measurement(bytes)?.let { trySend(HeartRateFrame.Measured(it)) }
        }
        peripheral.delegate = delegate
        held[id] = peripheral to delegate
        central.follow(id) { event ->
            when (event) {
                CentralEvent.Connected -> {
                    connected = true
                    answers.trySend(Answer.Connected)
                }

                // A drop after it read is the end of this connection; one that never came is a failure.
                is CentralEvent.Ended ->
                    if (connected) close() else close(IllegalStateException("No connection: ${event.error}"))
            }
        }
        manager.connectPeripheral(peripheral, options = null)
        launch {
            // A set-up that never finishes ends the connection as a failure, which the stream tries again.
            withTimeoutOrNull(SET_UP_MILLIS) {
                answers.expect<Answer.Connected>()
                peripheral.discoverServices(listOf(SERVICE, BATTERY_SERVICE))
                answers.expect<Answer.Done>().succeeded("finding the services")
                val services = peripheral.services.orEmpty().filterIsInstance<CBService>()
                services.firstOrNull { it.UUID == BATTERY_SERVICE }?.let { battery ->
                    // Not every sensor says its battery: a read that fails is no failure of the stream.
                    peripheral.discoverCharacteristics(listOf(BATTERY_LEVEL), forService = battery)
                    val found = answers.expect<Answer.Done>()
                    val level = battery.characteristic(BATTERY_LEVEL)
                    if (found.error == null && level != null) {
                        peripheral.readValueForCharacteristic(level)
                        val read = answers.expect<Answer.Read>()
                        read.value?.let(HeartRateService::batteryLevel)?.let { send(HeartRateFrame.Battery(it)) }
                    }
                }
                val heart = services.firstOrNull { it.UUID == SERVICE }
                    ?: throw IllegalStateException("$id has no Heart Rate service")
                peripheral.discoverCharacteristics(listOf(MEASUREMENT), forService = heart)
                answers.expect<Answer.Done>().succeeded("finding the heart rate")
                val measurement = heart.characteristic(MEASUREMENT)
                    ?: throw IllegalStateException("$id's heart rate can't be read")
                peripheral.setNotifyValue(true, forCharacteristic = measurement)
            } ?: close(IllegalStateException("$id wasn't set up in time"))
        }
        awaitClose {
            central.unfollow(id)
            manager.cancelPeripheralConnection(peripheral)
            peripheral.delegate = null
            held.remove(id)
        }
    }

    override fun scan(): Flow<HeardSensor> = callbackFlow {
        check(ready()) { "Bluetooth is off, or the app may not use it" }
        launch(start = CoroutineStart.UNDISPATCHED) { central.heard.collect { send(it) } }
        // Every peripheral, not only those advertising the service: some of Polar's leave it out.
        manager.scanForPeripheralsWithServices(null, options = null)
        awaitClose { manager.stopScan() }
    }

    private fun Answer.Done.succeeded(what: String) = check(error == null) { "Failed $what: $error" }

    /** What the central manager says of one peripheral. */
    private sealed interface CentralEvent {
        data object Connected : CentralEvent

        class Ended(val error: NSError?) : CentralEvent
    }

    /** What each of the set-up's steps answered. */
    private sealed interface Answer {
        data object Connected : Answer

        class Done(val error: NSError?) : Answer

        class Read(val value: ByteArray?) : Answer
    }

    /** The central manager's delegate: its state, what a scan hears, and each followed peripheral's connection. */
    private class Central :
        NSObject(),
        CBCentralManagerDelegateProtocol {
        val state = MutableStateFlow<CBManagerState?>(null)
        val heard = MutableSharedFlow<HeardSensor>(extraBufferCapacity = HEARD_BUFFER)
        private val followers = mutableMapOf<String, (CentralEvent) -> Unit>()

        fun follow(id: String, follower: (CentralEvent) -> Unit) {
            followers[id] = follower
        }

        fun unfollow(id: String) {
            followers.remove(id)
        }

        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            state.value = central.state
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber,
        ) {
            val name = advertisementData[CBAdvertisementDataLocalNameKey] as? String ?: didDiscoverPeripheral.name
                ?: return
            val services = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? List<*>).orEmpty()
            if (SERVICE in services || HeartRateService.isPolarSensor(name)) {
                heard.tryEmit(HeardSensor(didDiscoverPeripheral.identifier.UUIDString, name, RSSI.intValue))
            }
        }

        override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
            followers[didConnectPeripheral.identifier.UUIDString]?.invoke(CentralEvent.Connected)
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didDisconnectPeripheral: CBPeripheral,
            error: NSError?,
        ) {
            followers[didDisconnectPeripheral.identifier.UUIDString]?.invoke(CentralEvent.Ended(error))
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didFailToConnectPeripheral: CBPeripheral,
            error: NSError?,
        ) {
            followers[didFailToConnectPeripheral.identifier.UUIDString]?.invoke(CentralEvent.Ended(error))
        }
    }

    /** One connection's peripheral delegate: the set-up's answers to [answers], each measurement to [measured]. */
    private class PeripheralDelegate(
        private val answers: Channel<Answer>,
        private val measured: (ByteArray) -> Unit,
    ) : NSObject(),
        CBPeripheralDelegateProtocol {
        override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) {
            answers.trySend(Answer.Done(didDiscoverServices))
        }

        override fun peripheral(
            peripheral: CBPeripheral,
            didDiscoverCharacteristicsForService: CBService,
            error: NSError?,
        ) {
            answers.trySend(Answer.Done(error))
        }

        // A read's answer and every notification alike, told apart by the characteristic.
        @ObjCSignatureOverride
        override fun peripheral(
            peripheral: CBPeripheral,
            didUpdateValueForCharacteristic: CBCharacteristic,
            error: NSError?,
        ) {
            val bytes = didUpdateValueForCharacteristic.value?.toByteArray()
            if (didUpdateValueForCharacteristic.UUID == MEASUREMENT) {
                if (error == null && bytes != null) measured(bytes)
            } else {
                answers.trySend(Answer.Read(bytes.takeIf { error == null }))
            }
        }
    }

    private companion object {
        val SERVICE: CBUUID = CBUUID.UUIDWithString(HeartRateService.SERVICE_SHORT)
        val MEASUREMENT: CBUUID = CBUUID.UUIDWithString(HeartRateService.MEASUREMENT_SHORT)
        val BATTERY_SERVICE: CBUUID = CBUUID.UUIDWithString(HeartRateService.BATTERY_SERVICE_SHORT)
        val BATTERY_LEVEL: CBUUID = CBUUID.UUIDWithString(HeartRateService.BATTERY_LEVEL_SHORT)

        /** How long a connection has to come and be set up, as on Android. */
        const val SET_UP_MILLIS = 35_000L

        /** How long iOS may take to say whether Bluetooth is on: the first time, the user answers its prompt. */
        const val READY_MILLIS = 120_000L

        /** Sensors heard and not yet taken by a scan's collector. */
        const val HEARD_BUFFER = 64
    }
}

/** The service's characteristic [uuid], once discovered. */
private fun CBService.characteristic(uuid: CBUUID): CBCharacteristic? =
    characteristics.orEmpty().filterIsInstance<CBCharacteristic>().firstOrNull { it.UUID == uuid }

/** The next answer of the kind [T], passing over any other: a late one from a step gone by. */
private suspend inline fun <reified T> Channel<*>.expect(): T {
    while (true) {
        val answer = receive()
        if (answer is T) return answer
    }
}

private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
