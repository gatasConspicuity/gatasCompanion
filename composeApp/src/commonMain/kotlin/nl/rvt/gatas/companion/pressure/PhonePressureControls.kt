package nl.rvt.gatas.companion.pressure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.rvt.gatas.companion.gatasSuccessColor
import nl.rvt.gatas.companion.GaTasDevice
import nl.rvt.gatas.companion.background.BridgeBackgroundComponent

@Composable
fun PhonePressureControls(device: GaTasDevice, connected: Boolean, status: PhonePressureStatus) {
    var showDetails by rememberSaveable(device.identifier) { mutableStateOf(false) }
    val enabled = connected || status.enabled
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                value = status.enabled, enabled = enabled, role = Role.Switch,
                onValueChange = { BridgeBackgroundComponent.requestPhonePressureChange(device.identifier, it) },
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Phone barometer", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f).padding(end = 12.dp).semantics { heading() })
            Switch(checked = status.enabled, enabled = enabled, onCheckedChange = null)
        }
        Text("Use this phone’s air pressure instead of an onboard sensor.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        PhonePressureStatusMessage(connected, status, compact = true)
        TextButton(onClick = { showDetails = !showDetails }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (showDetails) "Hide details" else "How it works", style = MaterialTheme.typography.labelMedium)
        }
        if (showDetails) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Sends air pressure to GATAS over Bluetooth so it can calculate pressure altitude. " +
                    "This setting is saved separately for each GATAS device.", style = MaterialTheme.typography.bodySmall)
                Text("Measurements continue in the background while connected and may increase battery use.",
                    style = MaterialTheme.typography.bodySmall)
                Text(phonePressurePlatformNotice(), style = MaterialTheme.typography.bodySmall)
                Text("Sending pressure does not confirm that GATAS is using it.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
internal fun PhonePressureStatusMessage(connected: Boolean, status: PhonePressureStatus, compact: Boolean = false) {
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val statusColor = when {
        !status.enabled || !connected -> mutedColor
        status.condition == PressureSensorCondition.Error ||
            status.condition == PressureSensorCondition.PermissionDenied ||
            status.condition == PressureSensorCondition.Unavailable || status.stale -> MaterialTheme.colorScheme.error
        status.condition == PressureSensorCondition.Available && status.packetsSent > 0 -> gatasSuccessColor()
        else -> mutedColor
    }
    Text(text = when {
        !status.enabled -> if (compact) "Disabled for this device" else "Phone barometer disabled for this device"
        !connected -> "Waiting for Bluetooth"
        status.condition == PressureSensorCondition.Error -> "Barometer error: ${status.message ?: "Sensor unavailable"}"
        status.condition == PressureSensorCondition.PermissionRequired -> "Switch off and on while the app is visible to allow Motion & Fitness access."
        status.condition == PressureSensorCondition.PermissionDenied -> "Enable Motion & Fitness for this app in iOS Settings, then switch off and on."
        status.condition == PressureSensorCondition.Unavailable -> "This phone has no available barometer."
        status.stale -> "Barometer measurement is stale"
        status.packetsSent > 0 -> if (compact) "Pressure sent over Bluetooth" else "Pressure sent over Bluetooth; use by GATAS is not confirmed."
        else -> "Waiting for a new barometer measurement"
    }, style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
        color = statusColor, fontWeight = if (compact) FontWeight.Medium else FontWeight.SemiBold)
}
