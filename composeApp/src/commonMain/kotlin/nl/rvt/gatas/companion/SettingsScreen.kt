package nl.rvt.gatas.companion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    gdl90BridgeEnabled: Boolean,
    onGdl90BridgeEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        GatasCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(18.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Enable GDL90 bridge", style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f).padding(end = 12.dp))
                Switch(checked = gdl90BridgeEnabled, onCheckedChange = onGdl90BridgeEnabledChanged)
            }
        }
    }
}
