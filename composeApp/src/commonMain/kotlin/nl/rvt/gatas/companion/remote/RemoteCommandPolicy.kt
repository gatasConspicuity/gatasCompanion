package nl.rvt.gatas.companion.remote

import nl.rvantwisk.gatas.lib.models.WifiMode
import nl.rvt.gatas.companion.CompanionState

/** Validate against the current phone state, never against a Watch's cache.
 * In particular, a command composed for one device must not affect another
 * device selected on the phone while the message was in transit.
 */
object RemoteCommandPolicy {
    fun validate(command: RemoteCommand, state: CompanionState) {
        val activeId = state.activeDevice?.identifier
        when (command.action) {
            "start" -> {
                require(command.expectedDeviceId == activeId) { "Active device changed. Refresh and try again." }
                require(state.devices.any { it.identifier == command.deviceId }) { "Device is no longer saved" }
            }
            "stop", "setAircraft", "setWifi" -> {
                require(activeId != null && command.expectedDeviceId == activeId) { "Active device changed. Refresh and try again." }
                if (command.action != "stop") require(state.bridge.bleConnected) { "Bluetooth is not connected" }
                if (command.action == "setAircraft") {
                    require(command.icaoAddress != null && state.bridge.ownshipConfiguration?.icaoAddressList?.contains(command.icaoAddress) == true) {
                        "Aircraft is no longer available"
                    }
                }
                if (command.action == "setWifi") {
                    require(state.bridge.ownshipConfiguration?.wifiMode in listOf(WifiMode.AP, WifiMode.CLIENT)) {
                        "Wi-Fi control is unavailable on this connection"
                    }
                    require(command.mode in listOf("AP", "CLIENT")) { "Invalid Wi-Fi mode" }
                }
            }
            "setGdl90" -> require(command.enabled != null) { "Missing GDL90 setting" }
            "saveDevice" -> require(state.discovery.devices.any { it.identifier == command.deviceId }) { "Device is no longer in the search results" }
            "deleteDevice" -> {
                require(state.devices.any { it.identifier == command.deviceId }) { "Device is no longer saved" }
                require(command.deviceId != activeId) { "Stop the bridge before removing this device" }
            }
            "startScan", "stopScan", "refreshAircraft" -> Unit
            else -> error("Unsupported command")
        }
    }
}
