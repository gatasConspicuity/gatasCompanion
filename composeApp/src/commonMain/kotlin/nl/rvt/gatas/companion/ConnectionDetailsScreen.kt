package nl.rvt.gatas.companion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.LineAwesomeIcons
import compose.icons.lineawesomeicons.ExchangeAltSolid
import compose.icons.lineawesomeicons.LocationArrowSolid
import compose.icons.lineawesomeicons.LongArrowAltRightSolid
import compose.icons.lineawesomeicons.MobileAltSolid
import compose.icons.lineawesomeicons.ServerSolid
import kotlinx.coroutines.flow.collect
import nl.rvt.gatas.companion.pressure.PhonePressureStatusMessage
import nl.rvt.gatas.companion.pressure.PressureSensorCondition
import nl.rvt.gatas.companion.services.BridgeStatus
import nl.rvt.gatas.companion.services.Gdl90State
import kotlin.math.roundToInt

@Composable
fun ConnectionDetailsScreen(bridgeStatus: BridgeStatus, device: GaTasDevice?) {
    val indicators = connectionIndicators(bridgeStatus)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectionDetailCard("UDP Relay", indicators[1]) {
            PacketRoute(DetailEndpoint.Phone, DetailEndpoint.Relay, bridgeStatus.udpPacketCount)
            ProtocolCounters(bridgeStatus.udpNmeaPackets, bridgeStatus.udpCobsPackets,
                bridgeStatus.udpNmeaActivityTick, bridgeStatus.udpCobsActivityTick, bridgeStatus.udpHealthy)
            if (bridgeStatus.lastRelayRoundTripMillis != null) {
                DetailDivider()
                DetailMetrics(listOf(
                    DetailMetric("Queue", "${bridgeStatus.lastRelayQueueDelayMillis ?: 0} ms"),
                    DetailMetric("Round trip", "${bridgeStatus.lastRelayRoundTripMillis} ms"),
                    DetailMetric("Dropped", bridgeStatus.relayQueueDrops.toString()),
                ))
            }
        }
        ConnectionDetailCard("Bluetooth", indicators[0]) {
            PacketRoute(DetailEndpoint.Phone, DetailEndpoint.Gatas, bridgeStatus.blePacketCount)
            ProtocolCounters(bridgeStatus.bleNmeaPackets, bridgeStatus.bleCobsPackets,
                bridgeStatus.bleNmeaActivityTick, bridgeStatus.bleCobsActivityTick, bridgeStatus.bleConnected)
        }
        val gdl90 = bridgeStatus.gdl90
        if (gdl90.enabled) {
            ConnectionDetailCard("GDL90", indicators[2].let {
                if (it.state == ConnectionIndicatorState.Healthy) it.copy(text = "Forwarding") else it
            }) {
                PacketRoute(DetailEndpoint.Phone, DetailEndpoint.Apps, gdl90.packetsSent,
                    bidirectional = false, caption = "Local apps · UDP 4000",
                    activityTick = gdl90.activityTick, active = gdl90IsActive(bridgeStatus))
                if (gdl90.state == Gdl90State.Error || gdl90.lastError != null) {
                    DetailNote(gdl90.lastError ?: "Unknown forwarding error", MaterialTheme.colorScheme.error)
                }
                DetailMetrics(listOf(
                    DetailMetric("Traffic", gdl90.trafficMessages.toString()),
                    DetailMetric("Ownship", gdl90.ownshipMessages.toString()),
                    DetailMetric("Heartbeat", gdl90.heartbeatMessages.toString()),
                    DetailMetric("Other", gdl90.otherMessages.toString()),
                ))
                if (gdl90.lastTrafficIntervalMillis != null || gdl90.lastForwardDurationMillis != null) {
                    DetailDivider()
                    DetailMetrics(listOf(
                        DetailMetric("Traffic gap", "${gdl90.lastTrafficIntervalMillis ?: 0} ms"),
                        DetailMetric("Max. gap", "${gdl90.maximumTrafficIntervalMillis} ms"),
                        DetailMetric("Forward", "${gdl90.lastForwardDurationMillis ?: 0} ms"),
                    ))
                }
            }
        }
        val pressure = bridgeStatus.phonePressure
        if (pressure.enabled && device != null) {
            ConnectionDetailCard("Phone barometer", indicators[3]) {
                PacketRoute(DetailEndpoint.Phone, DetailEndpoint.Gatas, pressure.packetsSent,
                    bidirectional = false, caption = device.name, activityTick = pressure.packetsSent,
                    active = phonePressureIsActive(bridgeStatus.bleConnected, pressure))
                if (bridgeStatus.bleConnected && pressure.condition in setOf(
                    PressureSensorCondition.Error, PressureSensorCondition.PermissionRequired,
                    PressureSensorCondition.PermissionDenied, PressureSensorCondition.Unavailable,
                )) {
                    PhonePressureStatusMessage(bridgeStatus.bleConnected, pressure, compact = true)
                }
                DetailMetrics(buildList {
                    pressure.pressureHpa?.let {
                        add(DetailMetric("Pressure", "${(it * 10).roundToInt() / 10f} hPa"))
                        add(DetailMetric("Sample age", "${pressure.sampleAgeMillis ?: 0} ms"))
                    }
                    add(DetailMetric("Samples", pressure.samplesReceived.toString()))
                    add(DetailMetric("Elapsed", "${pressure.elapsedMillis / 1000} s"))
                })
                DetailDivider()
                DetailMetrics(listOf(
                    DetailMetric("Max. sensor gap", "${pressure.maximumSampleGapMillis} ms"),
                    DetailMetric("Max. send gap", "${pressure.maximumSendGapMillis} ms"),
                    DetailMetric("Max. delivery age", "${pressure.maximumDeliveryAgeMillis} ms"),
                ))
                if (pressure.packetsSent > 0) {
                    DetailNote("Sent over Bluetooth; use by GATAS is not confirmed.")
                }
            }
        }
    }
}

