import SwiftUI

struct HomeView: View {
    var body: some View {
        NavigationStack {
            ContentUnavailableView(
                "No Android device",
                systemImage: "dot.radiowaves.left.and.right",
                description: Text("BLE discovery will be added next.")
            )
            .navigationTitle("BlueHotspot")
        }
    }
}
