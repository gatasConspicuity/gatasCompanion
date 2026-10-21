package nl.rvt.gatas.companion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal const val UNAVAILABLE_GATAS_ID = "--------"

@Composable
internal fun DeviceInfoCard(
    gatasHexCode: String,
    bluetoothName: String,
    selectedAircraftCallsign: String,
    onOpenGatasDetails: (String) -> Unit,
    onChangeAircraft: () -> Unit,
    changeAircraftEnabled: Boolean,
) {
    GatasCard(Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val stacked = maxWidth < 320.dp || LocalDensity.current.fontScale >= 1.5f
            Column {
                DeviceInfoRow(
                    label = "GATAS ID", value = gatasHexCode, stacked = stacked,
                    onClick = { onOpenGatasDetails(gatasHexCode) },
                    enabled = gatasHexCode != UNAVAILABLE_GATAS_ID, onClickLabel = "Open GATAS details",
                )
                DeviceInfoDivider()
                DeviceInfoRow(label = "Bluetooth name", value = bluetoothName, stacked = stacked)
                DeviceInfoDivider()
                DeviceInfoRow(
                    label = "Registration", value = selectedAircraftCallsign, stacked = stacked,
                    onClick = onChangeAircraft, enabled = changeAircraftEnabled,
                    onClickLabel = "Change registration",
                )
            }
        }
    }
}

@Composable
private fun DeviceInfoDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 18.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
    )
}

@Composable
private fun DeviceInfoRow(
    label: String,
    value: String,
    stacked: Boolean,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    onClickLabel: String? = null,
) {
    val valueColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
        onClick != null -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val rowModifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).then(
        if (onClick == null) Modifier else Modifier.clickable(
            enabled = enabled, role = Role.Button, onClickLabel = onClickLabel, onClick = onClick,
        )
    ).semantics(mergeDescendants = true) {}

    Row(
        rowModifier.padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (stacked) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium, color = valueColor)
            }
        } else {
            Text(label, modifier = Modifier.weight(0.45f), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, modifier = Modifier.weight(0.55f), style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.End, color = valueColor)
        }
        Box(Modifier.width(16.dp), contentAlignment = Alignment.CenterEnd) {
            if (onClick != null) {
                Text("›", style = MaterialTheme.typography.titleLarge, color = valueColor,
                    modifier = Modifier.clearAndSetSemantics {})
            }
        }
    }
}
