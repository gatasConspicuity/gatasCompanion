import Foundation
import XCTest
@testable import WatchProtocol

final class CompanionHandoffTests: XCTestCase {
    func testPhoneRegistersTheSameRouteAdvertisedByTheWatch() throws {
        let iosRoot = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent().deletingLastPathComponent().deletingLastPathComponent()
        let data = try Data(contentsOf: iosRoot.appendingPathComponent("iosApp/Info.plist"))
        let info = try XCTUnwrap(PropertyListSerialization.propertyList(from: data, format: nil) as? [String: Any])
        let types = try XCTUnwrap(info["NSUserActivityTypes"] as? [String])
        XCTAssertTrue(types.contains(CompanionHandoff.showLiveActivity))
    }

    func testContinuationIsShortLivedAndContainsNoDeferredDeviceCommand() {
        let requestedAt = Date(timeIntervalSince1970: 1000)
        let activity = NSUserActivity(activityType: CompanionHandoff.showLiveActivity)
        CompanionHandoff.configure(activity, requestedAt: requestedAt)

        XCTAssertTrue(activity.isEligibleForHandoff)
        XCTAssertFalse(activity.isEligibleForSearch)
        XCTAssertFalse(activity.isEligibleForPublicIndexing)
        XCTAssertEqual(activity.expirationDate, requestedAt.addingTimeInterval(120))
        // Foundation may expose an empty dictionary even when no payload was set.
        XCTAssertTrue(activity.userInfo?.isEmpty ?? true)
        XCTAssertNil(activity.webpageURL)
    }
}
