package dev.justinsmith.interval.platform

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

/**
 * The standard Heart Rate service over Android's own Bluetooth: any strap or armband that streams the heart rate,
 * Polar's among them. Each [connect] is a GATT connection of its own, set up one step at a time (each of Android's
 * GATT calls waits for the last one's answer): the services found, the battery read, the measurements' notifications
 * switched on; then a frame for each measurement until the connection drops, and closed as its collector goes.
 *
 * The caller holds Bluetooth's permissions (`BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, Android 12's) before it asks.
 */
@SuppressLint("MissingPermission")
class AndroidHeartRateLink(private val context: Context) : HeartRateRadio {
    private val adapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    override fun connect(id: String): Flow<HeartRateFrame> = callbackFlow {
        val device = adapter?.takeIf { BluetoothAdapter.checkBluetoothAddress(id) }?.getRemoteDevice(id)
            ?: throw IllegalArgumentException("No Bluetooth, or no sensor at $id")
        // The answers the set-up waits on, in turn; the measurements go straight to the flow.
        val answers = Channel<Answer>(Channel.UNLIMITED)
        var connected = false
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                when {
                    newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS -> {
                        connected = true
                        answers.trySend(Answer.Connected)
                    }

                    newState == BluetoothProfile.STATE_DISCONNECTED || status != BluetoothGatt.GATT_SUCCESS ->
                        // A drop after it read is the end of this connection; one that never came is a failure.
                        if (connected) close() else close(IllegalStateException("No connection: GATT status $status"))
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                answers.trySend(Answer.Done(status))
            }

            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
                answers.trySend(Answer.Done(status))
            }

            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
                status: Int,
            ) {
                answers.trySend(Answer.Read(value, status))
            }

            // Android 12's: from 13 the one above is called, with the value.
            @Deprecated("Android 12's, its value on the characteristic")
            @Suppress("DEPRECATION")
            override fun onCharacteristicRead(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int,
            ) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    answers.trySend(Answer.Read(characteristic.value ?: ByteArray(0), status))
                }
            }

            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                value: ByteArray,
            ) {
                measured(characteristic, value)
            }

            // Android 12's: from 13 the one above is called, and this one too, so it reads only below 13.
            @Deprecated("Android 12's, its value on the characteristic")
            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    measured(characteristic, characteristic.value ?: ByteArray(0))
                }
            }

            private fun measured(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (characteristic.uuid != MEASUREMENT) return
                HeartRateService.measurement(value)?.let { trySend(HeartRateFrame.Measured(it)) }
            }
        }
        val gatt = device.connectLe(callback) ?: throw IllegalStateException("No GATT for $id")
        launch {
            // A set-up that never finishes (a connection that never comes, an answer that never does) ends the
            // connection as a failure, which the stream tries again: its timeout alone would end only this.
            withTimeoutOrNull(SET_UP_MILLIS) {
                answers.expect<Answer.Connected>()
                check(gatt.discoverServices()) { "Couldn't look for the services" }
                answers.expect<Answer.Done>().succeeded("finding the services")
                gatt.getService(BATTERY_SERVICE)?.getCharacteristic(BATTERY_LEVEL)?.let { level ->
                    // Not every sensor says its battery: a read that fails is no failure of the stream.
                    if (gatt.readCharacteristic(level)) {
                        val read = answers.expect<Answer.Read>()
                        if (read.status == BluetoothGatt.GATT_SUCCESS) {
                            HeartRateService.batteryLevel(read.value)?.let { send(HeartRateFrame.Battery(it)) }
                        }
                    }
                }
                val measurement = gatt.getService(SERVICE)?.getCharacteristic(MEASUREMENT)
                    ?: throw IllegalStateException("$id has no Heart Rate service")
                check(gatt.setCharacteristicNotification(measurement, true)) { "Couldn't hear the heart rate" }
                val notifications = measurement.getDescriptor(NOTIFICATIONS)
                    ?: throw IllegalStateException("$id's heart rate can't be notified")
                check(gatt.enable(notifications)) { "Couldn't switch the heart rate's notifications on" }
                answers.expect<Answer.Done>().succeeded("switching the notifications on")
            } ?: close(IllegalStateException("$id wasn't set up in time"))
        }
        awaitClose {
            gatt.disconnect()
            gatt.close()
        }
    }

    override fun scan(): Flow<HeardSensor> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner ?: throw IllegalStateException("No Bluetooth")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.scanRecord?.deviceName ?: result.device.name ?: return
                val serves = result.scanRecord?.serviceUuids?.contains(ParcelUuid(SERVICE)) == true
                if (serves || HeartRateService.isPolarSensor(name)) {
                    trySend(HeardSensor(result.device.address, name, result.rssi))
                }
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("The scan failed: $errorCode"))
            }
        }
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(null, settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }

    /**
     * A connection over Bluetooth LE, made at once rather than whenever the sensor comes in reach. Android 17 makes
     * it from connection settings instead, which nothing older has.
     */
    @Suppress("DEPRECATION")
    private fun BluetoothDevice.connectLe(callback: BluetoothGattCallback): BluetoothGatt? =
        connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)

    /** What each of the set-up's GATT calls answered. */
    private sealed interface Answer {
        data object Connected : Answer

        class Done(val status: Int) : Answer

        class Read(val value: ByteArray, val status: Int) : Answer
    }

    /** The next answer of the kind [T], passing over any other: a late one from a step gone by. */
    private suspend inline fun <reified T : Answer> Channel<Answer>.expect(): T {
        while (true) {
            val answer = receive()
            if (answer is T) return answer
        }
    }

    private fun Answer.Done.succeeded(what: String) =
        check(status == BluetoothGatt.GATT_SUCCESS) { "Failed $what: GATT status $status" }

    /** Writes [descriptor] to switch its characteristic's notifications on, as each version of Android does it. */
    @Suppress("DEPRECATION")
    private fun BluetoothGatt.enable(descriptor: BluetoothGattDescriptor): Boolean {
        val on = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            writeDescriptor(descriptor, on) == BluetoothGatt.GATT_SUCCESS
        } else {
            descriptor.value = on
            writeDescriptor(descriptor)
        }
    }

    private companion object {
        val SERVICE: UUID = UUID.fromString(HeartRateService.SERVICE)
        val MEASUREMENT: UUID = UUID.fromString(HeartRateService.MEASUREMENT)
        val BATTERY_SERVICE: UUID = UUID.fromString(HeartRateService.BATTERY_SERVICE)
        val BATTERY_LEVEL: UUID = UUID.fromString(HeartRateService.BATTERY_LEVEL)
        val NOTIFICATIONS: UUID = UUID.fromString(HeartRateService.NOTIFICATIONS)

        /** How long a connection has to come and be set up: Android gives up on its own at about 30 s. */
        const val SET_UP_MILLIS = 35_000L
    }
}
