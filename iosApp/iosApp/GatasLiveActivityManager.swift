import ActivityKit
import Foundation
import UIKit
import ComposeApp
import os

/// Process-owned synchronizer, independent of the iPhone's visible views.
///
/// A Live Activity is a status surface only. It does not grant background
/// execution time and must never be used as a substitute for iOS Bluetooth
/// restoration or ordinary application lifecycle handling.
@MainActor
final class GatasLiveActivityManager: NSObject {
    static let shared = GatasLiveActivityManager()
    private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "GATAS", category: "LiveActivity")
    private var activity: Activity<GatasPropellerLiveActivityAttributes>?
    private var loopTask: Task<Void, Never>?
    private var syncTask: Task<Void, Never>?
    private var syncRequested = false
    private var startFailed = false
    private var started = false
    private let notifications = GatasBridgeNotifications()

    private override init() { super.init() }

    private var hasActivity: Bool {
        guard let activity else { return false }
        // A stale activity is still visible and can become current again after
        // an update. Creating a replacement here would produce duplicates.
        return activity.activityState == .active || activity.activityState == .stale
    }

    private var nextAction: LiveActivitySyncAction {
        LiveActivitySyncPolicy.action(
            bridgeRunning: GatasLiveActivityBridge.shared.isBridgeRunning(),
            hasActivity: hasActivity,
            isForeground: UIApplication.shared.applicationState == .active,
            activitiesEnabled: ActivityAuthorizationInfo().areActivitiesEnabled,
            startFailed: startFailed
        )
    }

    var presentationStatus: WatchLiveActivityStatus {
        guard UIDevice.current.userInterfaceIdiom == .phone else { return .unavailable }
        switch nextAction {
        case .end: return .inactive
        case .start: return .starting
        case .update: return .active
        case .waitForForeground: return .waitingForForeground
        case .disabled: return .disabled
        case .failed: return .failed
        }
    }

    func start() {
        guard !started else { return }
        started = true
        GatasLiveActivityBridge.shared.observeState { [weak self] in
            Task { @MainActor in
                self?.notifications.synchronize()
                self?.synchronize()
            }
        }
        NotificationCenter.default.addObserver(
            self, selector: #selector(applicationDidBecomeActive),
            name: UIApplication.didBecomeActiveNotification, object: nil
        )
        guard UIDevice.current.userInterfaceIdiom == .phone else { return }
        // Background launches must recover an existing activity before a Watch
        // command arrives, even if SwiftUI never creates the phone's root view.
        let existingActivities = Activity<GatasPropellerLiveActivityAttributes>.activities
        activity = existingActivities.first { $0.activityState == .active || $0.activityState == .stale }
        endDuplicateActivities(existingActivities.filter { $0.id != activity?.id })
        synchronize()
        loopTask = Task { [weak self] in
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(1))
                guard !Task.isCancelled else { break }
                self?.synchronize()
            }
        }
    }

    @objc private func applicationDidBecomeActive() {
        // A normal WatchConnectivity wakeup cannot start a new Live Activity.
        // Catch up immediately when the person brings the app to the foreground,
        // and permit one new attempt if an earlier foreground request failed.
        startFailed = false
        notifications.synchronize()
        synchronize()
    }

    func synchronize() {
        guard UIDevice.current.userInterfaceIdiom == .phone else { return }
        syncRequested = true
        guard syncTask == nil else { return }
        // ActivityKit update/end calls suspend. Serialize all callers so a Watch
        // stop during an update cannot race a foreground request or create a
        // second activity. The next iteration always reads current bridge state.
        syncTask = Task { [weak self] in
            guard let self else { return }
            defer { self.syncTask = nil }
            while self.syncRequested {
                self.syncRequested = false
                await self.syncActivity()
            }
        }
    }

    private func syncActivity() async {
        let bridge = GatasLiveActivityBridge.shared
        switch nextAction {
        case .end:
            startFailed = false
            await endActivity()
        case .start:
            await startActivity(rotationDegrees: Int(bridge.rotationDegrees()))
        case .update:
            if bridge.consumeRotationTick() {
                await updateActivity(rotationDegrees: Int(bridge.rotationDegrees()))
            }
        case .disabled:
            startFailed = false
        case .waitForForeground, .failed:
            // Do not request every second and repeatedly log authorization
            // errors. iOS grants no visibility privilege for a WCSession message.
            break
        }
    }

    private func startActivity(rotationDegrees: Int) async {
        let contentState = GatasPropellerLiveActivityAttributes.ContentState(
            rotationDegrees: rotationDegrees,
            isRunning: true
        )
        let content = ActivityContent(state: contentState, staleDate: nil)

        do {
            activity = try Activity.request(
                attributes: GatasPropellerLiveActivityAttributes(title: "GATAS Bridge"),
                content: content,
                pushType: nil
            )
        } catch {
            activity = nil
            startFailed = true
            logger.error("Unable to start Live Activity: \(error.localizedDescription, privacy: .public)")
        }
    }

    private func updateActivity(rotationDegrees: Int) async {
        guard let activity, hasActivity else {
            logger.notice("Skipped Live Activity update because the activity is no longer active")
            self.activity = nil
            return
        }

        let contentState = GatasPropellerLiveActivityAttributes.ContentState(
            rotationDegrees: rotationDegrees,
            isRunning: true
        )
        let content = ActivityContent(state: contentState, staleDate: nil)

        await activity.update(content)
    }

    private func endActivity() async {
        guard let activity else { return }
        let contentState = GatasPropellerLiveActivityAttributes.ContentState(
            rotationDegrees: Int(GatasLiveActivityBridge.shared.rotationDegrees()),
            isRunning: false
        )
        let content = ActivityContent(state: contentState, staleDate: nil)

        await activity.end(content, dismissalPolicy: .immediate)
        self.activity = nil
    }

    private func endDuplicateActivities(
        _ duplicates: [Activity<GatasPropellerLiveActivityAttributes>]
    ) {
        guard !duplicates.isEmpty else { return }

        Task { [logger] in
            for duplicate in duplicates {
                let state = GatasPropellerLiveActivityAttributes.ContentState(
                    rotationDegrees: 0,
                    isRunning: false
                )
                await duplicate.end(
                    ActivityContent(state: state, staleDate: nil),
                    dismissalPolicy: .immediate
                )
                logger.notice("Ended a duplicate or stale Live Activity")
            }
        }
    }
}
