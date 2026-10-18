package nl.rvt.gatas.companion.background

import nl.rvantwisk.gatas.lib.models.WifiMode
import nl.rvt.gatas.companion.GaTasDevice

actual object BridgeBackgroundComponent {
    private val controller = BridgeBackgroundController()

    actual val status = controller.status
    actual val activeDevice = controller.activeDevice

    actual fun start(device: GaTasDevice) {
        controller.start(device)
    }

    actual fun stop() {
        controller.stop()
    }

    actual fun requestAircraftChange(icaoAddress: Long) {
        controller.requestAircraftChange(icaoAddress)
    }

    actual fun requestWifiModeChange(mode: WifiMode) {
        controller.requestWifiModeChange(mode)
    }
}
