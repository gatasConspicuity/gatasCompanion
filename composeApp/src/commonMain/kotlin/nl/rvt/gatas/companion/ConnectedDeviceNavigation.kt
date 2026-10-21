package nl.rvt.gatas.companion

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import compose.icons.LineAwesomeIcons
import compose.icons.lineawesomeicons.CogSolid
import compose.icons.lineawesomeicons.StopSolid
import compose.icons.lineawesomeicons.ToolsSolid

@Composable
fun ConnectedDeviceNavigation(onSettings: () -> Unit, onActions: () -> Unit, onStop: () -> Unit) {
    NavigationBar(windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)) {
        NavigationBarItem(
            selected = false, onClick = onSettings,
            icon = { Icon(LineAwesomeIcons.CogSolid, contentDescription = null) },
            label = { Text("Settings", modifier = Modifier.clearAndSetSemantics {}) },
            modifier = Modifier.semantics { contentDescription = "GATAS Settings" },
        )
        NavigationBarItem(
            selected = false, onClick = onActions,
            icon = { Icon(LineAwesomeIcons.ToolsSolid, contentDescription = null) },
            label = { Text("Actions", modifier = Modifier.clearAndSetSemantics {}) },
            modifier = Modifier.semantics { contentDescription = "Device Actions" },
        )
        NavigationBarItem(
            selected = false, onClick = onStop,
            icon = { Icon(LineAwesomeIcons.StopSolid, contentDescription = null) },
            label = { Text("STOP", modifier = Modifier.clearAndSetSemantics {}) },
            modifier = Modifier.semantics { contentDescription = "Stop bridge and return Home" },
            colors = NavigationBarItemDefaults.colors(
                unselectedIconColor = MaterialTheme.colorScheme.error,
                unselectedTextColor = MaterialTheme.colorScheme.error,
            ),
        )
    }
}
