package pk.psx.wealth.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(primary = Color(0xFF196B45), secondary = Color(0xFF4E6355), background = Color(0xFFF7F9F4), surface = Color.White, error = Color(0xFFBA1A1A))
private val Dark = darkColorScheme(primary = Color(0xFF8ED5AD), secondary = Color(0xFFB5CCBC))

@Composable fun PsxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Typography(), content = content)
}

