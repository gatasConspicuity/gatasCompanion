package nl.rvt.gatas.companion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import nl.rvt.gatas.companion.pressure.PhonePressureStatus
import nl.rvt.gatas.companion.pressure.PressureSensorCondition
import nl.rvt.gatas.companion.services.BridgeStatus
import nl.rvt.gatas.companion.services.Gdl90State
import kotlin.math.PI
import kotlin.math.sin

enum class ConnectionIndicatorState { Healthy, Attention, Disabled }

data class ConnectionIndicator(
    val title: String,
    val text: String,
    val state: ConnectionIndicatorState,
    val activityCount: Long,
)

fun gdl90IsActive(status: BridgeStatus): Boolean =
    status.bleConnected && status.gdl90.enabled && status.gdl90.state == Gdl90State.Sending &&
        status.gdl90.packetsSent > 0 && status.gdl90.lastError == null

fun phonePressureIsActive(connected: Boolean, status: PhonePressureStatus): Boolean =
    status.enabled && connected && !status.stale &&
        status.condition == PressureSensorCondition.Available && status.packetsSent > 0

fun connectionIndicators(status: BridgeStatus): List<ConnectionIndicator> {
    fun indicator(title: String, enabled: Boolean = true, healthy: Boolean, activityCount: Long, text: String) =
        ConnectionIndicator(title, if (enabled) text else "Disabled", when {
            !enabled -> ConnectionIndicatorState.Disabled
            healthy -> ConnectionIndicatorState.Healthy
            else -> ConnectionIndicatorState.Attention
        }, activityCount)
    val pressure = status.phonePressure
    return listOf(
        indicator("Bluetooth", healthy = status.bleConnected, activityCount = status.gatasActivityTick, text = when {
            status.bleConnected -> "Connected"
            status.connecting -> "Connecting…"
            else -> "Waiting for Bluetooth"
        }),
        indicator("UDP Relay", healthy = status.udpHealthy, activityCount = status.framesRelayed,
            text = if (status.udpHealthy) "Connected" else "Waiting for UDP"),
        indicator("GDL90", enabled = status.gdl90.enabled, healthy = gdl90IsActive(status),
            activityCount = status.gdl90.packetsSent, text = when {
            status.gdl90.state == Gdl90State.Error || status.gdl90.lastError != null -> "Forwarding error"
            !status.bleConnected -> "Waiting for Bluetooth"
            gdl90IsActive(status) -> "Forwarding to this device"
            else -> "Waiting for GDL90 frames"
        }),
        indicator("Phone Barometer", enabled = pressure.enabled,
            healthy = phonePressureIsActive(status.bleConnected, pressure), activityCount = pressure.packetsSent, text = when {
                !status.bleConnected -> "Waiting for Bluetooth"
                pressure.condition == PressureSensorCondition.PermissionDenied -> "Permission denied"
                pressure.condition == PressureSensorCondition.PermissionRequired -> "Permission required"
                pressure.condition == PressureSensorCondition.Unavailable -> "Sensor unavailable"
                pressure.condition == PressureSensorCondition.Error -> "Sensor error"
                pressure.stale -> "Measurement is stale"
                phonePressureIsActive(status.bleConnected, pressure) -> "Sending pressure"
                else -> "Waiting for a measurement"
            }),
    )
}

@Composable
fun ConnectionSummaryCard(status: BridgeStatus, onClick: () -> Unit) {
    GatasCard(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "Open connection info", onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Connection Info", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("›", style = MaterialTheme.typography.headlineSmall, color = GatasBlue,
                    modifier = Modifier.clearAndSetSemantics {})
            }
            connectionIndicators(status).forEach { indicator ->
                val color = when (indicator.state) {
                    ConnectionIndicatorState.Healthy -> gatasSuccessColor()
                    ConnectionIndicatorState.Attention -> MaterialTheme.colorScheme.error
                    ConnectionIndicatorState.Disabled -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ConnectionActivityMarker(
                        activityCount = indicator.activityCount,
                        active = indicator.state == ConnectionIndicatorState.Healthy,
                        color = color,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(indicator.title, style = MaterialTheme.typography.bodyLarge)
                        Text(indicator.text, style = MaterialTheme.typography.bodyMedium, color = color)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionActivityMarker(activityCount: Long, active: Boolean, color: Color) {
    val progress = remember { Animatable(1f) }
    val latestCount by rememberUpdatedState(activityCount)
    var previousCount by remember { mutableLongStateOf(activityCount) }

    LaunchedEffect(active) {
        if (!active) {
            previousCount = latestCount
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        // Observe increases only: opening the page or resetting a session is not traffic.
        // Collect sequentially so bursts coalesce instead of restarting the pulse on every packet.
        snapshotFlow { latestCount }.collect { count ->
            val advanced = count > previousCount
            previousCount = count
            if (advanced) {
                progress.snapTo(0f)
                progress.animateTo(1f, tween(durationMillis = 600, easing = LinearEasing))
                delay(1400)
            }
        }
    }

    Canvas(Modifier.size(10.dp).clearAndSetSemantics {}) {
        val radius = size.minDimension / 2f
        val phase = progress.value
        val pulse = if (active) sin(phase * PI).toFloat().coerceAtLeast(0f) else 0f
        drawCircle(color = lerp(color, Color.White, 0.35f * pulse), radius = radius)
    }
}
