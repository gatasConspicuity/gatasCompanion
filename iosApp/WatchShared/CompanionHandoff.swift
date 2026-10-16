import Foundation

/// A shared route identifier, also registered in the iPhone's NSUserActivityTypes.
/// It identifies a presentation request and deliberately carries no device IDs
/// or bridge commands: continuing later must always use the phone's current state.
enum CompanionHandoff {
    static let showLiveActivity = "nl.rvt.gatas.companion.show-live-activity"

    static func configure(_ activity: NSUserActivity, requestedAt: Date) {
        activity.title = "GATAS Live Activity"
        activity.isEligibleForHandoff = true
        activity.isEligibleForSearch = false
        activity.isEligibleForPublicIndexing = false
        #if os(iOS) || os(watchOS)
        activity.isEligibleForPrediction = false
        #endif
        // A fresh tap offers a short continuation window. An old request must
        // not remain discoverable after the person has moved on to another task.
        activity.expirationDate = requestedAt.addingTimeInterval(120)
    }
}
