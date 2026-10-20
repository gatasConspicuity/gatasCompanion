package nl.rvt.gatas

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.window.ComposeUIViewController
import com.juul.kable.CentralManager
import kotlinx.coroutines.CompletableDeferred
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

fun MainViewController(onContentReady: () -> Unit): UIViewController {
    initializeCompanionRuntime()

    return ComposeUIViewController {
        val firstDraw = remember { CompletableDeferred<Unit>() }
        val permissionsController = remember { PermissionsController() }
        LaunchedEffect(firstDraw) {
            firstDraw.await()
            // Give the first drawn frame a chance to reach the native surface
            // before removing the loading view that covers it.
            withFrameNanos { }
            onContentReady()
        }
        Box(Modifier.fillMaxSize().drawWithContent {
            drawContent()
            firstDraw.complete(Unit)
        }) {
            App(permissionsController = permissionsController)
        }
    }
}
