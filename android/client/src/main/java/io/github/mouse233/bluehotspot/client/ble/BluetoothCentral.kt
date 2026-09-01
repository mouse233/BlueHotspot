package io.github.mouse233.bluehotspot.client.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class DiscoveredDevice(
    val id: String,
    val name: String,
    val rssi: Int,
)

internal enum class BluetoothState {
    Unsupported,
    PoweredOff,
    Ready,
}

internal enum class ConnectionState {
    Disconnected,
    Scanning,
    Connecting,
    Pairing,
    Connected,
}

/** Android BLE central for the BlueHotspot v1 encrypted GATT protocol. */
@SuppressLint("MissingPermission")
internal class BluetoothCentral(context: Context) : AutoCloseable {
    private companion object {
        const val MAX_CHUNK = 180
        const val REQUESTED_MTU = 247
        const val RSSI_REFRESH_INTERVAL_MS = 2_000L
        const val SCAN_REPORT_DELAY_MS = 2_000L
        const val PREFERENCES_NAME = "bluehotspot_client"
        const val AUTO_CONNECT_KEY = "auto_connect_enabled"
        const val PREFERRED_DEVICE_ID_KEY = "preferred_device_id"
    }

    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val adapter: BluetoothAdapter? =
        appContext.getSystemService(BluetoothManager::class.java)?.adapter
    private val scanner get() = adapter?.bluetoothLeScanner

