import CoreBluetooth
import SwiftUI

struct HomeView: View {
    @StateObject private var bluetooth = BluetoothCentral()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    deviceSection
                    connectionSection
                    controlsSection

                    if let error = bluetooth.lastError {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .font(.footnote)
                            .foregroundStyle(.red)
                            .padding(.horizontal, 4)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color(uiColor: .systemGroupedBackground).ignoresSafeArea())
            .navigationTitle("BlueHotspot")
            .navigationBarTitleDisplayMode(.large)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Toggle(isOn: $bluetooth.autoConnectEnabled) {
                            Label("Automatic connection", systemImage: "bolt.horizontal.circle")
                        }
                    } label: {
                        Image(systemName: "ellipsis.circle")
                    }
                    .accessibilityLabel("Menu")
                }
            }
            .onAppear { bluetooth.startScanning() }
        }
    }


    private var deviceSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionHeader(
                "Android devices",
                systemImage: "antenna.radiowaves.left.and.right",
                count: bluetooth.discoveredDevices.count,
            )

            VStack(spacing: 0) {
                if bluetooth.discoveredDevices.isEmpty {
                    Label("No Android devices found", systemImage: "magnifyingglass")
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(16)
                } else {
                    ForEach(bluetooth.discoveredDevices) { device in
                        deviceRow(device)
                        if device.id != bluetooth.discoveredDevices.last?.id {
                            rowDivider
                        }
                    }
                }

                rowDivider

                Button {
                    bluetooth.startScanning()
                } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "arrow.clockwise")
                            .frame(width: 28)
                        Text("Scan again")
                        Spacer()
                        Image(systemName: "chevron.right")
                            .font(.footnote.weight(.semibold))
                            .foregroundStyle(.tertiary)
                    }
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .foregroundStyle(.tint)
                .padding(16)
            }
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        }
    }

    private func deviceRow(_ device: DiscoveredBluetoothDevice) -> some View {
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
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                if bluetooth.connectingDeviceID == device.id {
                    ProgressView()
                        .controlSize(.small)
                } else if bluetooth.isConnected && bluetooth.deviceName == device.name {
                    Text("Connected")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.green)
                } else {
                    Image(systemName: "chevron.right")
                        .font(.footnote.weight(.semibold))
                        .foregroundStyle(.tertiary)
                }
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(16)
    }

    private var connectionSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionHeader("Connection", systemImage: "link")

            VStack(spacing: 0) {
                HStack(spacing: 12) {
                    Image(systemName: bluetooth.isConnected ? "checkmark.circle.fill" : "circle.dashed")
                        .font(.title3)
                        .foregroundStyle(bluetooth.isConnected ? .green : .secondary)
                        .frame(width: 28)

                    Text(bluetooth.isConnected ? (bluetooth.deviceName ?? "Android device") : "No Android device")
                        .font(.body.weight(.medium))

                    Spacer()
                }
                .padding(16)

                rowDivider
                statusRow("Bluetooth", value: bluetooth.state.label)
                rowDivider
                statusRow("Hotspot", value: bluetooth.hotspotState)
            }
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        }
    }

    private var controlsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            sectionHeader("Controls", systemImage: "slider.horizontal.3")

            VStack(spacing: 0) {
                if bluetooth.isConnected {
                    Button {
                        bluetooth.startHotspot()
                    } label: {
                        controlRow("Start hotspot", systemImage: "power")
                    }
                    .disabled(bluetooth.hotspotState == "STARTING" || bluetooth.hotspotState == "ACTIVE")

                    rowDivider

                    Button {
                        bluetooth.stopHotspot()
                    } label: {
                        controlRow("Stop hotspot", systemImage: "stop.fill")
                    }
                    .disabled(bluetooth.hotspotState != "ACTIVE")

                    rowDivider

                    Button(role: .destructive) {
                        bluetooth.disconnect()
                    } label: {
                        controlRow("Disconnect", systemImage: "xmark.circle", destructive: true)
                    }
                } else {
                    Button {
                        bluetooth.startScanning()
                    } label: {
                        controlRow("Scan for Android device", systemImage: "magnifyingglass")
                    }
                    .disabled(bluetooth.state != .poweredOn)
                }
            }
            .buttonStyle(.plain)
            .tint(.blue)
            .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 24, style: .continuous))
            .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        }
    }

    private func sectionHeader(_ title: String, systemImage: String, count: Int? = nil) -> some View {
        HStack(spacing: 8) {
            Label(title, systemImage: systemImage)
                .font(.headline)
                .foregroundStyle(.secondary)

            Spacer()

            if let count {
                Text("\(count)")
                    .font(.subheadline)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(.horizontal, 16)
    }

    private func statusRow(_ title: String, value: String) -> some View {
        HStack {
            Text(title)
            Spacer()
            Text(value)
                .foregroundStyle(.secondary)
        }
        .padding(16)
    }

    private func controlRow(_ title: String, systemImage: String, destructive: Bool = false) -> some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage)
                .frame(width: 28)
            Text(title)
            Spacer()
            Image(systemName: "chevron.right")
                .font(.footnote.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .foregroundStyle(destructive ? Color.red : Color.accentColor)
        .contentShape(Rectangle())
        .padding(16)
    }

    private var rowDivider: some View {
        Divider()
            .padding(.leading, 56)
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