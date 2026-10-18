@file:OptIn(ExperimentalUuidApi::class)

package nl.rvt.gatas.companion

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import co.touchlab.kermit.Logger
import compose.icons.LineAwesomeIcons
import compose.icons.lineawesomeicons.PlaneDepartureSolid
import dev.icerock.moko.permissions.DeniedAlwaysException
import dev.icerock.moko.permissions.DeniedException
import dev.icerock.moko.permissions.PermissionsController
import kotlinx.coroutines.launch
import kotlin.uuid.ExperimentalUuidApi

@Composable
fun LandingScreen(
    devices: Set<GaTasDevice>,
    deleteItem: (id: GaTasDevice) -> Unit,
    onItemClicked: (device: GaTasDevice) -> Unit = {},
    addBleDevice: (ble: GaTasDevice) -> Unit,
    onScanClicked: () -> Unit = {},
    permissionsController: PermissionsController
) {
    var pendingDeleteId by remember { mutableStateOf<GaTasDevice?>(null) }
    val handleScanClick = rememberBleScanPermissionHandler(permissionsController, onScanClicked)

    Box(modifier = Modifier.fillMaxSize().padding(bottom = 72.dp)) {
        Column(modifier = Modifier.fillMaxSize()) {
            ListContent(
                oeitems = devices.toList(),
                onItemClicked = onItemClicked,
                deleteItem = { identifier -> pendingDeleteId = identifier },
            )
            Spacer(modifier = Modifier.weight(1f)) // This will take up all available space
        }

        if (devices.isEmpty()) {
            LargeRoundButton(
                text = "Find GATAS",
                modifier = Modifier
                    .align(Alignment.Center),
                onClick = handleScanClick,
            )
        } else {
            Button(
                onClick = handleScanClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
            ) {
                Text("Find GATAS")
            }
        }
    }

    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete Device") },
            text = { Text("Are you sure you want to delete this device?") },
            confirmButton = {
                Button(onClick = {
                    deleteItem(pendingDeleteId!!)
                    pendingDeleteId = null
                }) {
                    Text("Delete")
                }
            },
            dismissButton = {
                Button(onClick = { pendingDeleteId = null }) {
                    Text("Cancel")
                }
            }
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




@Composable
private fun ListContent(
    oeitems: List<GaTasDevice>,
    onItemClicked: (device: GaTasDevice) -> Unit,
    deleteItem: (item: GaTasDevice) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize()
    ) {
        items(oeitems) { item ->
            DeviceButton(
                item = item,
                onClicked = { onItemClicked(item) },
                onDeleteClicked = { deleteItem(item) }
            )
        }
    }

}


@Composable
private fun DeviceButton(
    item: GaTasDevice,
    onClicked: () -> Unit,
    onDeleteClicked: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClicked),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.weight(1f)) // pushes icon to the right

            Icon(
                imageVector = LineAwesomeIcons.PlaneDepartureSolid,
                contentDescription = "Plug",
                modifier = Modifier.size(42.dp),
                tint = LocalContentColor.current
            )
            TextButton(onClick = onDeleteClicked) {
                Text("Delete")
            }
        }
    }
}
