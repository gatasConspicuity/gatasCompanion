@file:OptIn(ExperimentalFoundationApi::class)

package nl.rvt.gatas.companion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

@Composable
fun ConnectScreen(state: CompanionState, onConnectionInfo: () -> Unit, onGatasDetails: (String) -> Unit) {
    val bridgeStatus = state.bridge
    var showAircraftDialog by remember { mutableStateOf(false) }
    val ownshipConfiguration = bridgeStatus.ownshipConfiguration

    LaunchedEffect(showAircraftDialog) {
        if (showAircraftDialog) CompanionController.refreshAircraft()
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DeviceInfoCard(
            gatasHexCode = ownshipConfiguration?.gatasId?.toHexId() ?: UNAVAILABLE_GATAS_ID,
            bluetoothName = state.activeDevice?.name ?: "Unavailable",
            selectedAircraftCallsign = currentSelectedAircraftCallsign(ownshipConfiguration, state.aircraft),
            onOpenGatasDetails = onGatasDetails,
            onChangeAircraft = { showAircraftDialog = true },
            changeAircraftEnabled = ownshipConfiguration != null && bridgeStatus.deviceControl.operation?.busy != true,
        )
        ConnectionSummaryCard(bridgeStatus, onConnectionInfo)
    }

    if (showAircraftDialog) {
        AircraftSelectionDialog(
            entries = state.aircraft,
            loading = state.aircraftLoading,
            loadError = state.aircraftError,
            selectedIcaoAddress = ownshipConfiguration?.icaoAddress,
            pendingIcaoAddress = bridgeStatus.aircraftChangeTargetIcaoAddress,
            onSelectAircraft = CompanionController::changeAircraft,
            onDismiss = { showAircraftDialog = false },
        )
    }
}

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
fun GatasDetailsScreen(hexCode: String) {
    EmbeddedWebView(
        url = "https://gatas.vantwisk.nl/#$hexCode",
        modifier = Modifier.fillMaxSize(),
    )
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
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                    OutlinedButton(onClick = onDismiss, shape = MaterialTheme.shapes.medium) {
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
    val backgroundTint = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
    val borderColor = when {
        pending -> MaterialTheme.colorScheme.primary
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor,
                shape = MaterialTheme.shapes.large
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = backgroundTint),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