@Composable
private fun ConnectionDetailCard(title: String, indicator: ConnectionIndicator, content: @Composable ColumnScope.() -> Unit) {
    val statusColor = when (indicator.state) {
        ConnectionIndicatorState.Healthy -> gatasSuccessColor()
        ConnectionIndicatorState.Attention -> MaterialTheme.colorScheme.error
        ConnectionIndicatorState.Disabled -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    GatasCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).semantics { heading() })
                Text(indicator.text, style = MaterialTheme.typography.bodySmall, color = statusColor,
                    textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            content()
        }
    }
}

private enum class DetailEndpoint(val description: String, val icon: ImageVector?) {
    Phone("This phone", LineAwesomeIcons.MobileAltSolid),
    Relay("GATAS relay server", LineAwesomeIcons.ServerSolid),
    Gatas("GATAS device", null),
    Apps("Apps on this phone", LineAwesomeIcons.LocationArrowSolid),
}

@Composable
private fun PacketRoute(
    from: DetailEndpoint,
    to: DetailEndpoint,
    packets: Long,
    bidirectional: Boolean = true,
    caption: String? = null,
    activityTick: Long = 0,
    active: Boolean = false,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val countWidth = maxWidth * 0.55f
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.clearAndSetSemantics {
                        contentDescription = "${from.description}, ${if (bidirectional) "two-way connection" else "to"}, ${to.description}"
                    },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    EndpointSymbol(from)
                    Icon(if (bidirectional) LineAwesomeIcons.ExchangeAltSolid else LineAwesomeIcons.LongArrowAltRightSolid,
                        contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    EndpointSymbol(to)
                    if (!bidirectional) ActivityPropeller(activityTick, active, Modifier.size(16.dp))
                }
                caption?.let { DetailNote(it) }
            }
            Column(horizontalAlignment = Alignment.End,
                modifier = Modifier.widthIn(max = countWidth).semantics(mergeDescendants = true) {}) {
                Text(packets.toString(), style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
                DetailNote("packets")
            }
        }
    }
}

