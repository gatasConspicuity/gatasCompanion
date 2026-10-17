package nl.rvt.gatas.companion.background

import kotlinx.coroutines.flow.StateFlow
import nl.rvantwisk.gatas.lib.models.WifiMode
import nl.rvt.gatas.companion.GaTasDevice
import nl.rvt.gatas.companion.services.BridgeStatus
import nl.rvt.gatas.companion.services.DeviceActionCommand

expect object BridgeBackgroundComponent {
    val status: StateFlow<BridgeStatus>
    val activeDevice: StateFlow<GaTasDevice?>

    fun start(device: GaTasDevice)
    fun stop()
    fun requestAircraftChange(icaoAddress: Long)
    fun requestWifiModeChange(mode: WifiMode)
    fun requestDeviceAction(command: DeviceActionCommand): Boolean
}
