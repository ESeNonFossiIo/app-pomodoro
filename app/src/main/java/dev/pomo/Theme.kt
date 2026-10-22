package dev.pomo

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Black = Color(0xFF000000)
private val White = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = Black, onPrimary = White,
    primaryContainer = Color(0xFFE6E6E6), onPrimaryContainer = Black,
    secondary = Color(0xFF3A3A3A), onSecondary = White,
    secondaryContainer = Color(0xFFE0E0E0), onSecondaryContainer = Black,
    tertiary = Color(0xFF3A3A3A), onTertiary = White,
    background = White, onBackground = Black,
    surface = White, onSurface = Black, surfaceTint = Black,
    surfaceVariant = Color(0xFFEDEDED), onSurfaceVariant = Color(0xFF4A4A4A),
    surfaceContainerLowest = White, surfaceContainerLow = Color(0xFFF5F5F5),
    surfaceContainer = Color(0xFFF0F0F0), surfaceContainerHigh = Color(0xFFEBEBEB),
    surfaceContainerHighest = Color(0xFFE6E6E6),
    outline = Color(0xFF7A7A7A), outlineVariant = Color(0xFFCFCFCF),
)

private val DarkColors = darkColorScheme(
    primary = White, onPrimary = Black,
    primaryContainer = Color(0xFF2B2B2B), onPrimaryContainer = White,
    secondary = Color(0xFFCFCFCF), onSecondary = Black,
    secondaryContainer = Color(0xFF333333), onSecondaryContainer = White,
    tertiary = Color(0xFFCFCFCF), onTertiary = Black,
    background = Black, onBackground = White,
    surface = Black, onSurface = White, surfaceTint = White,
    surfaceVariant = Color(0xFF262626), onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainerLowest = Black, surfaceContainerLow = Color(0xFF0F0F0F),
    surfaceContainer = Color(0xFF161616), surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF262626),
    outline = Color(0xFF8A8A8A), outlineVariant = Color(0xFF3A3A3A),
)

enum class ThemeMode { COLOR, MONO }

/** Both modes follow the system light/dark setting. */
@Composable
fun PomoTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = when {
        mode == ThemeMode.MONO && dark -> DarkColors
        mode == ThemeMode.MONO -> LightColors
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
