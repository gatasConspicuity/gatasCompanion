import Foundation

// This file is compiled into both Apple apps and the small macOS protocol test
// package. Keep it independent of WatchConnectivity, SwiftUI and Kotlin.
struct WatchDevice: Codable, Equatable, Identifiable {
    let name: String
    let identifier: String
    var id: String { identifier }
}

struct WatchAircraft: Codable, Equatable, Identifiable {
    let icaoAddress: Int64
    let icaoHexCode: String
    let registration: String
    let aircraftType: String
    var id: Int64 { icaoAddress }
    var displayName: String { registration == "-" ? icaoHexCode : registration }
}

struct WatchSnapshot: Codable, Equatable {
    let version: Int
    let updatedAt: Double
    let devices: [WatchDevice]
    let discoveredDevices: [WatchDevice]
    let scanning: Bool
    let scanError: String?
    let activeDevice: WatchDevice?
    let running: Bool
    let connecting: Bool
    let bleConnected: Bool
    let udpHealthy: Bool
    let gatasId: String?
    let selectedIcaoAddress: Int64?
    let aircraft: [WatchAircraft]
    let aircraftLoading: Bool
    let aircraftError: String?
    let pendingAircraft: Int64?
    let wifiMode: String?
    let pendingWifiMode: String?
    let wifiMessage: String?
    let gdl90Enabled: Bool
    let lastEvent: String
    let lastError: String?
    let diagnostics: [String: String]
    // ActivityKit belongs to the native iPhone process. Its WCSession adapter
    // adds this optional field; older phones/cached snapshots remain readable.
    var liveActivity: String?

    var liveActivityStatus: WatchLiveActivityStatus? {
        liveActivity.flatMap(WatchLiveActivityStatus.init(rawValue:))
    }

    var selectedAircraftName: String {
        aircraft.first { $0.icaoAddress == selectedIcaoAddress }?.displayName
            ?? selectedIcaoAddress.map { String(format: "%06llX", $0) } ?? "—"
    }

    static func decode(_ encoded: String) throws -> WatchSnapshot {
        let state = try JSONDecoder().decode(Self.self, from: Data(encoded.utf8))
        guard state.version == 1 else { throw WatchProtocolError.unsupportedVersion }
        return state
    }
}

enum WatchLiveActivityStatus: String {
    case inactive, active, starting, waitingForForeground, disabled, unavailable, failed

    var canContinueOnPhone: Bool {
        self == .waitingForForeground || self == .failed
    }

    var label: String {
        switch self {
        case .inactive: return "Inactive"
        case .active: return "Active"
        case .starting: return "Starting"
        case .waitingForForeground: return "Open iPhone app"
        case .disabled: return "Disabled on iPhone"
        case .unavailable: return "Unavailable"
        case .failed: return "Could not start"
        }
    }

    var notice: String? {
        switch self {
        case .waitingForForeground:
            return "Open GatasCompanion on your iPhone to show the Live Activity."
        case .disabled:
            return "Live Activities are disabled for GatasCompanion in iPhone Settings."
        case .failed:
            return "The Live Activity could not start. Open the iPhone app to try again."
        default: return nil
        }
    }
}

// Keep the lifecycle decision independent of ActivityKit so background-start,
// foreground recovery and remote-stop behavior can be tested on the host.
enum LiveActivitySyncAction: Equatable {
    case end, start, update, waitForForeground, disabled, failed
}

enum LiveActivitySyncPolicy {
    static func action(bridgeRunning: Bool, hasActivity: Bool, isForeground: Bool,
                       activitiesEnabled: Bool, startFailed: Bool) -> LiveActivitySyncAction {
        guard bridgeRunning else { return .end }
        guard activitiesEnabled else { return .disabled }
        if hasActivity { return .update }
        guard isForeground else { return .waitForForeground }
        return startFailed ? .failed : .start
    }
}

struct WatchCommand: Codable {
    let version: Int
    let id: String
    let action: String
    let expiresAt: Double
    var deviceId: String?
    var expectedDeviceId: String?
    var icaoAddress: Int64?
    var mode: String?
    var enabled: Bool?

    init(action: String, deviceId: String? = nil, expectedDeviceId: String? = nil,
         icaoAddress: Int64? = nil, mode: String? = nil, enabled: Bool? = nil) {
        self.version = 1
        self.id = UUID().uuidString
        self.action = action
        self.expiresAt = Date().timeIntervalSince1970 + 10
        self.deviceId = deviceId
        self.expectedDeviceId = expectedDeviceId
        self.icaoAddress = icaoAddress
        self.mode = mode
        self.enabled = enabled
    }

    func encoded() throws -> String { String(decoding: try JSONEncoder().encode(self), as: UTF8.self) }
}

struct WatchReply: Codable {
    let id: String
    let status: String
    let message: String
}

enum WatchProtocolError: LocalizedError {
    case unsupportedVersion, invalidReply
    var errorDescription: String? {
        switch self {
        case .unsupportedVersion: return "Update both the iPhone and Watch apps to compatible versions."
        case .invalidReply: return "The iPhone returned an invalid response. Refresh before trying again."
        }
    }
}

// Freshness is a UI/transport property, separate from BLE and relay health.
// A cached green Bluetooth indicator must never enable controls by itself.
enum WatchFreshness {
    static func canControl(receivedLiveAt: Date?, now: Date = Date()) -> Bool {
        guard let receivedLiveAt else { return false }
        let age = now.timeIntervalSince(receivedLiveAt)
        return age >= 0 && age < 15
    }
}
