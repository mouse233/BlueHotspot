import CoreBluetooth
import SwiftUI

struct HomeView: View {
    @StateObject private var bluetooth = BluetoothCentral()

    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(
                    colors: [Color.indigo.opacity(0.24), Color.cyan.opacity(0.12), Color.clear],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing,
                )
                .ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        header
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
            }
            .navigationTitle("BlueHotspot")
            .onAppear { bluetooth.startScanning() }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Hotspot control")
                .font(.largeTitle.bold())
            Text("Control your Android hotspot securely over Bluetooth")
                .foregroundStyle(.secondary)
        }
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
