package nl.rvt.gatas.companion.background

import nl.rvt.gatas.appContext
import nl.rvantwisk.gatas.lib.models.WifiMode
import nl.rvt.gatas.companion.GaTasDevice
import nl.rvt.gatas.companion.services.DeviceActionCommand

actual object BridgeBackgroundComponent {
    actual val status = GatasBridgeForegroundService.status
    actual val activeDevice = GatasBridgeForegroundService.activeDevice

    actual fun start(device: GaTasDevice) {
        GatasBridgeForegroundService.start(appContext, device)
    }

    actual fun stop() {
        GatasBridgeForegroundService.stop(appContext)
    }

    actual fun requestAircraftChange(icaoAddress: Long) {
        GatasBridgeForegroundService.requestAircraftChange(appContext, icaoAddress)
    }

    actual fun requestWifiModeChange(mode: WifiMode) {
        GatasBridgeForegroundService.requestWifiModeChange(appContext, mode)
    }

    actual fun requestDeviceAction(command: DeviceActionCommand): Boolean = GatasBridgeForegroundService.requestDeviceAction(command)
}
