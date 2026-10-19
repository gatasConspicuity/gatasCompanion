package nl.rvt.gatas.companion

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.LineAwesomeIcons
import compose.icons.lineawesomeicons.CogSolid
import gatascompanion.composeapp.generated.resources.Res
import gatascompanion.composeapp.generated.resources.gatas_dark
import org.jetbrains.compose.resources.painterResource

@Composable
fun GatasCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        content = content,
    )
}

@Composable
fun GatasHeader(
    title: String? = null,
    modifier: Modifier = Modifier,
    onSettings: (() -> Unit)? = null,
    onStop: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
) {
    Surface(color = GatasNavy, contentColor = Color.White) {
        Row(
            modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (title == null) {
                if (onSettings != null) {
                    Box(Modifier.width(72.dp)) {
                        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
                            Icon(LineAwesomeIcons.CogSolid, contentDescription = "GATAS Settings")
                        }
                    }
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(Res.drawable.gatas_dark),
                        contentDescription = "GATAS",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.width(if (onStop == null) 240.dp else 200.dp).height(52.dp),
                    )
                }
            } else {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            if (onStop != null) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.widthIn(min = 72.dp).heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GatasDanger, contentColor = Color.White),
                ) { Text("STOP", textAlign = TextAlign.Center) }
            }
            if (onBack != null) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                ) { Text("Back") }
            }
        }
    }
}
