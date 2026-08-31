package io.github.mouse233.bluehotspot.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.mouse233.bluehotspot.tethering.TetheringController
import io.github.mouse233.bluehotspot.tethering.TetheringState
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface BlePeripheralState {
    data object Stopped : BlePeripheralState
    data object Advertising : BlePeripheralState
    data class Failed(val reason: String) : BlePeripheralState
}

/** Android BLE peripheral for the deliberately small, hotspot-only v1 API. */
@SuppressLint("MissingPermission")
internal class BleGattServer(
    private val context: Context,
    private val tethering: TetheringController,
) {
    private companion object {
        const val DEVICE_INFO_TEXT = "BlueHotspot/1"
        const val MAX_CHUNK = 180
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _state = MutableStateFlow<BlePeripheralState>(BlePeripheralState.Stopped)
    val state: StateFlow<BlePeripheralState> = _state.asStateFlow()

    private val decoders = mutableMapOf<String, BleFrameDecoder>()
    private val subscribedDevices = mutableSetOf<String>()
    private var server: BluetoothGattServer? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var stateJobStarted = false

    private val gattCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            synchronized(decoders) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    decoders[device.address] = BleFrameDecoder()
                } else {
                    decoders.remove(device.address)
                    subscribedDevices.remove(device.address)
                }
            }
        }

        override fun onCharacteristicReadRequest(
            device: BluetoothDevice,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (characteristic.uuid != BleUuids.DEVICE_INFO) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
                return
            }
            val value = DEVICE_INFO_TEXT.toByteArray(Charsets.UTF_8)
            if (offset > value.size) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_INVALID_OFFSET, offset, null)
            } else {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value.copyOfRange(offset, value.size))
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (responseNeeded) {
                server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
            if (preparedWrite || offset != 0 || characteristic.uuid != BleUuids.COMMAND) return
            try {
                val decoder = synchronized(decoders) {
                    decoders.getOrPut(device.address) { BleFrameDecoder() }
                }
                decoder.append(value).forEach { frame -> handleFrame(device, frame) }
            } catch (error: BleProtocolException) {
                send(device, BleFrame(BleMessage.ERROR, UUID.randomUUID(), "INVALID_MESSAGE"))
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray,
        ) {
            if (descriptor.uuid != BleUuids.CLIENT_CONFIG || preparedWrite || offset != 0) {
                if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED, 0, null)
                return
            }
            val enabled = value.contentEquals(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            synchronized(decoders) {
                if (enabled) subscribedDevices.add(device.address) else subscribedDevices.remove(device.address)
            }
            if (responseNeeded) server?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
        }
    }

    fun start() {
        if (server != null) return
        if (!hasBluetoothPermissions()) {
            _state.value = BlePeripheralState.Failed("Bluetooth permission is required")
            return
        }
        val manager = context.getSystemService(BluetoothManager::class.java)
        val adapter = manager?.adapter
        if (adapter == null || !adapter.isEnabled) {
            _state.value = BlePeripheralState.Failed("Bluetooth is unavailable")
            return
        }
        val localServer = manager.openGattServer(context, gattCallback)
        if (localServer == null) {
            _state.value = BlePeripheralState.Failed("Unable to open GATT server")
            return
        }
        val service = BluetoothGattService(BleUuids.SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val encryptedRead = BluetoothGattCharacteristic(
            BleUuids.DEVICE_INFO,
            BluetoothGattCharacteristic.PROPERTY_READ,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,
        )
        val encryptedCommand = BluetoothGattCharacteristic(
            BleUuids.COMMAND,
            BluetoothGattCharacteristic.PROPERTY_WRITE or BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE,
            BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED,
        )
        val encryptedEvent = BluetoothGattCharacteristic(
            BleUuids.EVENT,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED,
        )
        encryptedEvent.addDescriptor(
            BluetoothGattDescriptor(
                BleUuids.CLIENT_CONFIG,
                BluetoothGattDescriptor.PERMISSION_READ_ENCRYPTED or BluetoothGattDescriptor.PERMISSION_WRITE_ENCRYPTED,
            ),
        )
        val encryptedPairing = BluetoothGattCharacteristic(
            BleUuids.PAIRING,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ_ENCRYPTED or BluetoothGattCharacteristic.PERMISSION_WRITE_ENCRYPTED,
        )
        service.addCharacteristic(encryptedRead)
        service.addCharacteristic(encryptedCommand)
        service.addCharacteristic(encryptedEvent)
        service.addCharacteristic(encryptedPairing)
        if (!localServer.addService(service)) {
            localServer.close()
            _state.value = BlePeripheralState.Failed("Unable to add GATT service")
            return
        }
        server = localServer
        advertiser = adapter.bluetoothLeAdvertiser
        val localAdvertiser = advertiser
        if (localAdvertiser == null) {
            stop()
            _state.value = BlePeripheralState.Failed("BLE advertising is unavailable")
            return
        }
        localAdvertiser.startAdvertising(
            AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setConnectable(true)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .build(),
            AdvertiseData.Builder()
                .setIncludeDeviceName(false)
                .addServiceUuid(ParcelUuid(BleUuids.SERVICE))
                .build(),
            advertiseCallback,
        )
        if (!stateJobStarted) {
            stateJobStarted = true
            scope.launch {
                tethering.state.collect { current -> broadcastState(current) }
            }
        }
    }

    fun stop() {
        if (hasBluetoothPermissions()) advertiser?.stopAdvertising(advertiseCallback)
        advertiser = null
        server?.close()
        server = null
        synchronized(decoders) {
            decoders.clear()
            subscribedDevices.clear()
        }
        _state.value = BlePeripheralState.Stopped
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            _state.value = BlePeripheralState.Advertising
        }

        override fun onStartFailure(errorCode: Int) {
            _state.value = BlePeripheralState.Failed("BLE advertising failed: $errorCode")
        }
    }

    private fun handleFrame(device: BluetoothDevice, frame: BleFrame) {
        when (frame.type) {
            BleMessage.HELLO -> send(device, frame.copy(type = BleMessage.HELLO_ACK, payload = "v1"))
            BleMessage.GET_STATUS -> sendStatus(device, frame.requestId, tethering.state.value)
            BleMessage.PING -> send(device, frame.copy(type = BleMessage.PONG))
            BleMessage.START_HOTSPOT -> {
                send(device, frame.copy(type = BleMessage.HOTSPOT_STARTING))
                tethering.start()
            }
            BleMessage.STOP_HOTSPOT -> {
                tethering.stop()
            }
            else -> send(device, BleFrame(BleMessage.ERROR, frame.requestId, "UNKNOWN_REQUEST"))
        }
    }

    private fun broadcastState(state: TetheringState) {
        val event = when (state) {
            TetheringState.Starting -> BleMessage.HOTSPOT_STARTING
            TetheringState.Active -> BleMessage.HOTSPOT_READY
            TetheringState.Stopping -> BleMessage.STATUS
            TetheringState.Idle -> BleMessage.HOTSPOT_STOPPED
            TetheringState.Unsupported -> BleMessage.ERROR
            is TetheringState.Failed -> BleMessage.HOTSPOT_FAILED
        }
        val payload = when (state) {
            TetheringState.Starting -> "STARTING"
            TetheringState.Active -> "ACTIVE"
            TetheringState.Stopping -> "STOPPING"
            TetheringState.Idle -> "IDLE"
            TetheringState.Unsupported -> "TETHERING_UNSUPPORTED"
            is TetheringState.Failed -> "FAILED"
        }
        val frame = BleFrame(event, UUID.randomUUID(), payload)
        synchronized(decoders) {
            subscribedDevices.toList().forEach { address ->
                BluetoothAdapter.getDefaultAdapter()?.getRemoteDevice(address)?.let { send(it, frame) }
            }
        }
    }

    private fun sendStatus(device: BluetoothDevice, requestId: UUID, state: TetheringState) {
        val payload = when (state) {
            TetheringState.Idle -> "IDLE"
            TetheringState.Starting -> "STARTING"
            TetheringState.Active -> "ACTIVE"
            TetheringState.Stopping -> "STOPPING"
            TetheringState.Unsupported -> "UNSUPPORTED"
            is TetheringState.Failed -> "FAILED"
        }
        send(device, BleFrame(BleMessage.STATUS, requestId, payload))
    }

    private fun send(device: BluetoothDevice, frame: BleFrame) {
        val localServer = server ?: return
        if (!hasBluetoothPermissions()) return
        val value = BleFrameCodec.encode(frame)
        value.asList().chunked(MAX_CHUNK).forEach { chunk ->
            val characteristic = localServer.getService(BleUuids.SERVICE)?.getCharacteristic(BleUuids.EVENT) ?: return
            @Suppress("DEPRECATION")
            localServer.notifyCharacteristicChanged(device, characteristic, false, chunk.toByteArray())
        }
    }

    private fun hasBluetoothPermissions(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED
    }
}





