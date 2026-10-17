package nl.rvt.gatas.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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

@Composable
fun DeviceActionsCard(status: BridgeStatus, deviceId: String?) {
    val control = status.deviceControl
    var confirmation by remember { mutableStateOf<ObservedDeviceAction?>(null) }
    val available = status.bleConnected && control.operation?.busy != true &&
        status.aircraftChangeTargetIcaoAddress == null && status.wifiModeChangeTarget == null
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Device actions", style = MaterialTheme.typography.titleMedium)
            when {
                !status.bleConnected -> Text("Connect to GATAS over Bluetooth to use device actions.")
                control.checking -> Text("Checking GATAS firmware support…")
                control.bootId == null -> Text("Device actions require updated GATAS firmware.")
                else -> Text(if (control.hasUnsavedChanges == true) "Unsaved GATAS configuration" else "GATAS configuration is saved")
            }
            OutlinedButton(onClick = { CompanionController.requestDeviceAction(DeviceAction.Save, deviceId, control.bootId) },
                enabled = available && control.supports(DeviceAction.Save), modifier = Modifier.fillMaxWidth()) {
                Text("Save configuration")
            }
            OutlinedButton(onClick = { confirmation = ObservedDeviceAction(DeviceAction.Restart, deviceId, control) },
                enabled = available && control.supports(DeviceAction.Restart), modifier = Modifier.fillMaxWidth()) {
                Text("Restart GATAS")
            }
            OutlinedButton(onClick = { confirmation = ObservedDeviceAction(DeviceAction.UsbBoot, deviceId, control) },
                enabled = available && control.supports(DeviceAction.UsbBoot) && control.hasUnsavedChanges != null,
                modifier = Modifier.fillMaxWidth()) { Text("Enter firmware update mode") }
            control.operation?.let { operation ->
                if (operation.busy) CircularProgressIndicator()
                Text(operation.message, color = if (operation.phase in listOf("failed", "unknown"))
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            }
        }
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
