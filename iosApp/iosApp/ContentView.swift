import UIKit
import SwiftUI
import ComposeApp // the Kotlin framework name

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        StartupViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

/// Reuse the system launch layout until Compose has drawn its first content.
/// The indicator is native, so no Compose/Metal rendering is needed to show it.
private final class StartupViewController: UIViewController {
    private var loadingController: UIViewController?
    private var composeController: UIViewController?
    private var started = false

    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }
    override var childForStatusBarStyle: UIViewController? {
        loadingController == nil ? composeController : nil
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(named: "LaunchBackground")

        guard let loading = UIStoryboard(name: "LaunchScreen", bundle: nil).instantiateInitialViewController() else {
            preconditionFailure("LaunchScreen must contain an initial view controller")
        }
        addChild(loading)
        loading.view.frame = view.bounds
        loading.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(loading.view)
        loading.didMove(toParent: self)
        loadingController = loading

        let indicator = UIActivityIndicatorView(style: .medium)
        indicator.color = .white
        indicator.translatesAutoresizingMaskIntoConstraints = false
        indicator.isAccessibilityElement = false
        loading.view.addSubview(indicator)
        NSLayoutConstraint.activate([
            indicator.centerXAnchor.constraint(equalTo: loading.view.centerXAnchor),
            indicator.centerYAnchor.constraint(equalTo: loading.view.centerYAnchor, constant: 80),
        ])
        indicator.startAnimating()
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !started else { return }
        started = true
        // Let UIKit present the native loading view before initializing Compose.
        DispatchQueue.main.async { [weak self] in self?.startCompose() }
    }

    private func startCompose() {
        let compose = MainViewControllerKt.MainViewController(onContentReady: { [weak self] in
            DispatchQueue.main.async { self?.showContent() }
        })
        addChild(compose)
        compose.view.frame = view.bounds
        compose.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.insertSubview(compose.view, at: 0)
        compose.didMove(toParent: self)
        composeController = compose
    }

    private func showContent() {
        guard let loading = loadingController else { return }
        loading.willMove(toParent: nil)
        loading.view.removeFromSuperview()
        loading.removeFromParent()
        loadingController = nil
        setNeedsStatusBarAppearanceUpdate()
    }
}

struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea() // Compose applies safe drawing and keyboard insets.
            .onContinueUserActivity(CompanionHandoff.showLiveActivity) { _ in
                // Handoff brings the person here through the system UI. Read
                // the live bridge state; never restart a bridge from old data.
                GatasLiveActivityManager.shared.synchronize()
            }
    }
}
