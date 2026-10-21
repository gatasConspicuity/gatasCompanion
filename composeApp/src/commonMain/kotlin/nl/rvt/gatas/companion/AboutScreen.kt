package nl.rvt.gatas.companion

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp

@Composable
fun AboutScreen(buildInfo: AppBuildInfo = remember { appBuildInfo() }) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GatasCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("GATAS Companion", style = MaterialTheme.typography.headlineSmall)
                Text("Version ${buildInfo.version} (Build ${buildInfo.buildNumber})",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Connect your phone or tablet to GATAS over Bluetooth and relay traffic data between GATAS and the Internet.")
                Text("Enable GDL90 forwarding for compatible EFB apps on this device, or use Phone Barometer to send air pressure to GATAS.")
            }
        }
        GatasCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Projects and license", style = MaterialTheme.typography.titleMedium)
                Text("Source code, documentation and license:")
                AboutLink("GATAS Companion on GitHub", "https://github.com/gatasConspicuity/gatasCompanion", uriHandler::openUri)
                AboutLink("GATAS on GitHub", "https://github.com/gatasConspicuity/gatas", uriHandler::openUri)
                Text("Apache License 2.0 with Commons Clause", style = MaterialTheme.typography.bodyMedium)
                AboutLink("License", "https://github.com/gatasConspicuity/gatasCompanion/blob/main/LICENSE", uriHandler::openUri)
            }
        }
    }
}

@Composable
private fun AboutLink(label: String, url: String, openUri: (String) -> Unit) {
    TextButton(onClick = { openUri(url) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(label)
    }
}
