import Foundation
import WatchConnectivity
import ComposeApp
import os

/// Owns the phone side for the whole process, including launches by watchOS.
/// Never attach this delegate to a SwiftUI/Compose screen: background requests
/// can arrive before that screen exists or after it has disappeared.
@MainActor
final class PhoneWatchSession: NSObject, WCSessionDelegate {
    static let shared = PhoneWatchSession()
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "GATAS", category: "Watch")
    private var subscription: WatchSubscription?
    private var started = false

    func start() {
        guard !started else { return }
        started = true
        WatchRemoteBridge.shared.initialize()
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
        // Kotlin coalesces its high-frequency packet counters to at most 1 Hz.
        subscription = WatchRemoteBridge.shared.observe { [weak self] encoded in
            Task { @MainActor in self?.publish(encoded) }
        }
    }

    private func publish(_ encoded: String) {
        let session = WCSession.default
        guard session.activationState == .activated, session.isPaired, session.isWatchAppInstalled else { return }
        // Context replaces its predecessor; no backlog of packet statistics is
        // created while the Watch is asleep or disconnected.
        let encoded = nativeSnapshot(encoded)
        do { try session.updateApplicationContext(["snapshot": encoded]) }
        catch { logger.error("Watch context update failed: \(error.localizedDescription, privacy: .public)") }
        if session.isReachable {
            session.sendMessage(["snapshot": encoded], replyHandler: nil, errorHandler: { _ in
                // Going out of range is expected. The next refresh/context wins.
            })
        }
    }

    private func nativeSnapshot(_ encoded: String) -> String {
        // The Kotlin snapshot describes the bridge. Only this native process
        // can know whether ActivityKit actually created its status surface.
        do {
            var snapshot = try WatchSnapshot.decode(encoded)
            snapshot.liveActivity = GatasLiveActivityManager.shared.presentationStatus.rawValue
            return String(decoding: try JSONEncoder().encode(snapshot), as: UTF8.self)
        } catch {
            logger.error("Unable to append Live Activity status: \(error.localizedDescription, privacy: .public)")
            return encoded
        }
    }

    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any],
                             replyHandler: @escaping ([String: Any]) -> Void) {
        Task { @MainActor in
            guard message["version"] as? Int == 1 else {
                replyHandler(["error": "Unsupported protocol version"])
                return
            }
            var response: [String: Any] = [:]
            if let encoded = message["command"] as? String {
                guard encoded.utf8.count <= 16_384 else {
                    replyHandler(["error": "Command is too large"])
                    return
                }
                response["reply"] = WatchRemoteBridge.shared.handleCommand(encoded: encoded)
                GatasLiveActivityManager.shared.synchronize()
            }
            response["snapshot"] = self.nativeSnapshot(WatchRemoteBridge.shared.snapshot())
            replyHandler(response)
        }
    }

    nonisolated func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState,
                             error: Error?) {
        Task { @MainActor in
            if activationState == .activated { self.publish(WatchRemoteBridge.shared.snapshot()) }
        }
    }
    nonisolated func sessionReachabilityDidChange(_ session: WCSession) {
        Task { @MainActor in if session.isReachable { self.publish(WatchRemoteBridge.shared.snapshot()) } }
    }
    nonisolated func sessionWatchStateDidChange(_ session: WCSession) {
        Task { @MainActor in self.publish(WatchRemoteBridge.shared.snapshot()) }
    }
    nonisolated func sessionDidBecomeInactive(_ session: WCSession) {}
    nonisolated func sessionDidDeactivate(_ session: WCSession) { session.activate() }
}
