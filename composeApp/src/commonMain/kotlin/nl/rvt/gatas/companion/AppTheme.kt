package nl.rvt.gatas.companion

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

val GatasNavy = Color(0xFF0B2239)
val GatasBlue = Color(0xFF1494DF)
val GatasDanger = Color(0xFFC73B45)

// Darker button blue preserves contrast with white labels; GatasBlue is the brand accent.
private val LightColors = lightColorScheme(
    primary = Color(0xFF0878B5), onPrimary = Color.White,
    primaryContainer = Color(0xFFDDEFFC), onPrimaryContainer = GatasNavy,
    secondary = GatasNavy, onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDEFFC), onSecondaryContainer = GatasNavy,
    background = Color(0xFFF5F8FB), onBackground = GatasNavy,
    surface = Color(0xFFF9FCFE), onSurface = GatasNavy,
    surfaceContainer = Color(0xFFEFF5F9), surfaceContainerLow = Color(0xFFF2F8FC),
    surfaceContainerHighest = Color(0xFFE8F2F8), onSurfaceVariant = Color(0xFF526575),
    outline = Color(0xFF788C9C), outlineVariant = Color(0xFFCFDEE8),
    error = GatasDanger, onError = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF55C2FF), onPrimary = GatasNavy,
    primaryContainer = Color(0xFF143B56), onPrimaryContainer = Color(0xFFDDEFFC),
    secondary = Color(0xFF91D6FF), onSecondary = GatasNavy,
    secondaryContainer = Color(0xFF143B56), onSecondaryContainer = Color(0xFFDDEFFC),
    background = Color(0xFF0C1722), onBackground = Color(0xFFE4EDF4),
    surface = Color(0xFF111F2D), onSurface = Color(0xFFE4EDF4),
    surfaceContainer = Color(0xFF142534), surfaceContainerLow = Color(0xFF152838),
    surfaceContainerHighest = Color(0xFF203748), onSurfaceVariant = Color(0xFFADBFCC),
    outline = Color(0xFF8197A7), outlineVariant = Color(0xFF304B60),
    error = Color(0xFFFF929B), onError = Color(0xFF4A1018),
)

@Composable
fun gatasSuccessColor(): Color = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF36D399) else Color(0xFF16845B)

@Composable
fun AppTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(16.dp),
            extraLarge = RoundedCornerShape(16.dp),
        ),
        content = content,
    )
}
