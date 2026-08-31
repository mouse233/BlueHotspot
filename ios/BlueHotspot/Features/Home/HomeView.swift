import CoreBluetooth
import SwiftUI

struct HomeView: View {
    @StateObject private var bluetooth = BluetoothCentral()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    deviceListCard
                    statusCard
                    actionCard

                    if let error = bluetooth.lastError {
                        Text(error)
                            .foregroundStyle(.red)
                            .font(.footnote)
                            .padding(.horizontal, 4)
                    }
                }
                .padding()
            }
            .navigationTitle("BlueHotspot")
            .onAppear { bluetooth.startScanning() }
        }
    }

    private var deviceListCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("Android devices")
                    .font(.headline)
                Spacer()
                if bluetooth.discoveredDevices.isEmpty == false {
                    Text("\(bluetooth.discoveredDevices.count)")
                        .foregroundStyle(.secondary)
                        .font(.subheadline)
                }
            }

            if bluetooth.discoveredDevices.isEmpty {
                Label("No Android devices found", systemImage: "antenna.radiowaves.left.and.right")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
            } else {
                ForEach(bluetooth.discoveredDevices) { device in
                    Button {
                        bluetooth.connect(to: device)
                    } label: {
                        HStack(spacing: 12) {
                            Image(systemName: "antenna.radiowaves.left.and.right")
                                .font(.title3)
                                .foregroundStyle(.tint)
                                .frame(width: 28)

                            VStack(alignment: .leading, spacing: 3) {
                                Text(device.name)
                                    .font(.body.weight(.medium))
                                    .foregroundStyle(.primary)
                                Text(device.rssi == 0 ? "Signal unavailable" : "\(device.rssi) dBm")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            Spacer()

                            if bluetooth.connectingDeviceID == device.id {
                                ProgressView()
                                    .controlSize(.small)
                            } else if bluetooth.isConnected && bluetooth.deviceName == device.name {
                                Text("Connected")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.green)
                            } else {
                                Image(systemName: "chevron.right")
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .padding(.vertical, 6)
                }
            }

            glassButton("Scan again", systemImage: "arrow.clockwise") {
                bluetooth.startScanning()
            }

            Toggle(isOn: $bluetooth.autoConnectEnabled) {
                Label("Automatic connection", systemImage: "bolt.horizontal.circle")
            }
            .tint(.green)

            Text("Reconnect to the last selected Android device when it is available.")
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .glassCard()
    }

    private var statusCard: some View {
        VStack(alignment: .leading, spacing: 16) {
            Label(
                bluetooth.isConnected ? (bluetooth.deviceName ?? "Android device") : "No Android device",
                systemImage: bluetooth.isConnected ? "checkmark.circle.fill" : "dot.radiowaves.left.and.right",
            )
            .font(.title3.weight(.semibold))
            .foregroundStyle(bluetooth.isConnected ? .green : .primary)

            Divider()

            statusRow("Bluetooth", value: bluetooth.state.label)
            statusRow("Hotspot", value: bluetooth.hotspotState)
        }
        .glassCard()
    }

    private var actionCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Controls")
                .font(.headline)

            if bluetooth.isConnected {
                glassButton("Start hotspot", systemImage: "power", prominent: true) {
                    bluetooth.startHotspot()
                }
                .disabled(bluetooth.hotspotState == "STARTING" || bluetooth.hotspotState == "ACTIVE")

                glassButton("Stop hotspot", systemImage: "stop.fill") {
                    bluetooth.stopHotspot()
                }
                .disabled(bluetooth.hotspotState != "ACTIVE")

                Button("Disconnect", systemImage: "xmark.circle") {
                    bluetooth.disconnect()
                }
                .buttonStyle(.borderless)
                .frame(maxWidth: .infinity)
            } else {
                glassButton(
                    "Scan for Android device",
                    systemImage: "dot.radiowaves.left.and.right",
                    prominent: true,
                ) {
                    bluetooth.startScanning()
                }
                .disabled(bluetooth.state != .poweredOn)
            }
        }
        .glassCard()
    }

    private func statusRow(_ title: String, value: String) -> some View {
        LabeledContent {
            Text(value)
                .foregroundStyle(.secondary)
        } label: {
            Text(title)
        }
    }

    @ViewBuilder
    private func glassButton(
        _ title: String,
        systemImage: String,
        prominent: Bool = false,
        action: @escaping () -> Void,
    ) -> some View {
        if #available(iOS 26.0, *) {
            if prominent {
                Button(title, systemImage: systemImage, action: action)
                    .buttonStyle(.glassProminent)
                    .frame(maxWidth: .infinity)
            } else {
                Button(title, systemImage: systemImage, action: action)
                    .buttonStyle(.glass)
                    .frame(maxWidth: .infinity)
            }
        } else {
            if prominent {
                Button(title, systemImage: systemImage, action: action)
                    .buttonStyle(.borderedProminent)
                    .frame(maxWidth: .infinity)
            } else {
                Button(title, systemImage: systemImage, action: action)
                    .buttonStyle(.bordered)
                    .frame(maxWidth: .infinity)
            }
        }
    }
}

private struct GlassCardModifier: ViewModifier {
    func body(content: Content) -> some View {
        if #available(iOS 26.0, *) {
            content
                .padding(20)
                .glassEffect(.regular, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
        } else {
            content
                .padding(20)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 26, style: .continuous))
        }
    }
}

private extension View {
    func glassCard() -> some View {
        modifier(GlassCardModifier())
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
