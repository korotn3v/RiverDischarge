package com.example.riverdischarge.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Запасная «речная» палитра — используется на Android < 12 или если динамический цвет выключен.
private val RiverLightColors = lightColorScheme(
    primary = Color(0xFF1660A6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD2E4FF),
    onPrimaryContainer = Color(0xFF001C39),
    secondary = Color(0xFF00696E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF8FF2F8),
    onSecondaryContainer = Color(0xFF002022),
    tertiary = Color(0xFF2A6A45),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFADF2C2),
    onTertiaryContainer = Color(0xFF002112),
    background = Color(0xFFF8FAFD),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF8FAFD),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF42474E),
    outline = Color(0xFF72777F)
)

private val RiverDarkColors = darkColorScheme(
    primary = Color(0xFF9FCAFF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF004880),
    onPrimaryContainer = Color(0xFFD2E4FF),
    secondary = Color(0xFF4DD9E0),
    onSecondary = Color(0xFF00373A),
    secondaryContainer = Color(0xFF004F53),
    onSecondaryContainer = Color(0xFF8FF2F8),
    tertiary = Color(0xFF92D5A7),
    onTertiary = Color(0xFF00391E),
    tertiaryContainer = Color(0xFF09512E),
    onTertiaryContainer = Color(0xFFADF2C2),
    background = Color(0xFF111416),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF42474E),
    onSurfaceVariant = Color(0xFFC2C7CE),
    outline = Color(0xFF8C9199)
)

@Composable
fun RiverDischargeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> RiverDarkColors
        else -> RiverLightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
