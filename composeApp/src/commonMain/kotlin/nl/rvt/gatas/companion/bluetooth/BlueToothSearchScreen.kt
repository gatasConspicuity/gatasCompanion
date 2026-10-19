@file:OptIn(ExperimentalUuidApi::class)

package nl.rvt.gatas.companion.bluetooth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import compose.icons.LineAwesomeIcons
import compose.icons.lineawesomeicons.Bluetooth
import nl.rvt.gatas.companion.GatasCard
import nl.rvt.gatas.companion.GaTasDevice
import kotlin.uuid.ExperimentalUuidApi

@Composable
fun BlueToothSearchScreen(
    scannerViewModel: BleScannerViewModel = viewModel { BleScannerViewModel() },

    onItemClicked: (GaTasDevice: GaTasDevice) -> Unit,
) {
    val discovery by scannerViewModel.discovery.collectAsState()

    LaunchedEffect(Unit) {
        scannerViewModel.startScanning()
    }

    DisposableEffect(Unit) {
        onDispose {
            scannerViewModel.stopScanning()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        discovery.error?.let { Text(it, modifier = Modifier.padding(16.dp)) }
        if (!discovery.scanning && discovery.devices.isEmpty()) {
            Text("Search finished. Reopen this screen to search again.", modifier = Modifier.padding(16.dp))
        }
        ListContent(
            items = discovery.devices,
            onItemClicked = onItemClicked
        )
    }
}



@Composable
private fun ListContent(
    items: List<GaTasDevice>,
    onItemClicked: (GaTasDevice: GaTasDevice) -> Unit
) {
    val listState = rememberLazyListState()

    if (items.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Scanning for devices...",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(items) { item ->
                DeviceButton(
                    item = item,
                    onClicked = { onItemClicked(item) }
                )
            }
        }
    }
}


@Composable
private fun DeviceButton(
    item: GaTasDevice,
    onClicked: () -> Unit
) {
    GatasCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onClicked)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = LineAwesomeIcons.Bluetooth,
                contentDescription = "Bluetooth",
                modifier = Modifier.size(36.dp),
                tint = LocalContentColor.current
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.identifier,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
