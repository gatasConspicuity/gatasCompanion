package nl.rvt.gatas.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.LineAwesomeIcons
import compose.icons.lineawesomeicons.CheckCircleSolid
import compose.icons.lineawesomeicons.ExclamationTriangleSolid
import compose.icons.lineawesomeicons.InfoCircleSolid
import compose.icons.lineawesomeicons.MicrochipSolid
import compose.icons.lineawesomeicons.RedoAltSolid
import compose.icons.lineawesomeicons.SaveSolid
import nl.rvt.gatas.companion.services.BridgeStatus
import nl.rvt.gatas.companion.services.DeviceAction
import nl.rvt.gatas.companion.services.DeviceUpdatePolicy

@Composable
internal fun DeviceActionsDialog(
    status: BridgeStatus,
    sameDevice: Boolean,
    available: Boolean,
    onSelect: (DeviceAction) -> Unit,
    onDismiss: () -> Unit,
) {
    val control = status.deviceControl
    val unavailable = !sameDevice || !status.bleConnected || (!control.checking && control.bootId == null)
    val saved = !unavailable && !control.checking && control.operation?.busy != true && control.hasUnsavedChanges == false
    GatasDialog(title = "Device Actions", onDismiss = onDismiss) {
        GatasCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(when {
                    unavailable -> LineAwesomeIcons.ExclamationTriangleSolid
                    saved -> LineAwesomeIcons.CheckCircleSolid
                    else -> LineAwesomeIcons.InfoCircleSolid
                }, contentDescription = null, modifier = Modifier.size(18.dp), tint = when {
                    unavailable -> MaterialTheme.colorScheme.error
                    saved -> gatasSuccessColor()
                    else -> MaterialTheme.colorScheme.primary
                })
                Text(when {
                    !sameDevice -> "GATAS connection changed. Close and reopen this menu."
                    !status.bleConnected -> "Connect to GATAS over Bluetooth to use device actions."
                    control.checking -> "Checking GATAS firmware support…"
                    control.bootId == null -> "Device actions require updated GATAS firmware."
                    control.operation?.busy == true -> control.operation.message
                    control.hasUnsavedChanges == true -> "Unsaved GATAS configuration"
                    else -> "GATAS configuration is saved"
                }, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DeviceActionOption("Save configuration", LineAwesomeIcons.SaveSolid,
            enabled = available && control.supports(DeviceAction.Save), onClick = { onSelect(DeviceAction.Save) })
        DeviceActionOption("Restart GATAS", LineAwesomeIcons.RedoAltSolid,
            enabled = available && control.supports(DeviceAction.Restart), onClick = { onSelect(DeviceAction.Restart) })
        DeviceActionOption("Enter firmware update mode", LineAwesomeIcons.MicrochipSolid,
            enabled = available && control.supports(DeviceAction.UsbBoot) && control.hasUnsavedChanges != null,
            onClick = { onSelect(DeviceAction.UsbBoot) })
    }
}

@Composable
private fun DeviceActionOption(title: String, icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        shape = MaterialTheme.shapes.medium,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 12.dp).size(20.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun DeviceActionConfirmationDialog(
    action: DeviceAction,
    hasUnsavedChanges: Boolean,
    onSubmit: (DeviceUpdatePolicy) -> Unit,
    onDismiss: () -> Unit,
) {
    val restart = action == DeviceAction.Restart
    GatasDialog(title = if (restart) "Restart GATAS?" else "Start firmware update mode?", onDismiss = onDismiss) {
        Text(if (restart)
            "Traffic forwarding will pause while GATAS restarts. Unsaved configuration remains available until power is disconnected."
            else "Connect GATAS to your computer through the microcontroller USB data port, not the charge port. Copy the matching UF2 file to the USB drive after it appears.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!restart && hasUnsavedChanges) {
            GatasCard(Modifier.fillMaxWidth()) {
                Text("GATAS has unsaved configuration. Save it before continuing, or explicitly continue without saving.",
                    Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
        Button(
            onClick = { onSubmit(if (!restart && hasUnsavedChanges) DeviceUpdatePolicy.SaveFirst else DeviceUpdatePolicy.RequireSaved) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(if (restart) "Restart GATAS" else if (hasUnsavedChanges) "Save and continue" else "Start firmware mode",
                style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
        if (!restart && hasUnsavedChanges) {
            TextButton(
                onClick = { onSubmit(DeviceUpdatePolicy.AllowUnsaved) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Continue without saving", style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center) }
        }
        OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.medium) {
            Text("Cancel", style = MaterialTheme.typography.labelLarge)
        }
    }
}
