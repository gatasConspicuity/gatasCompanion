import ComposeApp
import UIKit
import UserNotifications
import os

/// Local disconnect alerts work without a Watch or an APNs server. They depend
/// on the BLE callback reaching this process, not on Live Activity permission.
@MainActor
final class GatasBridgeNotifications: NSObject, UNUserNotificationCenterDelegate {
    private static let disconnectIdentifier = "gatas.bridge.disconnected"
    private let center = UNUserNotificationCenter.current()
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "GATAS", category: "BridgeNotifications")
    private var lastAlertedCount: Int64 = 0
    private var pendingIdentifier: String?
    private var syncTask: Task<Void, Never>?
    private var syncRequested = false

    override init() {
        super.init()
        center.delegate = self
        center.removePendingNotificationRequests(withIdentifiers: [Self.disconnectIdentifier])
    }

    func synchronize() {
        let state = GatasLiveActivityBridge.shared.lifecycleState()
        if !state.running || !state.unexpectedDisconnectActive {
            cancelPendingAlert()
        }
        syncRequested = true
        guard syncTask == nil else { return }
        // Serialize settings, permission and scheduling calls. Stop/recovery
        // can arrive during any await; always reconcile the latest lifecycle.
        syncTask = Task {
            defer { syncTask = nil }
            while syncRequested {
                syncRequested = false
                await syncNotifications()
            }
        }
    }

    private func syncNotifications() async {
        var settings = await center.notificationSettings()
        // Ask in the context of a running bridge, never during a locked-phone
        // Watch launch. Returning to the phone app is another opportunity.
        if settings.authorizationStatus == .notDetermined,
           GatasLiveActivityBridge.shared.isBridgeRunning(),
           UIApplication.shared.applicationState == .active {
            do {
                _ = try await center.requestAuthorization(options: [.alert, .sound])
                settings = await center.notificationSettings()
            } catch {
                logger.error("Unable to request notification permission: \(error.localizedDescription, privacy: .public)")
            }
        }

        let state = GatasLiveActivityBridge.shared.lifecycleState()
        guard state.running && state.unexpectedDisconnectActive else {
            cancelPendingAlert()
            return
        }
        guard settings.authorizationStatus == .authorized || settings.authorizationStatus == .provisional,
              state.unexpectedDisconnectCount != lastAlertedCount else { return }
        cancelPendingAlert()
        let count = state.unexpectedDisconnectCount
        let identifier = Self.disconnectIdentifier
        let content = UNMutableNotificationContent()
        content.title = "GATAS connection lost"
        content.body = "The Bluetooth connection to GATAS was interrupted. Traffic forwarding is paused while the bridge tries to reconnect."
        content.sound = .default
        content.threadIdentifier = "gatas.bridge.connection"
        // Avoid an alert for a brief radio interruption. iOS owns this
        // delay even if the app is suspended after the disconnect callback.
        let request = UNNotificationRequest(identifier: identifier, content: content,
            trigger: UNTimeIntervalNotificationTrigger(timeInterval: 3, repeats: false))
        do {
            try await center.add(request)
            lastAlertedCount = count
            pendingIdentifier = identifier
            // Stop/recovery may have run while add() suspended. Remove a late
            // registration before the grace period expires.
            let latest = GatasLiveActivityBridge.shared.lifecycleState()
            if !latest.running || !latest.unexpectedDisconnectActive || latest.unexpectedDisconnectCount != count {
                cancelPendingAlert()
            }
        } catch {
            logger.error("Unable to schedule disconnect alert: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func cancelPendingAlert() {
        guard let pendingIdentifier else { return }
        center.removePendingNotificationRequests(withIdentifiers: [pendingIdentifier])
        self.pendingIdentifier = nil
    }

    nonisolated func userNotificationCenter(_ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }
}
