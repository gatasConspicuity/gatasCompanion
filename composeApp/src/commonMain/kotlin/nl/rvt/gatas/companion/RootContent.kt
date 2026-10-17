package nl.rvt.gatas.companion


import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.LineAwesomeIcons
import compose.icons.lineawesomeicons.CogSolid
import compose.icons.lineawesomeicons.HomeSolid
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.compose.BindEffect
import nl.rvt.gatas.companion.RootStore.Screen
import nl.rvt.gatas.companion.bluetooth.BlueToothSearchScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RootContent(
    modifier: Modifier = Modifier,
    permissionsController: PermissionsController
) {
    val model = remember { RootStore() }
    val state by CompanionController.state.collectAsState()
    val screen = model.screen
    val bridgeStatus = state.bridge
    val activeDevice = state.activeDevice

    // Required to bind permissions lifecycle to the activity
    BindEffect(permissionsController)


    LaunchedEffect(activeDevice) {
        if (activeDevice != null) model.showConnected()
        else if (model.screen == Screen.Connected) model.landing()
    }

    val screenTitle = when (screen) {
        Screen.Landing -> "GATAS Companion"
        Screen.Connected -> "Connection Info"
        Screen.BlueTooth -> "Find GATAS"
        Screen.Settings -> "Settings"
    }

    AppTheme {
        state.actionError?.let { error ->
            AlertDialog(
                onDismissRequest = CompanionController::dismissActionError,
                title = { Text("Device changed") },
                text = { Text(error) },
                confirmButton = { TextButton(onClick = CompanionController::dismissActionError) { Text("OK") } },
            )
        }
        Scaffold(
            modifier = modifier,
            bottomBar = {
                if (screen != Screen.Connected) {
                    NavigationBar {
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = LineAwesomeIcons.HomeSolid,
                                    contentDescription = "Home",
                                )
                            },
                            selected = screen == Screen.Landing,
                            onClick = model::landing,
                            label = { Text("Home") }
                        )
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = LineAwesomeIcons.CogSolid,
                                    contentDescription = "Settings",
                                )
                            },
                            selected = screen == Screen.Settings,
                            onClick = model::settings,
                            label = { Text("Settings") }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Text(
                    text = screenTitle,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(vertical = 16.dp).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                // Android ends its foreground service after USB boot. Keep the
                // terminal instruction visible on Home after that transition.
                if (screen != Screen.Connected) {
                    bridgeStatus.deviceControl.operation?.takeUnless { it.busy }?.let { operation ->
                        Text(operation.message, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.Landing -> LandingScreen(
                            devices = state.devices,
                            deleteItem = CompanionController::deleteDevice,
                            onItemClicked = model::connected,
                            onScanClicked = model::blueTooth,
                            addBleDevice = {},
                            permissionsController = permissionsController
                        )

                        Screen.BlueTooth -> {

                            BlueToothSearchScreen(
                                onClose = model::landing,
                                onItemClicked = {
                                    CompanionController.saveDevice(it)
                                    model.landing()
                                },
                            )
                        }

                        Screen.Settings -> SettingsScreen(
                            gdl90BridgeEnabled = state.gdl90Enabled,
                            onGdl90BridgeEnabledChanged = CompanionController::setGdl90Enabled,
                        )

                        Screen.Connected -> ConnectScreen(
                            bridgeStatus = bridgeStatus,
                            onBack = model::landing,
                            onStopBridge = CompanionController::stop,
                        )
                    }
                }
            }
        }
    }
}
