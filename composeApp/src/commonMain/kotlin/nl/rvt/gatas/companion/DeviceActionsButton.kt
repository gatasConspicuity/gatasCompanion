package nl.rvt.gatas.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import nl.rvt.gatas.companion.services.BridgeStatus
import nl.rvt.gatas.companion.services.DeviceAction
import nl.rvt.gatas.companion.services.DeviceControlState
import nl.rvt.gatas.companion.services.DeviceUpdatePolicy

private data class ObservedDeviceAction(val action: DeviceAction, val deviceId: String?, val control: DeviceControlState)
private data class DeviceActionsTarget(val deviceId: String?, val bootId: String?)

@Composable
fun DeviceActionsButton(status: BridgeStatus, deviceId: String?) {
    val control = status.deviceControl
    var menuTarget by remember { mutableStateOf<DeviceActionsTarget?>(null) }
    var confirmation by remember { mutableStateOf<ObservedDeviceAction?>(null) }
    val available = status.bleConnected && control.operation?.busy != true &&
        status.aircraftChangeTargetIcaoAddress == null && status.wifiModeChangeTarget == null
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { menuTarget = DeviceActionsTarget(deviceId, control.bootId) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text("Device actions")
        }
        // Keep feedback visible after selecting an action closes the menu.
        control.operation?.let { operation ->
            if (operation.busy) CircularProgressIndicator()
            Text(operation.message, color = if (operation.phase in listOf("failed", "unknown"))
                MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
        }
    }

    menuTarget?.let { target ->
        // Opening a menu must not silently retarget its actions if the phone
        // changes devices or GATAS restarts while the menu is still visible.
        val sameDevice = target.deviceId == deviceId && target.bootId == control.bootId
        val menuAvailable = available && sameDevice
        fun select(action: DeviceAction) {
            menuTarget = null
            if (action == DeviceAction.Save) {
                CompanionController.requestDeviceAction(action, target.deviceId, target.bootId)
            } else {
                confirmation = ObservedDeviceAction(action, target.deviceId, control)
            }
        }
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            title = { Text("Device actions") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(when {
                        !sameDevice -> "GATAS connection changed. Close and reopen this menu."
                        !status.bleConnected -> "Connect to GATAS over Bluetooth to use device actions."
                        control.checking -> "Checking GATAS firmware support…"
                        control.bootId == null -> "Device actions require updated GATAS firmware."
                        control.operation?.busy == true -> control.operation.message
                        control.hasUnsavedChanges == true -> "Unsaved GATAS configuration"
                        else -> "GATAS configuration is saved"
                    })
                    OutlinedButton(
                        onClick = { select(DeviceAction.Save) },
                        enabled = menuAvailable && control.supports(DeviceAction.Save),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Save configuration") }
                    OutlinedButton(
                        onClick = { select(DeviceAction.Restart) },
                        enabled = menuAvailable && control.supports(DeviceAction.Restart),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Restart GATAS") }
                    OutlinedButton(
                        onClick = { select(DeviceAction.UsbBoot) },
                        enabled = menuAvailable && control.supports(DeviceAction.UsbBoot) && control.hasUnsavedChanges != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) { Text("Enter firmware update mode") }
                }
            },
            confirmButton = { TextButton(onClick = { menuTarget = null }) { Text("Close") } },
        )
    }

    confirmation?.let { observed ->
        val restart = observed.action == DeviceAction.Restart
        val dirty = observed.control.hasUnsavedChanges == true
        fun submit(policy: DeviceUpdatePolicy) {
            // Use what the person saw when opening the dialog, never silently
            // retarget its destructive action to a newer selected device/boot.
            CompanionController.requestDeviceAction(observed.action, observed.deviceId, observed.control.bootId, policy)
            confirmation = null
        }
        AlertDialog(onDismissRequest = { confirmation = null },
            title = { Text(if (restart) "Restart GATAS?" else "Start firmware update mode?") },
            text = { Text(if (restart)
                "Traffic forwarding will pause while GATAS restarts. Unsaved configuration remains available until power is disconnected."
                else "Connect GATAS to your computer through the microcontroller USB data port, not the charge port. Copy the matching UF2 file to the USB drive after it appears." +
                    if (dirty) "\n\nGATAS has unsaved configuration. Save it before continuing, or explicitly continue without saving." else "") },
            confirmButton = {
                Column {
                    TextButton(onClick = { submit(if (!restart && dirty) DeviceUpdatePolicy.SaveFirst else DeviceUpdatePolicy.RequireSaved) }) {
                        Text(if (restart) "Restart GATAS" else if (dirty) "Save and continue" else "Start firmware mode")
                    }
                    if (!restart && dirty) TextButton(onClick = { submit(DeviceUpdatePolicy.AllowUnsaved) }) { Text("Continue without saving") }
                }
            }, dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } })
    }
}
