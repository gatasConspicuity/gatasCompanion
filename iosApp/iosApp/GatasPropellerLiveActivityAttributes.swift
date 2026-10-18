import ActivityKit
import SwiftUI

struct GatasPropellerLiveActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var rotationDegrees: Int
        var isRunning: Bool
    }

    var title: String
}

struct GatasPropellerIcon: View {
    let rotationDegrees: Int
    let active: Bool
    let size: CGFloat

    init(rotationDegrees: Int, active: Bool, size: CGFloat = 28) {
        self.rotationDegrees = rotationDegrees
        self.active = active
        self.size = size
    }

    var body: some View {
        ZStack {
            ForEach(0..<3, id: \.self) { index in
                Capsule(style: .continuous)
                    .fill(bladeColor)
                    .frame(width: size * 0.16, height: size * 0.42)
                    .offset(y: -size * 0.13)
                    .rotationEffect(.degrees(Double(index * 120)))
            }

            Circle()
                .fill(hubColor)
                .frame(width: size * 0.22, height: size * 0.22)

            Circle()
                .stroke(hubColor.opacity(active ? 0.26 : 0.16), lineWidth: max(1, size * 0.04))
                .frame(width: size * 0.42, height: size * 0.42)
        }
        .frame(width: size, height: size)
        .clipped()
        .rotationEffect(.degrees(Double(rotationDegrees)))
        .animation(.easeOut(duration: 0.25), value: rotationDegrees)
        .accessibilityHidden(true)
    }

    private var bladeColor: Color {
        active ? Color.white : Color.white.opacity(0.72)
    }

    private var hubColor: Color {
        active ? Color.white : Color.white.opacity(0.78)
    }
}
