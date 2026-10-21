package nl.rvt.gatas.companion

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import compose.icons.LineAwesomeIcons
import compose.icons.lineawesomeicons.CogSolid
import compose.icons.lineawesomeicons.HomeSolid
import compose.icons.lineawesomeicons.InfoCircleSolid
import dev.icerock.moko.permissions.PermissionsController
import dev.icerock.moko.permissions.compose.BindEffect
import nl.rvt.gatas.PlatformKeepScreenOnEffect
import nl.rvt.gatas.companion.RootStore.Screen
import nl.rvt.gatas.companion.bluetooth.BlueToothSearchScreen

@Composable
fun RootContent(modifier: Modifier = Modifier, permissionsController: PermissionsController) {
    val model = remember { RootStore() }
    val state by CompanionController.state.collectAsState()
    val screen = model.screen
    var showGatasSettings by remember(state.activeDevice?.identifier) { mutableStateOf(false) }

    BindEffect(permissionsController)
    LaunchedEffect(state.activeDevice?.identifier) {
        model.activeDeviceChanged(state.activeDevice?.identifier)
    }
    PlatformBackHandler(enabled = screen != Screen.Landing && screen != Screen.Connected, onBack = model::back)
    if (screen.isConnected) PlatformKeepScreenOnEffect()

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
            contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            topBar = {
                GatasHeader(
                    title = when (screen) {
                        Screen.Landing, Screen.Connected -> null
                        Screen.ConnectionInfo -> "Connection Info"
                        Screen.GatasDetails -> "GATAS Details"
                        Screen.BlueTooth -> "Find GATAS"
                        Screen.Settings -> "Settings"
                        Screen.About -> "About"
                    },
                    modifier = Modifier.windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                    ),
                    onBack = if (screen == Screen.ConnectionInfo || screen == Screen.GatasDetails || screen == Screen.BlueTooth)
                        model::back else null,
                )
            },
            bottomBar = {
                if (screen.isConnected) {
                    DeviceActionsMenu(state.bridge, state.activeDevice?.identifier) { onOpenActions ->
                        Column {
                            DeviceActionFeedback(state.bridge, Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)
                            ).padding(horizontal = 16.dp, vertical = 8.dp))
                            ConnectedDeviceNavigation(
                                onSettings = { showGatasSettings = true },
                                onActions = onOpenActions,
                                onStop = { CompanionController.stop(); model.landing() },
                            )
                        }
                    }
                } else {
                    GatasNavigation(screen, model::landing, model::settings, model::about)
                }
            },
        ) { innerPadding ->
            Column(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)) {
                if (!screen.isConnected) {
                    state.bridge.deviceControl.operation?.takeUnless { it.busy }?.let {
                        Text(it.message, Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
                Box(Modifier.weight(1f)) {
                    when (screen) {
                        Screen.Landing -> LandingScreen(
                            devices = state.devices,
                            deleteItem = CompanionController::deleteDevice,
                            onItemClicked = model::connected,
                            onScanClicked = model::blueTooth,
                            permissionsController = permissionsController,
                        )
                        Screen.BlueTooth -> BlueToothSearchScreen(
                            onItemClicked = { CompanionController.saveDevice(it); model.landing() },
                        )
                        Screen.Settings -> SettingsScreen(state.gdl90Enabled, CompanionController::setGdl90Enabled)
                        Screen.About -> AboutScreen()
                        Screen.Connected -> ConnectScreen(state, onConnectionInfo = model::connectionInfo,
                            onGatasDetails = model::gatasDetails)
                        Screen.ConnectionInfo -> ConnectionDetailsScreen(state.bridge, state.activeDevice)
                        Screen.GatasDetails -> model.gatasDetailsId?.let { GatasDetailsScreen(it) }
                    }
                }
            }
        }
        if (showGatasSettings && screen.isConnected) {
            GatasSettingsDialog(state.bridge, state.activeDevice, CompanionController::changeWifiMode,
                onDismiss = { showGatasSettings = false })
        }
    }
}

@Composable
fun GatasNavigation(screen: Screen, onHome: () -> Unit, onSettings: () -> Unit, onAbout: () -> Unit) {
    NavigationBar(windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)) {
        NavigationBarItem(
            icon = { Icon(LineAwesomeIcons.HomeSolid, contentDescription = null) },
            selected = screen == Screen.Landing, onClick = onHome, label = { Text("Home") },
        )
        NavigationBarItem(
            icon = { Icon(LineAwesomeIcons.CogSolid, contentDescription = null) },
            selected = screen == Screen.Settings, onClick = onSettings, label = { Text("Settings") },
        )
        NavigationBarItem(
            icon = { Icon(LineAwesomeIcons.InfoCircleSolid, contentDescription = null) },
            selected = screen == Screen.About, onClick = onAbout, label = { Text("About") },
        )
    }
}
