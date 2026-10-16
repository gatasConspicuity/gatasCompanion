package nl.rvt.gatas

import androidx.compose.ui.window.ComposeUIViewController
import com.juul.kable.CentralManager
import nl.rvt.gatas.companion.App
import dev.icerock.moko.permissions.ios.PermissionsController
import platform.UIKit.UIViewController

// Kable implements CoreBluetooth's restoration delegate callback. Enabling
// restoration before the shared central manager is first accessed supplies its
// stable restore identifier and prevents CoreBluetooth's API-misuse warning.
// Lazy initialization keeps this one-time global configuration idempotent if
// SwiftUI happens to recreate the controller wrapper.
private val centralManagerConfiguration: Unit by lazy {
    CentralManager.configure {
        stateRestoration = true
    }
}

fun initializeCompanionRuntime() {
    initializeLogging()
    centralManagerConfiguration
}

fun MainViewController(): UIViewController {
    initializeCompanionRuntime()

    return ComposeUIViewController {
        val permissionsController = PermissionsController()
        App(permissionsController = permissionsController)
    }
}
