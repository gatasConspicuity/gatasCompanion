package nl.rvt.gatas.companion.pressure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.rvt.gatas.companion.GaTasDevice
import nl.rvt.gatas.companion.background.BridgeBackgroundComponent
import kotlin.math.roundToInt

@Composable
fun PhonePressureControls(device: GaTasDevice, connected: Boolean, status: PhonePressureStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Phone barometer", style = MaterialTheme.typography.titleMedium)
        Text("For ${device.name}")
        Text("Uses your phone’s barometer to send air pressure to this GATAS device via Bluetooth. " +
            "This lets GATAS calculate pressure altitude without an onboard BMP280 sensor. " +
            "Your choice is saved for this device.")
        Text("Measurements continue in the background while connected and may increase battery use.")
        Text(phonePressurePlatformNotice(), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = status.enabled, enabled = connected || status.enabled, onCheckedChange = {
                BridgeBackgroundComponent.requestPhonePressureChange(device.identifier, it)
            })
            Text("Use phone barometer")
        }
        PhonePressureStatusMessage(connected, status)
    }
}

@Composable
fun PhonePressureSummary(device: GaTasDevice, connected: Boolean, status: PhonePressureStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Phone barometer · ${device.name}", style = MaterialTheme.typography.titleMedium)
        PhonePressureStatusMessage(connected, status)
        PhonePressureReadings(status)
    }
}

@Composable
private fun PhonePressureStatusMessage(connected: Boolean, status: PhonePressureStatus) {
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val statusColor = when {
        !status.enabled || !connected -> mutedColor
        status.condition == PressureSensorCondition.Error ||
            status.condition == PressureSensorCondition.PermissionDenied ||
            status.condition == PressureSensorCondition.Unavailable || status.stale -> MaterialTheme.colorScheme.error
        status.condition == PressureSensorCondition.Available && status.packetsSent > 0 -> Color(0xFF57D48B)
        else -> mutedColor
    }
    Text(text = when {
        !status.enabled -> "Phone barometer disabled for this device"
        !connected -> "Waiting for Bluetooth"
        status.condition == PressureSensorCondition.Error -> "Barometer error: ${status.message ?: "Sensor unavailable"}"
        status.condition == PressureSensorCondition.PermissionRequired -> "Switch off and on while the app is visible to allow Motion & Fitness access."
        status.condition == PressureSensorCondition.PermissionDenied -> "Enable Motion & Fitness for this app in iOS Settings, then switch off and on."
        status.condition == PressureSensorCondition.Unavailable -> "This phone has no available barometer."
        status.stale -> "Barometer measurement is stale"
        status.packetsSent > 0 -> "Pressure sent over Bluetooth; use by GATAS is not confirmed."
        else -> "Waiting for a new barometer measurement"
    }, style = MaterialTheme.typography.bodyLarge, color = statusColor, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun PhonePressureReadings(status: PhonePressureStatus) {
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    status.pressureHpa?.let { pressure ->
        Text(
            text = "${(pressure * 10).roundToInt() / 10f} hPa · sample age ${status.sampleAgeMillis ?: 0} ms",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    Text(
        text = "${status.elapsedMillis / 1000}s · samples ${status.samplesReceived} · sent ${status.packetsSent}",
        style = MaterialTheme.typography.bodySmall,
        color = mutedColor,
    )
    Text(
        text = "Maximum gaps: sensor ${status.maximumSampleGapMillis} ms · send ${status.maximumSendGapMillis} ms",
        style = MaterialTheme.typography.bodySmall,
        color = mutedColor,
    )
    Text(
        text = "Maximum sensor delivery age: ${status.maximumDeliveryAgeMillis} ms",
        style = MaterialTheme.typography.bodySmall,
        color = mutedColor,
    )
}
