import SwiftUI

@main
struct GatasWatchApp: App {
    @StateObject private var session = WatchSession()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            WatchDashboard()
                .environmentObject(session)
                .onAppear { session.setForeground(scenePhase == .active) }
                .onChange(of: scenePhase) { _, phase in session.setForeground(phase == .active) }
        }
    }
}
