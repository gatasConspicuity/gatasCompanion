import XCTest
@testable import WatchProtocol

final class WatchProtocolTests: XCTestCase {
    // Represents the Kotlin encoder's v1 snapshot, including explicit nulls.
    private let encoded = """
    {"version":1,"updatedAt":1000,"devices":[{"name":"GATAS","identifier":"a"}],
    "discoveredDevices":[],"scanning":false,"scanError":null,"activeDevice":{"name":"GATAS","identifier":"a"},
    "running":true,"connecting":false,"bleConnected":true,"udpHealthy":false,"gatasId":"00000001",
    "selectedIcaoAddress":123,"aircraft":[{"icaoAddress":123,"icaoHexCode":"00007B","registration":"D-TEST","aircraftType":"C172"}],
    "aircraftLoading":false,"aircraftError":null,"pendingAircraft":null,"wifiMode":"AP","pendingWifiMode":null,
    "wifiMessage":null,"gdl90Enabled":true,"lastEvent":"Connected","lastError":null,"diagnostics":{"Relay queue drops":"0"}}
    """

    func testKotlinSnapshotShapeDecodes() throws {
        let state = try WatchSnapshot.decode(encoded)
        XCTAssertEqual(state.selectedAircraftName, "D-TEST")
        XCTAssertEqual(state.activeDevice?.id, "a")
        XCTAssertNil(state.pendingWifiMode)
        XCTAssertEqual(state.diagnostics["Relay queue drops"], "0")
        XCTAssertNil(state.liveActivityStatus)
    }

    func testNativeLiveActivityStatusRoundTrips() throws {
        var state = try WatchSnapshot.decode(encoded)
        state.liveActivity = WatchLiveActivityStatus.waitingForForeground.rawValue
        let decoded = try WatchSnapshot.decode(String(decoding: JSONEncoder().encode(state), as: UTF8.self))
        XCTAssertEqual(decoded.liveActivityStatus, .waitingForForeground)
        XCTAssertNotNil(decoded.liveActivityStatus?.notice)
        XCTAssertTrue(decoded.running)
        state.liveActivity = WatchLiveActivityStatus.active.rawValue
        XCTAssertNil(state.liveActivityStatus?.notice)
    }

    func testFutureNativeActivityStatusDoesNotBreakBridgeSnapshot() throws {
        let state = try WatchSnapshot.decode(encoded.replacingOccurrences(of: "\"version\":1", with: "\"liveActivity\":\"future-status\",\"version\":1"))
        XCTAssertTrue(state.running)
        XCTAssertNil(state.liveActivityStatus)
    }

    func testWatchStartedBridgeDefersActivityUntilPhoneForeground() {
        XCTAssertEqual(LiveActivitySyncPolicy.action(bridgeRunning: true, hasActivity: false,
            isForeground: false, activitiesEnabled: true, startFailed: false), .waitForForeground)
        XCTAssertEqual(LiveActivitySyncPolicy.action(bridgeRunning: true, hasActivity: false,
            isForeground: true, activitiesEnabled: true, startFailed: false), .start)
    }

    func testExistingActivityUpdatesWhilePhoneBackground() {
        XCTAssertEqual(LiveActivitySyncPolicy.action(bridgeRunning: true, hasActivity: true,
            isForeground: false, activitiesEnabled: true, startFailed: false), .update)
    }

    func testRemoteStopEndsActivityEvenWhenBackgroundOrPermissionRevoked() {
        for enabled in [false, true] {
            XCTAssertEqual(LiveActivitySyncPolicy.action(bridgeRunning: false, hasActivity: true,
                isForeground: false, activitiesEnabled: enabled, startFailed: true), .end)
        }
    }

    func testDisabledActivitiesAreRechecked() {
        XCTAssertEqual(LiveActivitySyncPolicy.action(bridgeRunning: true, hasActivity: false,
            isForeground: true, activitiesEnabled: false, startFailed: false), .disabled)
        XCTAssertEqual(LiveActivitySyncPolicy.action(bridgeRunning: true, hasActivity: false,
            isForeground: true, activitiesEnabled: true, startFailed: false), .start)
    }

    func testFailedStartWaitsUntilForegroundRetryReset() {
        XCTAssertEqual(LiveActivitySyncPolicy.action(bridgeRunning: true, hasActivity: false,
            isForeground: true, activitiesEnabled: true, startFailed: true), .failed)
        XCTAssertEqual(LiveActivitySyncPolicy.action(bridgeRunning: true, hasActivity: false,
            isForeground: false, activitiesEnabled: true, startFailed: true), .waitForForeground)
        XCTAssertEqual(LiveActivitySyncPolicy.action(bridgeRunning: true, hasActivity: false,
            isForeground: true, activitiesEnabled: true, startFailed: false), .start)
    }

    func testMissingMetadataKeepsHexSelectionUsable() throws {
        let state = try WatchSnapshot.decode(encoded.replacingOccurrences(of: "D-TEST", with: "-"))
        XCTAssertEqual(state.selectedAircraftName, "00007B")
    }

    func testUnknownFieldsAreCompatibleButFutureVersionIsRejected() throws {
        XCTAssertNoThrow(try WatchSnapshot.decode(encoded.replacingOccurrences(of: "\"version\":1", with: "\"extra\":true,\"version\":1")))
        XCTAssertThrowsError(try WatchSnapshot.decode(encoded.replacingOccurrences(of: "\"version\":1", with: "\"version\":2")))
    }

    func testCorruptOrPartialSnapshotsDoNotLookLikeIdleDevices() {
        XCTAssertThrowsError(try WatchSnapshot.decode("{}"))
        XCTAssertThrowsError(try WatchSnapshot.decode("not json"))
    }

    func testCachedOrExpiredStateCannotEnableControls() {
        let now = Date(timeIntervalSince1970: 1000)
        XCTAssertFalse(WatchFreshness.canControl(receivedLiveAt: nil, now: now))
        XCTAssertFalse(WatchFreshness.canControl(receivedLiveAt: now.addingTimeInterval(-15), now: now))
        XCTAssertFalse(WatchFreshness.canControl(receivedLiveAt: now.addingTimeInterval(1), now: now))
        XCTAssertTrue(WatchFreshness.canControl(receivedLiveAt: now.addingTimeInterval(-5), now: now))
    }

    func testCommandCarriesExplicitDesiredValueAndDeviceContext() throws {
        let command = WatchCommand(action: "setWifi", expectedDeviceId: "device-a", mode: "CLIENT")
        let json = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(command.encoded().utf8)) as? [String: Any])
        XCTAssertEqual(json["version"] as? Int, 1)
        XCTAssertGreaterThan(try XCTUnwrap(json["expiresAt"] as? Double), Date().timeIntervalSince1970)
        XCTAssertEqual(json["mode"] as? String, "CLIENT")
        XCTAssertEqual(json["expectedDeviceId"] as? String, "device-a")
        XCTAssertNotNil(UUID(uuidString: command.id))
        XCTAssertNotEqual(command.id, WatchCommand(action: "setWifi").id)
    }
}
