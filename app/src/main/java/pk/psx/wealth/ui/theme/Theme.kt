package pk.psx.wealth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import pk.psx.wealth.data.preferences.ThemePreference

private val Light = lightColorScheme(
    primary = Color(0xFF075E45),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F5E6),
    onPrimaryContainer = Color(0xFF003829),
    secondary = Color(0xFF3E6658),
    secondaryContainer = Color(0xFFD8EDE4),
    tertiary = Color(0xFF8A6810),
    tertiaryContainer = Color(0xFFFFE9A9),
    background = Color(0xFFF7FAF7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EFEA),
    error = Color(0xFFB3261E),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF84D9B4),
    onPrimary = Color(0xFF003829),
    primaryContainer = Color(0xFF00513A),
    onPrimaryContainer = Color(0xFFA3F2CE),
    secondary = Color(0xFFB7CCC1),
    tertiary = Color(0xFFECCB67),
    tertiaryContainer = Color(0xFF5F4A00),
    background = Color(0xFF101512),
    surface = Color(0xFF171D19),
    surfaceVariant = Color(0xFF25312B),
    error = Color(0xFFFFB4AB),
)

private val BaseTypography = Typography()
private val AppTypography = Typography(
    headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)
private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable fun PsxTheme(theme: ThemePreference = ThemePreference.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (theme) {
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
    }
    MaterialTheme(
        colorScheme = if (dark) Dark else Light,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
