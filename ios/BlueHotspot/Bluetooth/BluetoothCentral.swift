import CoreBluetooth
import Combine
import Foundation

final class BluetoothCentral: NSObject, ObservableObject {
    @Published private(set) var state: CBManagerState = .unknown

    private lazy var manager = CBCentralManager(delegate: self, queue: .main)

    func startScanning() {
        guard manager.state == .poweredOn else { return }
        manager.scanForPeripherals(withServices: [], options: nil)
    }
}

extension BluetoothCentral: CBCentralManagerDelegate {
    func centralManagerDidUpdateState(_ central: CBCentralManager) {
        state = central.state
    }
}
