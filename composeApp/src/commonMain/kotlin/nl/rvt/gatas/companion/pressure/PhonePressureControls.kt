package nl.rvt.gatas.companion.pressure

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
        Text("Measurements continue in the background while connected and may increase battery use. " +
            "Requires OpenAce 3.5.1 or compatible type-11 pressure support. " +
            "Firmware compatibility cannot be checked automatically.")
        Text(phonePressurePlatformNotice(), style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = status.enabled, enabled = connected || status.enabled, onCheckedChange = {
                BridgeBackgroundComponent.requestPhonePressureChange(device.identifier, it)
            })
            Text("Use phone barometer")
        }
        PhonePressureStatusDisplay(connected, status)
    }
}

@Composable
fun PhonePressureSummary(device: GaTasDevice, connected: Boolean, status: PhonePressureStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Phone barometer · ${device.name}", style = MaterialTheme.typography.titleMedium)
        PhonePressureStatusDisplay(connected, status)
    }
}

@Composable
private fun PhonePressureStatusDisplay(connected: Boolean, status: PhonePressureStatus) {
    Text(when {
        !status.enabled -> "Phone barometer disabled for this device"
        !connected -> "Waiting for Bluetooth"
        status.condition == PressureSensorCondition.Error -> "Barometer error: ${status.message ?: "Sensor unavailable"}"
        status.condition == PressureSensorCondition.PermissionRequired -> "Switch off and on while the app is visible to allow Motion & Fitness access."
        status.condition == PressureSensorCondition.PermissionDenied -> "Enable Motion & Fitness for this app in iOS Settings, then switch off and on."
        status.condition == PressureSensorCondition.Unavailable -> "This phone has no available barometer."
        status.stale -> "Barometer measurement is stale"
        status.packetsSent > 0 -> "Pressure sent over Bluetooth; use by GATAS is not confirmed."
        else -> "Waiting for a new barometer measurement"
    })
    status.pressureHpa?.let { pressure ->
        Text("${(pressure * 10).roundToInt() / 10f} hPa · sample age ${status.sampleAgeMillis ?: 0} ms")
    }
    Text("${status.elapsedMillis / 1000}s · samples ${status.samplesReceived} · sent ${status.packetsSent}")
    Text("Maximum gaps: sensor ${status.maximumSampleGapMillis} ms · send ${status.maximumSendGapMillis} ms")
    Text("Maximum sensor delivery age: ${status.maximumDeliveryAgeMillis} ms")
}
