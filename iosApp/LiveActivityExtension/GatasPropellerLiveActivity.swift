import ActivityKit
import WidgetKit
import SwiftUI

struct GatasPropellerLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: GatasPropellerLiveActivityAttributes.self) { context in
            GatasLiveActivityLockScreenView(context: context)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    GatasPropellerIcon(
                        rotationDegrees: context.state.rotationDegrees,
                        active: context.state.isRunning,
                        size: 36
                    )
                }
                DynamicIslandExpandedRegion(.center) {
                    VStack(spacing: 2) {
                        Text(context.attributes.title)
                            .font(.caption2)
                            .fontWeight(.semibold)
                        Text(context.state.isRunning ? "Packet flow active" : "Idle")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Text(context.state.isRunning ? "RX" : "--")
                        .font(.caption)
                        .fontWeight(.bold)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    HStack(spacing: 10) {
                        GatasPropellerIcon(
                            rotationDegrees: context.state.rotationDegrees,
                            active: context.state.isRunning,
                            size: 24
                        )
                        Text(context.state.isRunning ? "Rotates on packet bursts" : "Waiting for traffic")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            } compactLeading: {
                GatasPropellerIcon(
                    rotationDegrees: context.state.rotationDegrees,
                    active: context.state.isRunning,
                    size: 18
                )
            } compactTrailing: {
                Text(context.state.isRunning ? "RX" : "--")
                    .font(.caption2)
                    .fontWeight(.semibold)
            } minimal: {
                GatasPropellerIcon(
                    rotationDegrees: context.state.rotationDegrees,
                    active: context.state.isRunning,
                    size: 14
                )
            }
        }
    }
}

private struct GatasLiveActivityLockScreenView: View {
    let context: ActivityViewContext<GatasPropellerLiveActivityAttributes>

    var body: some View {
        HStack(spacing: 12) {
            GatasPropellerIcon(
                rotationDegrees: context.state.rotationDegrees,
                active: context.state.isRunning,
                size: 28
            )
            VStack(alignment: .leading, spacing: 2) {
                Text(context.attributes.title)
                    .font(.headline)
                Text(context.state.isRunning ? "Listening for packets" : "Stopped")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }
}

@main
struct GatasLiveActivityBundle: WidgetBundle {
    var body: some Widget {
        GatasPropellerLiveActivityWidget()
    }
}

#if DEBUG
@available(iOS 17.0, *)
private let previewAttributes = GatasPropellerLiveActivityAttributes(title: "GATAS Bridge")

@available(iOS 17.0, *)
private let runningPreviewState = GatasPropellerLiveActivityAttributes.ContentState(
    rotationDegrees: 120,
    isRunning: true
)

@available(iOS 17.0, *)
private let idlePreviewState = GatasPropellerLiveActivityAttributes.ContentState(
    rotationDegrees: 0,
    isRunning: false
)

@available(iOS 17.0, *)
#Preview("Lock Screen", as: .content, using: previewAttributes) {
    GatasPropellerLiveActivityWidget()
} contentStates: {
    runningPreviewState
    idlePreviewState
}

@available(iOS 17.0, *)
#Preview("Dynamic Island Expanded", as: .dynamicIsland(.expanded), using: previewAttributes) {
    GatasPropellerLiveActivityWidget()
} contentStates: {
    runningPreviewState
    idlePreviewState
}

@available(iOS 17.0, *)
#Preview("Dynamic Island Compact", as: .dynamicIsland(.compact), using: previewAttributes) {
    GatasPropellerLiveActivityWidget()
} contentStates: {
    runningPreviewState
    idlePreviewState
}

@available(iOS 17.0, *)
#Preview("Dynamic Island Minimal", as: .dynamicIsland(.minimal), using: previewAttributes) {
    GatasPropellerLiveActivityWidget()
} contentStates: {
    runningPreviewState
    idlePreviewState
}
#endif
