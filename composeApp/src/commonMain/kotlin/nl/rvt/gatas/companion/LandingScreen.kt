package nl.rvt.gatas.companion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import dev.icerock.moko.permissions.DeniedAlwaysException
import dev.icerock.moko.permissions.DeniedException
import dev.icerock.moko.permissions.PermissionsController
import kotlinx.coroutines.launch

@Composable
fun LandingScreen(
    devices: Set<GaTasDevice>,
    deleteItem: (GaTasDevice) -> Unit,
    onItemClicked: (GaTasDevice) -> Unit,
    onScanClicked: () -> Unit,
    permissionsController: PermissionsController,
) {
    SavedDevicesContent(devices, deleteItem, onItemClicked,
        rememberBleScanPermissionHandler(permissionsController, onScanClicked))
}

@Composable
fun SavedDevicesContent(
    devices: Set<GaTasDevice>,
    deleteItem: (GaTasDevice) -> Unit,
    onItemClicked: (GaTasDevice) -> Unit,
    onScanClicked: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<GaTasDevice?>(null) }
    Column(Modifier.fillMaxSize()) {
        if (devices.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("No saved devices. Find your GATAS to get started.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(devices.toList(), key = { it.identifier }) { device ->
                    GatasCard(Modifier.fillMaxWidth().clickable(role = Role.Button,
                        onClickLabel = "Connect to ${device.name}", onClick = { onItemClicked(device) })) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(device.name, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f).padding(4.dp))
                            TextButton(
                                onClick = { pendingDelete = device },
                                modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text("Delete") }
                        }
                    }
                }
            }
        }
        Button(
            onClick = onScanClicked,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth().heightIn(min = 48.dp),
            shape = MaterialTheme.shapes.medium,
        ) { Text("Find GATAS") }
    }
    pendingDelete?.let { device ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete Device") },
            text = { Text("Are you sure you want to delete this device?") },
            confirmButton = {
                TextButton(
                    onClick = { deleteItem(device); pendingDelete = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
fun rememberBleScanPermissionHandler(
    permissionsController: PermissionsController,
    onScanClicked: () -> Unit
): () -> Unit {
    val coroutineScope = rememberCoroutineScope()

    return {
        coroutineScope.launch {
            try {
                prepareForBleScan(permissionsController)
                onScanClicked()
            } catch (deniedAlways: DeniedAlwaysException) {
                Logger.i { "Permission permanently denied, ${deniedAlways.cause}" }
            } catch (denied: DeniedException) {
                Logger.i { "User did not gave access, ${denied.cause}" }
            }
        }
    }
}
