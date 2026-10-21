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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import compose.icons.LineAwesomeIcons
import compose.icons.lineawesomeicons.CogSolid
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
            icon = { Icon(StopSign, contentDescription = null, tint = Color.Unspecified) },
            label = { Text("STOP", modifier = Modifier.clearAndSetSemantics {}) },
            modifier = Modifier.semantics { contentDescription = "Stop bridge and return Home" },
            colors = NavigationBarItemDefaults.colors(unselectedTextColor = MaterialTheme.colorScheme.error),
        )
    }
}

private val StopSign = ImageVector.Builder(
    name = "Stop sign", defaultWidth = 32.dp, defaultHeight = 32.dp, viewportWidth = 24f, viewportHeight = 24f,
).apply {
    path(fill = SolidColor(GatasDanger), stroke = SolidColor(Color.White), strokeLineWidth = 0.7f) {
        moveTo(7.2f, 1.5f)
        lineTo(16.8f, 1.5f)
        lineTo(22.5f, 7.2f)
        lineTo(22.5f, 16.8f)
        lineTo(16.8f, 22.5f)
        lineTo(7.2f, 22.5f)
        lineTo(1.5f, 16.8f)
        lineTo(1.5f, 7.2f)
        close()
    }
    // Letter paths keep the sign legible as a single scalable graphic.
    path(stroke = SolidColor(Color.White), strokeLineWidth = 0.9f,
        strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
        moveTo(7.3f, 9.3f)
        horizontalLineTo(5.5f)
        curveTo(4.3f, 9.3f, 4.3f, 12f, 5.5f, 12f)
        horizontalLineTo(6.3f)
        curveTo(7.5f, 12f, 7.5f, 14.7f, 6.3f, 14.7f)
        horizontalLineTo(4.5f)
        moveTo(8.2f, 9.3f)
        horizontalLineTo(11.4f)
        moveTo(9.8f, 9.3f)
        verticalLineTo(14.7f)
        moveTo(13.6f, 9.3f)
        curveTo(12.1f, 9.3f, 12.1f, 14.7f, 13.6f, 14.7f)
        curveTo(15.1f, 14.7f, 15.1f, 9.3f, 13.6f, 9.3f)
        close()
        moveTo(16.6f, 14.7f)
        verticalLineTo(9.3f)
        horizontalLineTo(18.1f)
        curveTo(19.9f, 9.3f, 19.9f, 12f, 18.1f, 12f)
        horizontalLineTo(16.6f)
    }
}.build()
