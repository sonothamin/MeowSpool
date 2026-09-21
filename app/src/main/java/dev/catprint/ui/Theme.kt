package dev.catprint.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Seed = Color(0xFF7B5EA7)
private val Light = lightColorScheme(primary = Seed, secondaryContainer = Color(0xFFE9DDFF))
private val Dark = darkColorScheme(primary = Color(0xFFD0BCFF), secondaryContainer = Color(0xFF4A3F6B))

@Composable
fun MeowTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
