import CoreBluetooth
import SwiftUI

struct HomeView: View {
    @StateObject private var bluetooth = BluetoothCentral()

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 20) {
                Label(
                    bluetooth.isConnected ? (bluetooth.deviceName ?? "Android device") : "No Android device",
                    systemImage: bluetooth.isConnected ? "checkmark.circle.fill" : "dot.radiowaves.left.and.right",
                )
                .font(.title2)

                LabeledContent("Bluetooth", value: bluetooth.state.label)
                LabeledContent("Hotspot", value: bluetooth.hotspotState)

                if bluetooth.isConnected {
                    Button("Start hotspot") { bluetooth.startHotspot() }
                        .buttonStyle(.borderedProminent)
                        .disabled(bluetooth.hotspotState == "STARTING" || bluetooth.hotspotState == "ACTIVE")
                    Button("Stop hotspot") { bluetooth.stopHotspot() }
                        .buttonStyle(.bordered)
                        .disabled(bluetooth.hotspotState != "ACTIVE")
                    Button("Disconnect") { bluetooth.disconnect() }
                        .buttonStyle(.borderless)
                } else {
                    Button("Scan for Android device") { bluetooth.startScanning() }
                        .buttonStyle(.borderedProminent)
                        .disabled(bluetooth.state != .poweredOn)
                }

                if let error = bluetooth.lastError {
                    Text(error)
                        .foregroundStyle(.red)
                        .font(.footnote)
                }

                Spacer()
            }
            .padding()
            .navigationTitle("BlueHotspot")
            .onAppear { bluetooth.startScanning() }
        }
    }
}

private extension CBManagerState {
    var label: String {
        switch self {
        case .unknown: return "Unknown"
        case .resetting: return "Resetting"
        case .unsupported: return "Unsupported"
        case .unauthorized: return "Permission required"
        case .poweredOff: return "Powered off"
        case .poweredOn: return "Ready"
        @unknown default: return "Unknown"
        }
    }
}