@Composable
private fun EndpointSymbol(endpoint: DetailEndpoint) {
    if (endpoint.icon != null) {
        Icon(endpoint.icon, contentDescription = null, modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text("GATAS", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ProtocolCounters(nmea: Long, cobs: Long, nmeaTick: Long, cobsTick: Long, active: Boolean) {
    val density = LocalDensity.current
    val countStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
    val textMeasurer = rememberTextMeasurer()
    val valueWidth = with(density) {
        textMeasurer.measure(maxOf(nmea, cobs).toString(), style = countStyle).size.width.toDp()
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = maxWidth < maxOf(240.dp * density.fontScale, (valueWidth + 90.dp * density.fontScale) * 2 + 8.dp)
        val counter: @Composable (String, Long, Long, Modifier) -> Unit = { label, count, tick, modifier ->
            Row(modifier.semantics(mergeDescendants = true) {}
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActivityPropeller(tick, active, Modifier.size(16.dp))
                Text(count.toString(), style = countStyle,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.End)
            }
        }
        if (stack) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                counter("NMEA", nmea, nmeaTick, Modifier.fillMaxWidth())
                counter("COBS", cobs, cobsTick, Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                counter("NMEA", nmea, nmeaTick, Modifier.weight(1f))
                counter("COBS", cobs, cobsTick, Modifier.weight(1f))
            }
        }
    }
}

private data class DetailMetric(val label: String, val value: String)

@Composable
private fun DetailMetrics(metrics: List<DetailMetric>) {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val valueStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace)
    val valueWidth = with(density) {
        metrics.maxOf { textMeasurer.measure(it.value, style = valueStyle).size.width }.toDp()
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellWidth = maxOf(76.dp * density.fontScale, valueWidth + 4.dp)
        val capacity = ((maxWidth + 8.dp) / (cellWidth + 8.dp)).toInt().coerceAtLeast(1)
        val columns = (1..minOf(capacity, metrics.size)).last { metrics.size % it == 0 }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            metrics.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { metric ->
                        Column(Modifier.weight(1f).semantics(mergeDescendants = true) {},
                            verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            DetailNote(metric.label)
                            Text(metric.value, style = valueStyle)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

@Composable
private fun DetailNote(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = color)
}

@Composable
private fun ActivityPropeller(pulseTick: Long, active: Boolean, modifier: Modifier = Modifier) {
    val rotation = remember { Animatable(0f) }
    val latestTick by rememberUpdatedState(pulseTick)
    var previousTick by remember { mutableLongStateOf(pulseTick) }
    LaunchedEffect(active) {
        previousTick = latestTick
        if (!active) return@LaunchedEffect
        // Bursts coalesce into a complete turn; entering the page is not activity.
        snapshotFlow { latestTick }.collect { tick ->
            val advanced = tick > previousTick
            previousTick = tick
            if (advanced) {
                rotation.snapTo(0f)
                rotation.animateTo(360f, tween(480, easing = FastOutSlowInEasing))
            }
        }
    }
    val color = if (active) gatasSuccessColor() else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    Canvas(modifier.graphicsLayer { rotationZ = rotation.value }.clearAndSetSemantics {}) {
        val bladeWidth = size.width * 0.18f
        repeat(3) { index ->
            rotate(index * 120f) {
                drawRoundRect(color.copy(alpha = 0.7f),
                    topLeft = Offset((size.width - bladeWidth) / 2f, size.height * 0.08f),
                    size = Size(bladeWidth, size.height * 0.34f),
                    cornerRadius = CornerRadius(bladeWidth, bladeWidth))
            }
        }
        drawCircle(color, radius = size.minDimension * 0.12f)
    }
}
