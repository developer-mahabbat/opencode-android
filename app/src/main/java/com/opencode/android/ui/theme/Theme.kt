package com.opencode.android.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Dark = darkColorScheme(
    primary = Color(0xFF90CAF9), secondary = Color(0xFFA5D6A7), tertiary = Color(0xFFCE93D8),
    background = Color(0xFF0D1117), surface = Color(0xFF161B22),
    onPrimary = Color.Black, onSecondary = Color.Black, onBackground = Color.White, onSurface = Color.White,
    surfaceVariant = Color(0xFF21262D), onSurfaceVariant = Color(0xFF8B949E),
    outline = Color(0xFF30363D), primaryContainer = Color(0xFF1F6FEB)
)

private val Light = lightColorScheme(
    primary = Color(0xFF1F6FEB), secondary = Color(0xFF238636), tertiary = Color(0xFF8957E5),
    background = Color(0xFFFFFFFF), surface = Color(0xFFF6F8FA),
    onPrimary = Color.White, onSecondary = Color.White, onBackground = Color(0xFF1F2328), onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEAEEF2), onSurfaceVariant = Color(0xFF656D76),
    outline = Color(0xFFD0D7DE), primaryContainer = Color(0xFFDDF4FF)
)

@Composable
fun OpenCodeTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = colors, content = content)
}