    private val _bluetoothState = MutableStateFlow(currentBluetoothState())
    val bluetoothState: StateFlow<BluetoothState> = _bluetoothState.asStateFlow()
    private val _connectionState = MutableStateFlow(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()
    private val _hotspotState = MutableStateFlow("Unknown")
    val hotspotState: StateFlow<String> = _hotspotState.asStateFlow()
    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()
    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()
    private val _autoConnectEnabled = MutableStateFlow(
        preferences.getBoolean(AUTO_CONNECT_KEY, true),
    )
    val autoConnectEnabled: StateFlow<Boolean> = _autoConnectEnabled.asStateFlow()

    private val discoveredDevices = mutableMapOf<String, BluetoothDevice>()
    private val writeQueue = ArrayDeque<ByteArray>()
    private var writeInProgress = false
    private var decoder = BleFrameDecoder()
    private var currentDevice: BluetoothDevice? = null
    private var currentGatt: BluetoothGatt? = null
    private var commandCharacteristic: BluetoothGattCharacteristic? = null
    private var eventCharacteristic: BluetoothGattCharacteristic? = null
    private var negotiatedMtu = 23
    private var servicesDiscovering = false
    private var ready = false
    private val rssiHandler = Handler(Looper.getMainLooper())
    private val rssiRefresh = object : Runnable {
        override fun run() {
            val gatt = currentGatt
            if (ready && gatt != null) {
                gatt.readRemoteRssi()
                rssiHandler.postDelayed(this, RSSI_REFRESH_INTERVAL_MS)
            }
        }
    }

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
            if (device?.address != currentDevice?.address) return
            when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                BluetoothDevice.BOND_BONDED -> {
                    if (currentGatt != null) beginServiceDiscovery()
                }
                BluetoothDevice.BOND_NONE -> fail("Bluetooth pairing was rejected")
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            publishScanResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::publishScanResult)
        }

        override fun onScanFailed(errorCode: Int) {
            _connectionState.value = ConnectionState.Disconnected
            _lastError.value = "BLE scan failed: $errorCode"
        }
    }
    private fun publishScanResult(result: ScanResult) {
        val device = result.device
        val id = device.address
        discoveredDevices[id] = device
        val name = device.name
            ?: result.scanRecord?.deviceName
            ?: "BlueHotspot server"
        val discovered = DiscoveredDevice(id, name, result.rssi)
        _devices.value = (_devices.value.filterNot { it.id == id } + discovered)
            .sortedBy { it.name.lowercase() }
        if (_autoConnectEnabled.value && _connectionState.value == ConnectionState.Scanning &&
            preferences.getString(PREFERRED_DEVICE_ID_KEY, null) == id
        ) {
            connect(discovered)
        }
    }

    init {
        ContextCompat.registerReceiver(
            appContext,
            bondReceiver,
            IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    fun refreshBluetoothState() {
        _bluetoothState.value = currentBluetoothState()
    }

    fun startScanning() {
        refreshBluetoothState()
        if (_bluetoothState.value != BluetoothState.Ready) {
            _lastError.value = "Bluetooth is unavailable"
            return
        }
        try {
            stopScanning()
            stopRssiUpdates()
            discoveredDevices.clear()
            _devices.value = emptyList()
            _lastError.value = null
            _connectionState.value = ConnectionState.Scanning
            scanner?.startScan(
                listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(BleUuids.SERVICE)).build()),
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(SCAN_REPORT_DELAY_MS)
                    .build(),
                scanCallback,
            )
        } catch (error: SecurityException) {
            fail("Bluetooth permission is required")
        }
    }

    fun connect(device: DiscoveredDevice) {
        val target = discoveredDevices[device.id]
        if (target == null) {
            _lastError.value = "Device is no longer available"
            return
        }
        try {
            stopScanning()
            closeGatt()
            currentDevice = target
            preferences.edit().putString(PREFERRED_DEVICE_ID_KEY, device.id).apply()
            _deviceName.value = device.name
            _connectionState.value = ConnectionState.Connecting
            _lastError.value = null
            currentGatt = target.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE,
            )
        } catch (error: SecurityException) {
            fail("Bluetooth permission is required")
        }
    }

    fun disconnect() {
        closeGatt()
        currentDevice = null
        _deviceName.value = null
        _connectionState.value = ConnectionState.Disconnected
        _hotspotState.value = "Unknown"
    }

    fun setAutoConnectEnabled(enabled: Boolean) {
        _autoConnectEnabled.value = enabled
        preferences.edit().putBoolean(AUTO_CONNECT_KEY, enabled).apply()
        if (enabled) attemptAutomaticConnection()
    }

    private fun attemptAutomaticConnection() {
        if (!_autoConnectEnabled.value || _connectionState.value != ConnectionState.Scanning) return
        val preferredId = preferences.getString(PREFERRED_DEVICE_ID_KEY, null) ?: return
        _devices.value.firstOrNull { it.id == preferredId }?.let(::connect)
    }

    fun startHotspot() {
        send(BleFrame(BleMessage.START_HOTSPOT, UUID.randomUUID()))
    }

    fun stopHotspot() {
        send(BleFrame(BleMessage.STOP_HOTSPOT, UUID.randomUUID()))
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (gatt !== currentGatt) return
            if (newState != BluetoothGatt.STATE_CONNECTED || status != BluetoothGatt.GATT_SUCCESS) {
                fail("BLE connection lost (status $status)")
                closeGatt()
                _connectionState.value = ConnectionState.Disconnected
                return
            }
            try {
                val device = currentDevice ?: return
                if (device.bondState == BluetoothDevice.BOND_BONDED) {
                    beginServiceDiscovery()
                } else {
                    _connectionState.value = ConnectionState.Pairing
                    if (!device.createBond() && device.bondState != BluetoothDevice.BOND_BONDING) {
                        fail("Unable to start Bluetooth pairing")
                    }
                }
            } catch (error: SecurityException) {
                fail("Bluetooth permission is required")
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (gatt !== currentGatt) return
            negotiatedMtu = if (status == BluetoothGatt.GATT_SUCCESS) mtu else 23
            discoverServices(gatt)
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (gatt !== currentGatt) return
            servicesDiscovering = false
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("Unable to discover BlueHotspot service")
                return
            }
            val service = gatt.getService(BleUuids.SERVICE)
            commandCharacteristic = service?.getCharacteristic(BleUuids.COMMAND)
            eventCharacteristic = service?.getCharacteristic(BleUuids.EVENT)
            val command = commandCharacteristic
            val event = eventCharacteristic
            if (command == null || event == null) {
                fail("BlueHotspot service is incomplete")
                return
            }
            if (!gatt.setCharacteristicNotification(event, true)) {
                fail("Unable to enable hotspot notifications")
                return
            }
            val descriptor = event.getDescriptor(BleUuids.CLIENT_CONFIG)
            if (descriptor == null || gatt.writeDescriptor(
                    descriptor,
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
                ) != BluetoothStatusCodes.SUCCESS
            ) {
                fail("Unable to subscribe to hotspot events")
            }
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (gatt !== currentGatt || descriptor.uuid != BleUuids.CLIENT_CONFIG) return
            if (status != BluetoothGatt.GATT_SUCCESS) {
                fail("Unable to subscribe to hotspot events (status $status)")
                return
            }
            ready = true
            startRssiUpdates()
            _connectionState.value = ConnectionState.Connected
            send(BleFrame(BleMessage.HELLO, UUID.randomUUID()))
            send(BleFrame(BleMessage.GET_STATUS, UUID.randomUUID()))
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            if (gatt !== currentGatt || characteristic.uuid != BleUuids.EVENT) return
            try {
                decoder.append(value).forEach(::apply)
            } catch (error: BleProtocolException) {
                fail("Invalid message received from Android")
            }
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
            if (gatt !== currentGatt || status != BluetoothGatt.GATT_SUCCESS) return
            val address = gatt.device.address
            _devices.value = _devices.value.map { device ->
                if (device.id == address) device.copy(rssi = rssi) else device
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (gatt !== currentGatt || characteristic.uuid != BleUuids.COMMAND) return
            writeInProgress = false
            if (status != BluetoothGatt.GATT_SUCCESS) {
                _lastError.value = "BLE command write failed: $status"
                writeQueue.clear()
                return
            }
            pumpWrites()
        }
    }

    private fun beginServiceDiscovery() {
        val gatt = currentGatt ?: return
        if (servicesDiscovering) return
        if (gatt.requestMtu(REQUESTED_MTU)) return
        negotiatedMtu = 23
        discoverServices(gatt)
    }

    private fun discoverServices(gatt: BluetoothGatt) {
        if (servicesDiscovering) return
        servicesDiscovering = true
        if (!gatt.discoverServices()) {
            servicesDiscovering = false
            fail("Unable to discover BlueHotspot service")
        }
    }

    private fun send(frame: BleFrame) {
        if (!ready || commandCharacteristic == null) {
            _lastError.value = "Not connected to a paired Android device"
            return
        }
        writeQueue.addAll(BleFrameCodec.encode(frame).asList().chunked(chunkSize()).map { it.toByteArray() })
        pumpWrites()
    }

    private fun pumpWrites() {
        val gatt = currentGatt ?: return
        val characteristic = commandCharacteristic ?: return
        if (writeInProgress || writeQueue.isEmpty()) return
        val chunk = writeQueue.removeFirst()
        writeInProgress = true
        val status = gatt.writeCharacteristic(
            characteristic,
            chunk,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
        )
        if (status != BluetoothGatt.GATT_SUCCESS) {
            writeInProgress = false
            writeQueue.clear()
            _lastError.value = "BLE command write failed: $status"
        }
    }

    private fun chunkSize(): Int = minOf(MAX_CHUNK, (negotiatedMtu - 3).coerceAtLeast(20))

    private fun apply(frame: BleFrame) {
        when (frame.type) {
            BleMessage.STATUS -> _hotspotState.value = frame.payload
            BleMessage.HOTSPOT_STARTING -> _hotspotState.value = "STARTING"
            BleMessage.HOTSPOT_READY -> _hotspotState.value = "ACTIVE"
            BleMessage.HOTSPOT_STOPPED -> _hotspotState.value = "IDLE"
            BleMessage.HOTSPOT_FAILED -> {
                _hotspotState.value = "FAILED"
                _lastError.value = frame.payload.ifEmpty { "Hotspot operation failed" }
            }
            BleMessage.ERROR -> _lastError.value = frame.payload.ifEmpty { "Android rejected the request" }
            else -> Unit
        }
    }

    private fun currentBluetoothState(): BluetoothState = try {
        when {
            adapter == null -> BluetoothState.Unsupported
            !adapter.isEnabled -> BluetoothState.PoweredOff
            else -> BluetoothState.Ready
        }
    } catch (error: SecurityException) {
        BluetoothState.Unsupported
    }

    private fun startRssiUpdates() {
        rssiHandler.removeCallbacks(rssiRefresh)
        rssiHandler.post(rssiRefresh)
    }

    private fun stopRssiUpdates() {
        rssiHandler.removeCallbacks(rssiRefresh)
    }

    private fun stopScanning() {
        try {
            scanner?.stopScan(scanCallback)
        } catch (error: SecurityException) {
            _lastError.value = "Bluetooth permission is required"
        }
    }

    private fun closeGatt() {
        stopRssiUpdates()
        ready = false
        servicesDiscovering = false
        writeInProgress = false
        writeQueue.clear()
        commandCharacteristic = null
        eventCharacteristic = null
        decoder = BleFrameDecoder()
        currentGatt?.disconnect()
        currentGatt?.close()
        currentGatt = null
    }

    private fun fail(message: String) {
        _lastError.value = message
    }

    override fun close() {
        stopScanning()
        closeGatt()
        try {
            appContext.unregisterReceiver(bondReceiver)
        } catch (_: IllegalArgumentException) {
            // Already unregistered during process teardown.
        }
    }
}



