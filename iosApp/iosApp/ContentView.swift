import UIKit
import SwiftUI
import ComposeApp // the Kotlin framework name

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea(.keyboard) // Compose has own keyboard handler
            .onContinueUserActivity(CompanionHandoff.showLiveActivity) { _ in
                // Handoff brings the person here through the system UI. Read
                // the live bridge state; never restart a bridge from old data.
                GatasLiveActivityManager.shared.synchronize()
            }
    }
}
