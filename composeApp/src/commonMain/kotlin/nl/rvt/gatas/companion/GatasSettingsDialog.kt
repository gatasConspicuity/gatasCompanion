package nl.rvt.gatas.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import nl.rvantwisk.gatas.lib.models.WifiMode
import nl.rvt.gatas.companion.pressure.PhonePressureControls
import nl.rvt.gatas.companion.services.BridgeStatus

@Composable
fun GatasSettingsDialog(
    bridgeStatus: BridgeStatus,
    device: GaTasDevice?,
    onRequestWifiModeChange: (WifiMode) -> Unit,
    onDismiss: () -> Unit,
) {
    GatasDialog(title = "GATAS Settings", subtitle = device?.let { "For ${it.name}" }, onDismiss = onDismiss) {
        device?.let {
            GatasCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    PhonePressureControls(it, bridgeStatus.bleConnected, bridgeStatus.phonePressure)
                }
            }
        }
        WifiModeSettings(bridgeStatus, onRequestWifiModeChange)
    }
}

@Composable
private fun WifiModeSettings(status: BridgeStatus, onRequestChange: (WifiMode) -> Unit) {
    val currentMode = status.ownshipConfiguration?.wifiMode
    val pendingMode = status.wifiModeChangeTarget
    val applying = pendingMode != null && pendingMode != currentMode
    val modeKnown = currentMode == WifiMode.AP || currentMode == WifiMode.CLIENT
    val enabled = modeKnown && status.deviceControl.operation?.busy != true && !applying

    GatasCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Wi-Fi mode", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() })
            Text(
                when (currentMode) {
                    WifiMode.AP -> "Current: Access point (AP)"
                    WifiMode.CLIENT -> "Current: Wi-Fi client"
                    WifiMode.NC -> "Not configured"
                    null -> "Waiting for the current mode from GATAS"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 240.dp || LocalDensity.current.fontScale >= 1.5f) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        WifiModeOption("AP", "Access point", currentMode == WifiMode.AP, enabled,
                            onClick = { onRequestChange(WifiMode.AP) }, modifier = Modifier.fillMaxWidth())
                        WifiModeOption("CLIENT", "Wi-Fi client", currentMode == WifiMode.CLIENT, enabled,
                            onClick = { onRequestChange(WifiMode.CLIENT) }, modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        WifiModeOption("AP", "Access point", currentMode == WifiMode.AP, enabled,
                            onClick = { onRequestChange(WifiMode.AP) }, modifier = Modifier.weight(1f))
                        WifiModeOption("CLIENT", "Wi-Fi client", currentMode == WifiMode.CLIENT, enabled,
                            onClick = { onRequestChange(WifiMode.CLIENT) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (applying) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(status.wifiModeStatusMessage ?: "Applying Wi-Fi mode change…",
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            } else {
                status.wifiModeStatusMessage?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("The selected mode changes only after confirmation from GATAS.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WifiModeOption(
    label: String,
    description: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
        }
    }
    val buttonModifier = modifier.heightIn(min = 56.dp).semantics { this.selected = selected }
    if (selected) {
        Button(onClick = onClick, enabled = enabled, modifier = buttonModifier,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) { content() }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = buttonModifier,
            shape = MaterialTheme.shapes.medium,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)) { content() }
    }
}
