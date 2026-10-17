import Foundation
import SwiftUI
import WatchConnectivity
import WatchKit

/// The Watch is a remote control. It never owns a GATAS connection or relays
/// traffic. A displayed snapshot can outlive its transport, so freshness and
/// device health are deliberately tracked separately.
@MainActor
final class WatchSession: NSObject, ObservableObject, WCSessionDelegate {
    @Published private(set) var snapshot: WatchSnapshot?
    @Published private(set) var receivedLiveAt: Date?
    @Published private(set) var sending = false
    @Published private(set) var awaitingConfirmation: WatchCommand?
    @Published private(set) var message: String?
    @Published private(set) var messageIsError = false
    private var foreground = false
    private var refreshTask: Task<Void, Never>?
    private var confirmationTask: Task<Void, Never>?
    private var requestID: UUID?
    private let cacheKey = "watch.lastSnapshot.v1"

    var canControl: Bool {
        WatchFreshness.canControl(receivedLiveAt: receivedLiveAt) && !sending && awaitingConfirmation == nil && snapshot?.deviceControl?.operation?.busy != true
    }
    // A pending aircraft/Wi-Fi confirmation must not prevent stopping a bridge.
    var canStop: Bool { WatchFreshness.canControl(receivedLiveAt: receivedLiveAt) && !sending }

    override init() {
        super.init()
        if let cached = UserDefaults.standard.string(forKey: cacheKey) {
            snapshot = try? WatchSnapshot.decode(cached)
        }
        let session = WCSession.default
        session.delegate = self
        session.activate()
        if !session.receivedApplicationContext.isEmpty {
            apply(session.receivedApplicationContext, live: false)
        }
    }

    func setForeground(_ active: Bool) {
        foreground = active
        refreshTask?.cancel()
        refreshTask = nil
        guard active else {
            receivedLiveAt = nil
            return
        }
        refreshTask = Task { [weak self] in
            while !Task.isCancelled {
                self?.refresh()
                try? await Task.sleep(for: .seconds(5))
            }
        }
    }

    func refresh() {
        guard foreground, WCSession.default.activationState == .activated else { return }
        // Do not gate this on isReachable: an active Watch can wake the phone.
        WCSession.default.sendMessage(["version": 1], replyHandler: { [weak self] response in
            Task { @MainActor in self?.apply(response, live: true) }
        }, errorHandler: { [weak self] error in
            Task { @MainActor in
                self?.receivedLiveAt = nil
                if self?.snapshot == nil { self?.show(error.localizedDescription, error: true) }
            }
        })
    }

    func send(action: String, device: WatchDevice? = nil, aircraft: WatchAircraft? = nil,
              mode: String? = nil, enabled: Bool? = nil, observedState: WatchSnapshot? = nil) {
        guard (action == "stop" ? canStop : canControl), let state = observedState ?? snapshot else {
            show("Refresh the connection to your iPhone before sending a command.", error: true)
            return
        }
        // A dialog can remain open while the phone selects another device.
        // Preserve the state the person actually saw, rather than silently
        // retargeting its confirmation to the newest incoming snapshot.
        let command = WatchCommand(action: action, deviceId: device?.id,
                                   expectedDeviceId: state.activeDevice?.id,
                                   icaoAddress: aircraft?.id, mode: mode, enabled: enabled,
                                   expectedBootId: state.deviceControl?.bootId)
        do {
            let encoded = try command.encoded()
            let token = UUID()
            requestID = token
            sending = true
            show("Sending…")
            WCSession.default.sendMessage(["version": 1, "command": encoded], replyHandler: { [weak self] response in
                Task { @MainActor in
                    guard let self, self.requestID == token else { return }
                    self.requestID = nil
                    self.sending = false
                    do {
                        guard let encodedReply = response["reply"] as? String else { throw WatchProtocolError.invalidReply }
                        let reply = try JSONDecoder().decode(WatchReply.self, from: Data(encodedReply.utf8))
                        guard reply.id == command.id, ["accepted", "completed", "rejected"].contains(reply.status) else {
                            throw WatchProtocolError.invalidReply
                        }
                        self.show(reply.message, error: reply.status == "rejected")
                        if action == "stop" && reply.status == "completed" {
                            self.awaitingConfirmation = nil
                            self.confirmationTask?.cancel()
                        }
                        if reply.status == "accepted" && ["setAircraft", "setWifi", "saveConfiguration", "restartGatas"].contains(action) {
                            self.awaitingConfirmation = command
                            self.waitForConfirmationTimeout()
                        }
                        self.apply(response, live: true)
                        if reply.status == "completed" { self.playHaptic(.success) }
                    } catch { self.show(error.localizedDescription, error: true) }
                }
            }, errorHandler: { [weak self] _ in
                Task { @MainActor in
                    guard let self, self.requestID == token else { return }
                    self.requestID = nil
                    self.sending = false
                    self.receivedLiveAt = nil
                    // Delivery may have succeeded even if its reply was lost.
                    // Never automatically repeat a control command.
                    self.show("Result unknown. Refresh and check the device before trying again.", error: true)
                }
            })
            Task { [weak self] in
                try? await Task.sleep(for: .seconds(12))
                guard let self, self.requestID == token else { return }
                self.requestID = nil
                self.sending = false
                self.receivedLiveAt = nil
                self.show("No reply from iPhone. Result unknown; refresh before trying again.", error: true)
            }
        } catch { show(error.localizedDescription, error: true) }
    }

