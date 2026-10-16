@file:OptIn(ExperimentalFoundationApi::class)

package nl.rvt.gatas.companion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import compose.icons.LineAwesomeIcons
import compose.icons.lineawesomeicons.CogSolid
import nl.rvt.gatas.PlatformKeepScreenOnEffect
import nl.rvantwisk.gatas.lib.models.WifiMode
import nl.rvt.gatas.companion.services.BridgeStatus

@Composable
fun ConnectScreen(
    bridgeStatus: BridgeStatus,
    onBack: () -> Unit,
    onStopBridge: () -> Unit,
) {
    val scrollState = rememberScrollState()
    var showAircraftDialog by remember { mutableStateOf(false) }
    var showGatasSettingsDialog by remember { mutableStateOf(false) }
    var gatasBrowserHexCode by remember { mutableStateOf<String?>(null) }
    val ownshipConfiguration = bridgeStatus.ownshipConfiguration
    val companionState by CompanionController.state.collectAsState()
    val aircraftPickerEntries = companionState.aircraft
    val aircraftPickerLoading = companionState.aircraftLoading
    val aircraftPickerError = companionState.aircraftError

    PlatformKeepScreenOnEffect()
    LaunchedEffect(showAircraftDialog) {
        if (showAircraftDialog) CompanionController.refreshAircraft()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ConnectionInfoCard(
            gatasHexCode = bridgeStatus.ownshipConfiguration?.gatasId?.toHexId()
                ?: UNAVAILABLE_GATAS_ID,
            selectedAircraftCallsign = currentSelectedAircraftCallsign(
                ownshipConfiguration = bridgeStatus.ownshipConfiguration,
                aircraftPickerEntries = aircraftPickerEntries,
            ),
            onOpenGatasDetails = { hexCode -> gatasBrowserHexCode = hexCode },
            onOpenGatasSettings = { showGatasSettingsDialog = true },
            onChangeAircraft = { showAircraftDialog = true },
            changeAircraftEnabled = ownshipConfiguration != null,
        )

        LinkStatusCard(
            leftEndpoint = RouteEndpoint(
                label = "GATAS",
            ),
            rightEndpoint = RouteEndpoint(
                label = "Phone",
            ),
            connected = bridgeStatus.udpHealthy,
            statusText = if (bridgeStatus.udpHealthy) {
                "Connected via UDP"
            } else {
                "Waiting for UDP"
            },
            nmeaPackets = bridgeStatus.udpNmeaPackets,
            cobsPackets = bridgeStatus.udpCobsPackets,
            nmeaTick = bridgeStatus.udpNmeaActivityTick,
            cobsTick = bridgeStatus.udpCobsActivityTick,
            totalPackets = bridgeStatus.udpPacketCount,
            diagnosticsText = bridgeStatus.lastRelayRoundTripMillis?.let { roundTripMillis ->
                "Relay diagnostics · queue ${bridgeStatus.lastRelayQueueDelayMillis ?: 0} ms · " +
                    "round trip $roundTripMillis ms · drops ${bridgeStatus.relayQueueDrops}"
            },
        )

        LinkStatusCard(
            leftEndpoint = RouteEndpoint(
                label = "Phone",
            ),
            rightEndpoint = RouteEndpoint(
                label = "GATAS",
            ),
            connected = bridgeStatus.bleConnected,
            statusText = when {
                bridgeStatus.bleConnected -> "Connected via Bluetooth"
                bridgeStatus.connecting -> "Connecting via Bluetooth"
                else -> "Waiting for Bluetooth"
            },
            nmeaPackets = bridgeStatus.bleNmeaPackets,
            cobsPackets = bridgeStatus.bleCobsPackets,
            nmeaTick = bridgeStatus.bleNmeaActivityTick,
            cobsTick = bridgeStatus.bleCobsActivityTick,
            totalPackets = bridgeStatus.blePacketCount,
        )

        if (bridgeStatus.gdl90.enabled) {
            Gdl90BridgeCard(status = bridgeStatus)
        }

        Button(
            onClick = {
                onStopBridge()
                onBack()
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color(0xFFF44336),
                contentColor = Color.White
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Stop bridge")
        }

        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showAircraftDialog) {
        AircraftSelectionDialog(
            entries = aircraftPickerEntries,
            loading = aircraftPickerLoading,
            loadError = aircraftPickerError,
            selectedIcaoAddress = ownshipConfiguration?.icaoAddress,
            pendingIcaoAddress = bridgeStatus.aircraftChangeTargetIcaoAddress,
            onSelectAircraft = CompanionController::changeAircraft,
            onDismiss = { showAircraftDialog = false },
        )
    }

    if (showGatasSettingsDialog) {
        GatasSettingsDialog(
            bridgeStatus = bridgeStatus,
            onRequestWifiModeChange = CompanionController::changeWifiMode,
            onDismiss = { showGatasSettingsDialog = false },
        )
    }

    gatasBrowserHexCode?.let { hexCode ->
        GatasBrowserDialog(
            hexCode = hexCode,
            onDismiss = { gatasBrowserHexCode = null },
        )
    }
}

private const val UNAVAILABLE_GATAS_ID = "--------"

private fun UInt.toHexId(): String = toString(16).uppercase().padStart(8, '0')
private fun Long.toIcaoHex(): String = toString(16).uppercase().padStart(6, '0')

private fun currentSelectedAircraftCallsign(
    ownshipConfiguration: nl.rvantwisk.gatas.lib.models.OwnshipAircraftConfiguration?,
    aircraftPickerEntries: List<AircraftPickerEntry>,
): String {
    val selectedIcaoAddress = ownshipConfiguration?.icaoAddress ?: return "------"
    return aircraftPickerEntries
        .firstOrNull { it.icaoAddress == selectedIcaoAddress }
        ?.registration
        ?.takeUnless { it.isBlank() || it == "-" }
        ?: selectedIcaoAddress.toIcaoHex()
}

@Composable
private fun ConnectionInfoCard(
    gatasHexCode: String,
    selectedAircraftCallsign: String,
    onOpenGatasDetails: (String) -> Unit,
    onOpenGatasSettings: () -> Unit,
    onChangeAircraft: () -> Unit,
    changeAircraftEnabled: Boolean,
) {
    val gatasHexCodeAvailable = gatasHexCode != UNAVAILABLE_GATAS_ID

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "GATAS ID",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onOpenGatasSettings) {
                        Icon(
                            imageVector = LineAwesomeIcons.CogSolid,
                            contentDescription = "GATAS settings",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Text(
                        text = gatasHexCode,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (gatasHexCodeAvailable) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textDecoration = if (gatasHexCodeAvailable) {
                            TextDecoration.Underline
                        } else {
                            TextDecoration.None
                        },
                        modifier = Modifier.clickable(
                            enabled = gatasHexCodeAvailable,
                            onClick = { onOpenGatasDetails(gatasHexCode) },
                        )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Registration",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = onChangeAircraft,
                    enabled = changeAircraftEnabled,
                ) {
                    Text(
                        text = selectedAircraftCallsign,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun GatasSettingsDialog(
    bridgeStatus: BridgeStatus,
    onRequestWifiModeChange: (WifiMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentWifiMode = bridgeStatus.ownshipConfiguration?.wifiMode
    val pendingWifiMode = bridgeStatus.wifiModeChangeTarget
    val wifiModeKnown = currentWifiMode == WifiMode.AP || currentWifiMode == WifiMode.CLIENT
    val showPendingSpinner = pendingWifiMode != null && pendingWifiMode != currentWifiMode

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GATAS Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }

                Text(
                    text = "Wi-Fi Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentWifiMode == WifiMode.AP) {
                        Button(
                            onClick = { onRequestWifiModeChange(WifiMode.AP) },
                            enabled = wifiModeKnown && !showPendingSpinner,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("AP")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onRequestWifiModeChange(WifiMode.AP) },
                            enabled = wifiModeKnown && !showPendingSpinner,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("AP")
                        }
                    }

                    if (currentWifiMode == WifiMode.CLIENT) {
                        Button(
                            onClick = { onRequestWifiModeChange(WifiMode.CLIENT) },
                            enabled = wifiModeKnown && !showPendingSpinner,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CLIENT")
                        }
                    } else {
                        OutlinedButton(
                            onClick = { onRequestWifiModeChange(WifiMode.CLIENT) },
                            enabled = wifiModeKnown && !showPendingSpinner,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("CLIENT")
                        }
                    }
                }

                Text(
                    text = when (currentWifiMode) {
                        WifiMode.NC -> "Current mode reported by GATAS: Not configured"
                        WifiMode.AP -> "Current mode reported by GATAS: AP"
                        WifiMode.CLIENT -> "Current mode reported by GATAS: Client"
                        null -> "Current Wi-Fi mode is not available yet."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (showPendingSpinner) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = bridgeStatus.wifiModeStatusMessage
                                ?: "Applying Wi-Fi mode change...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFB26A00)
                        )
                    }
                } else {
                    bridgeStatus.wifiModeStatusMessage?.let { statusMessage ->
                        Text(
                            text = statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }

                Text(
                    text = if (showPendingSpinner) {
                        "The selected button shows the last mode confirmed by GATAS. It changes only after the device reports the new mode."
                    } else {
                        "Changes are asynchronous. After you send a request, GATAS will report the active mode back here."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GatasBrowserDialog(
    hexCode: String,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 760.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GATAS Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = onDismiss) {
                        Text("Back")
                    }
                }

                EmbeddedWebView(
                    url = "https://gatas.vantwisk.nl/#$hexCode",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(20.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun AircraftSelectionDialog(
    entries: List<AircraftPickerEntry>,
    loading: Boolean,
    loadError: String?,
    selectedIcaoAddress: Long?,
    pendingIcaoAddress: Long?,
    onSelectAircraft: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 680.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Change Aircraft",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }

                when {
                    loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    loadError != null && entries.isEmpty() -> {
                        Text(
                            text = loadError,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    entries.isEmpty() -> {
                        Text(
                            text = "No aircraft available.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 220.dp),
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(entries) { entry ->
                                AircraftPickerCard(
                                    entry = entry,
                                    selected = selectedIcaoAddress == entry.icaoAddress,
                                    pending = pendingIcaoAddress == entry.icaoAddress,
                                    onClick = { onSelectAircraft(entry.icaoAddress) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AircraftPickerCard(
    entry: AircraftPickerEntry,
    selected: Boolean,
    pending: Boolean,
    onClick: () -> Unit,
) {
    val backgroundTint = if (selected) Color(0xFFE9FADF) else MaterialTheme.colorScheme.surface
    val borderColor = when {
        pending -> MaterialTheme.colorScheme.primary
        selected -> Color(0xFF0F9D2A)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundTint),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backgroundTint)
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (pending) animatedSelectionBarColor() else MaterialTheme.colorScheme.outline.copy(
                                alpha = 0.18f
                            )
                        )
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = entry.registration,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.aircraftType,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = entry.icaoHexCode,
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (selected) {
                Text(
                    text = "✓",
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 30.sp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun animatedSelectionBarColor(): Color {
    val transition = rememberInfiniteTransition()
    val alpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = LinearEasing)
        )
    )
    return MaterialTheme.colorScheme.primary.copy(alpha = alpha)
}

@Composable
private fun LinkStatusCard(
    leftEndpoint: RouteEndpoint,
    rightEndpoint: RouteEndpoint,
    connected: Boolean,
    statusText: String,
    nmeaPackets: Long,
    cobsPackets: Long,
    nmeaTick: Long,
    cobsTick: Long,
    totalPackets: Long,
    diagnosticsText: String? = null,
) {
    val activeColor = if (connected) Color(0xFF57D48B) else Color(0xFFF44336)
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ConnectionRoute(
                leftEndpoint = leftEndpoint,
                rightEndpoint = rightEndpoint,
                connected = connected,
                statusColor = activeColor,
                packetCount = totalPackets,
            )

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyLarge,
                color = activeColor,
                fontWeight = FontWeight.SemiBold
            )

            diagnosticsText?.let { diagnostics ->
                Text(
                    text = diagnostics,
                    style = MaterialTheme.typography.bodySmall,
                    color = mutedColor,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProtocolActivityIndicator(
                    label = "NMEA",
                    pulseTick = nmeaTick,
                    active = connected,
                    color = activeColor,
                )
                Spacer(modifier = Modifier.width(28.dp))
                ProtocolActivityIndicator(
                    label = "COBS",
                    pulseTick = cobsTick,
                    active = connected,
                    color = activeColor,
                    reverse = true,
                )
            }
        }
    }
}

@Composable
private fun Gdl90BridgeCard(status: BridgeStatus) {
    val gdl90 = status.gdl90
    val active = gdl90.packetsSent > 0 && gdl90.lastError == null
    val textColor = if (active) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ConnectionRoute(
                leftEndpoint = RouteEndpoint(
                    label = "Phone",
                ),
                rightEndpoint = RouteEndpoint(
                    label = "This device",
                ),
                connected = active,
                statusColor = textColor,
                packetCount = gdl90.packetsSent,
            )
            Text(
                text = when (gdl90.state) {
                    nl.rvt.gatas.companion.services.Gdl90State.Disabled -> "GDL90 forwarding disabled"
                    nl.rvt.gatas.companion.services.Gdl90State.WaitingForFrames -> "Waiting for GDL90 frames from GATAS"
                    nl.rvt.gatas.companion.services.Gdl90State.Sending -> "GDL90 packets sent to this device · UDP 4000"
                    nl.rvt.gatas.companion.services.Gdl90State.Error -> "GDL90 forwarding error: ${gdl90.lastError ?: "Unknown error"}"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "GDL90 messages · traffic ${gdl90.trafficMessages} · " +
                    "ownship ${gdl90.ownshipMessages} · heartbeat ${gdl90.heartbeatMessages} · " +
                    "other ${gdl90.otherMessages}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (gdl90.lastTrafficIntervalMillis != null || gdl90.lastForwardDurationMillis != null) {
                Text(
                    text = "Timing · last traffic interval ${gdl90.lastTrafficIntervalMillis ?: 0} ms · " +
                        "maximum ${gdl90.maximumTrafficIntervalMillis} ms · " +
                        "forward ${gdl90.lastForwardDurationMillis ?: 0} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProtocolActivityIndicator(
                    label = "GDL90",
                    pulseTick = gdl90.activityTick,
                    active = active,
                    color = textColor,
                )
            }
        }
    }
}

@Composable
private fun ConnectionRoute(
    leftEndpoint: RouteEndpoint,
    rightEndpoint: RouteEndpoint,
    connected: Boolean,
    statusColor: Color,
    packetCount: Long,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LabeledEndpointMarker(leftEndpoint, statusColor, connected)
        TrafficFlowLanes(
            connected = connected,
            color = statusColor,
            packetCount = packetCount,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .padding(horizontal = 8.dp),
        )
        LabeledEndpointMarker(rightEndpoint, statusColor, connected)
    }
}

private data class RouteEndpoint(
    val label: String,
)

private const val WALKING_ANTS_PIXELS_PER_PACKET = 8f
private const val WALKING_ANTS_ANIMATION_DURATION_MS = 480

@Composable
private fun LabeledEndpointMarker(
    endpoint: RouteEndpoint,
    accentColor: Color,
    active: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        EndpointMarker(
            accentColor = accentColor,
            active = active,
        )
        Text(
            text = endpoint.label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.75f,
            ),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun TrafficFlowLanes(
    connected: Boolean,
    color: Color,
    packetCount: Long,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val animatedDistance = remember { Animatable(0f) }
    var previousPacketCount by remember { mutableStateOf(packetCount) }
    var targetDistance by remember { mutableStateOf(0f) }

    LaunchedEffect(packetCount) {
        val packetDelta = (packetCount - previousPacketCount).coerceAtLeast(0L)
        previousPacketCount = packetCount
        if (packetDelta == 0L) return@LaunchedEffect

        targetDistance += packetDelta.toFloat() * WALKING_ANTS_PIXELS_PER_PACKET
        animatedDistance.animateTo(
            targetValue = targetDistance,
            animationSpec = tween(
                durationMillis = WALKING_ANTS_ANIMATION_DURATION_MS,
                easing = FastOutSlowInEasing,
            ),
        )
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
        ) {
            val topY = size.height * 0.32f
            val bottomY = size.height * 0.68f
            val lineWidth = 1.dp.toPx()
            val dotRadius = 1.5.dp.toPx()
            val dotSpacing = 15.dp.toPx()
            val offset = animatedDistance.value % dotSpacing
            val lineColor = color.copy(alpha = if (connected) 0.42f else 0.24f)

            drawLine(lineColor, Offset(0f, topY), Offset(size.width, topY), lineWidth)
            drawLine(lineColor, Offset(0f, bottomY), Offset(size.width, bottomY), lineWidth)

            if (connected) {
                var x = -dotSpacing
                while (x <= size.width + dotSpacing) {
                    drawCircle(color = color, radius = dotRadius, center = Offset(x - offset, topY))
                    drawCircle(color = color, radius = dotRadius, center = Offset(x + offset, bottomY))
                    x += dotSpacing
                }
            }
        }

        Text(
            text = packetCount.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .background(surfaceColor, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EndpointMarker(
    accentColor: Color,
    active: Boolean,
) {
    val bubbleColor =
        if (active) accentColor.copy(alpha = 0.18f) else accentColor.copy(alpha = 0.12f)

    Box(
        modifier = Modifier
            .size(38.dp)
            .background(bubbleColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(accentColor, CircleShape),
        )
    }
}

@Composable
private fun ProtocolActivityIndicator(
    label: String,
    pulseTick: Long,
    active: Boolean,
    color: Color,
    reverse: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ActivityPropeller(
            pulseTick = pulseTick,
            color = color,
            active = active,
            reverse = reverse,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ProtocolActivityBadge(
    text: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
        )
    }
}

@Composable
private fun ActivityPropeller(
    pulseTick: Long,
    color: Color,
    active: Boolean,
    reverse: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val rotation = remember { Animatable(0f) }

    LaunchedEffect(pulseTick) {
        if (pulseTick == 0L) return@LaunchedEffect
        rotation.snapTo(0f)
        rotation.animateTo(
            targetValue = if (reverse) -360f else 360f,
            animationSpec = tween(durationMillis = 480, easing = FastOutSlowInEasing)
        )
    }

    val bladeColor = color.copy(alpha = if (active) 0.35f else 0.16f)
    val hubColor = color.copy(alpha = if (active) 0.65f else 0.32f)

    Canvas(
        modifier = modifier.graphicsLayer {
            rotationZ = rotation.value
        }
    ) {
        val bladeWidth = size.width * 0.18f
        val bladeHeight = size.height * 0.34f
        val bladeTop = size.height * 0.08f
        val bladeLeft = (size.width - bladeWidth) / 2f

        repeat(3) { index ->
            rotate(index * 120f) {
                drawRoundRect(
                    color = bladeColor,
                    topLeft = Offset(bladeLeft, bladeTop),
                    size = Size(bladeWidth, bladeHeight),
                    cornerRadius = CornerRadius(bladeWidth, bladeWidth)
                )
            }
        }

        drawCircle(
            color = hubColor,
            radius = size.minDimension * 0.12f
        )
        drawCircle(
            color = hubColor.copy(alpha = 0.22f),
            radius = size.minDimension * 0.2f
        )
    }
}
