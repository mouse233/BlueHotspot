import CoreBluetooth
import Combine
import Foundation

final class BluetoothCentral: NSObject, ObservableObject {
    @Published private(set) var state: CBManagerState = .unknown
    @Published private(set) var isConnected = false
    @Published private(set) var hotspotState = "Unknown"
    @Published private(set) var lastError: String?
    @Published private(set) var deviceName: String?

    private var manager: CBCentralManager!
    private var peripheral: CBPeripheral?
    private var commandCharacteristic: CBCharacteristic?
    private var eventCharacteristic: CBCharacteristic?
    private var decoder = BleFrameDecoder()
    private var writeQueue: [Data] = []
    private var writeInProgress = false
    private var helloSent = false

    override init() {
        super.init()
        manager = CBCentralManager(delegate: self, queue: .main)
    }

    func startScanning() {
        guard manager.state == .poweredOn else {
            lastError = "Bluetooth is unavailable"
            return
        }
        manager.scanForPeripherals(withServices: [BleUuids.service], options: [CBCentralManagerScanOptionAllowDuplicatesKey: false])
    }

    func disconnect() {
        if let peripheral { manager.cancelPeripheralConnection(peripheral) }
    }

    func startHotspot() {
        send(BleFrame(type: .startHotspot, requestId: UUID(), payload: ""))
    }

    func stopHotspot() {
        send(BleFrame(type: .stopHotspot, requestId: UUID(), payload: ""))
    }

    private func send(_ frame: BleFrame) {
        guard commandCharacteristic != nil else {
            lastError = "Not connected to an Android device"
            return
        }
        do {
            let encoded = try BleFrameCodec.encode(frame)
            var offset = 0
            while offset < encoded.count {
                let end = min(offset + 180, encoded.count)
                writeQueue.append(encoded.subdata(in: offset..<end))
                offset = end
            }
            pumpWrites()
        } catch {
            lastError = "Unable to encode BLE message"
        }
    }

    private func pumpWrites() {
        guard !writeInProgress, let peripheral, let characteristic = commandCharacteristic, !writeQueue.isEmpty else { return }
        writeInProgress = true
        peripheral.writeValue(writeQueue.removeFirst(), for: characteristic, type: .withResponse)
    }

    private func sendHelloIfReady() {
        guard !helloSent, commandCharacteristic != nil, eventCharacteristic != nil else { return }
        helloSent = true
        send(BleFrame(type: .hello, requestId: UUID(), payload: ""))
        send(BleFrame(type: .getStatus, requestId: UUID(), payload: ""))
    }

    private func apply(_ frame: BleFrame) {
        switch frame.type {
        case .helloAck, .pong:
            break
        case .status:
            hotspotState = frame.payload
        case .hotspotStarting:
            hotspotState = "STARTING"
        case .hotspotReady:
            hotspotState = "ACTIVE"
        case .hotspotStopped:
            hotspotState = "IDLE"
        case .hotspotFailed:
            hotspotState = "FAILED"
            lastError = frame.payload.isEmpty ? "Hotspot operation failed" : frame.payload
        case .error:
            lastError = frame.payload
        default:
            break
        }
    }
}

extension BluetoothCentral: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        state = central.state
        if central.state != .poweredOn {
            isConnected = false
            hotspotState = "Unknown"
        }
    }

    func centralManager(_ central: CBCentralManager, didDiscover peripheral: CBPeripheral, advertisementData: [String: Any], rssi RSSI: NSNumber) {
        manager.stopScan()
        self.peripheral = peripheral
        deviceName = peripheral.name ?? "Android device"
        peripheral.delegate = self
        manager.connect(peripheral, options: nil)
    }

    func centralManager(_ central: CBCentralManager, didConnect peripheral: CBPeripheral) {
        isConnected = true
        lastError = nil
        helloSent = false
        decoder = BleFrameDecoder()
        writeQueue.removeAll()
        writeInProgress = false
        peripheral.discoverServices([BleUuids.service])
    }

    func centralManager(_ central: CBCentralManager, didFailToConnect peripheral: CBPeripheral, error: Error?) {
        isConnected = false
        lastError = error?.localizedDescription ?? "Unable to connect"
    }

    func centralManager(_ central: CBCentralManager, didDisconnectPeripheral peripheral: CBPeripheral, error: Error?) {
        isConnected = false
        commandCharacteristic = nil
        eventCharacteristic = nil
        helloSent = false
        if let error { lastError = error.localizedDescription }
    }
}

extension BluetoothCentral: CBPeripheralDelegate {
    func peripheral(_ peripheral: CBPeripheral, didDiscoverServices error: Error?) {
        if let error { lastError = error.localizedDescription; return }
        guard let service = peripheral.services?.first(where: { $0.uuid == BleUuids.service }) else {
            lastError = "BlueHotspot service not found"
            return
        }
        peripheral.discoverCharacteristics([BleUuids.command, BleUuids.event], for: service)
    }

    func peripheral(_ peripheral: CBPeripheral, didDiscoverCharacteristicsFor service: CBService, error: Error?) {
        if let error { lastError = error.localizedDescription; return }
        for characteristic in service.characteristics ?? [] {
            if characteristic.uuid == BleUuids.command { commandCharacteristic = characteristic }
            if characteristic.uuid == BleUuids.event {
                eventCharacteristic = characteristic
                peripheral.setNotifyValue(true, for: characteristic)
            }
        }
        sendHelloIfReady()
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateNotificationStateFor characteristic: CBCharacteristic, error: Error?) {
        if let error { lastError = error.localizedDescription; return }
        sendHelloIfReady()
    }

    func peripheral(_ peripheral: CBPeripheral, didUpdateValueFor characteristic: CBCharacteristic, error: Error?) {
        if let error { lastError = error.localizedDescription; return }
        guard characteristic.uuid == BleUuids.event, let value = characteristic.value else { return }
        do {
            for frame in try decoder.append(value) { apply(frame) }
        } catch {
            lastError = "Invalid message received from Android"
        }
    }

    func peripheral(_ peripheral: CBPeripheral, didWriteValueFor characteristic: CBCharacteristic, error: Error?) {
        writeInProgress = false
        if let error { lastError = error.localizedDescription }
        pumpWrites()
    }
}