    private func waitForConfirmationTimeout() {
        confirmationTask?.cancel()
        confirmationTask = Task { [weak self] in
            let timeout = self?.awaitingConfirmation?.action == "restartGatas" ? 75 : 45
            try? await Task.sleep(for: .seconds(timeout))
            guard !Task.isCancelled, let self, self.awaitingConfirmation != nil else { return }
            self.awaitingConfirmation = nil
            self.show("GATAS has not confirmed the change. Refresh to check its current state.", error: true)
        }
    }

    private func apply(_ response: [String: Any], live: Bool) {
        if let error = response["error"] as? String {
            receivedLiveAt = nil
            show(error, error: true)
            return
        }
        guard let encoded = response["snapshot"] as? String else { return }
        do {
            let incoming = try WatchSnapshot.decode(encoded)
            // Opportunistic background context can arrive after a newer reply.
            guard incoming.updatedAt >= (snapshot?.updatedAt ?? 0) else { return }
            snapshot = incoming
            UserDefaults.standard.set(encoded, forKey: cacheKey)
            if live { receivedLiveAt = Date() }
            if let command = awaitingConfirmation {
                if let operation = incoming.deviceControl?.result(for: command.id) {
                    finishConfirmation(operation.message, success: operation.phase == "completed")
                } else if incoming.activeDevice?.id != command.expectedDeviceId {
                    finishConfirmation("The active device changed before confirmation.", success: false)
                } else if incoming.bleConnected &&
                    ((command.action == "setAircraft" && incoming.selectedIcaoAddress == command.icaoAddress) ||
                     (command.action == "setWifi" && incoming.wifiMode == command.mode)) {
                    finishConfirmation("Change confirmed by GATAS.", success: true)
                }
            }
        } catch {
            receivedLiveAt = nil
            show(error.localizedDescription, error: true)
        }
    }

    private func finishConfirmation(_ text: String, success: Bool) {
        awaitingConfirmation = nil
        confirmationTask?.cancel()
        show(text, error: !success)
        if success { playHaptic(.success) }
    }

    private func show(_ text: String, error: Bool = false) {
        // Repeated foreground refresh failures update the status quietly;
        // the same disconnected phone should not vibrate every five seconds.
        let changed = message != text || messageIsError != error
        message = text
        messageIsError = error
        if error && changed { playHaptic(.failure) }
    }

    private func playHaptic(_ type: WKHapticType) {
        // Simulator haptics can become audible Mac alerts even with no visible
        // Simulator window. Keep development runs silent; real Watches vibrate.
        #if !targetEnvironment(simulator)
        WKInterfaceDevice.current().play(type)
        #endif
    }

    nonisolated func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState,
                             error: Error?) {
        Task { @MainActor in
            if let error { self.show(error.localizedDescription, error: true) }
            else { self.refresh() }
        }
    }
    nonisolated func sessionReachabilityDidChange(_ session: WCSession) {
        Task { @MainActor in
            if !session.isReachable { self.receivedLiveAt = nil }
            if self.foreground { self.refresh() }
        }
    }
    nonisolated func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        Task { @MainActor in self.apply(message, live: true) }
    }
    nonisolated func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        Task { @MainActor in self.apply(applicationContext, live: false) }
    }
}
