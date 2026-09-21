package dev.meowspool.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font as GFont
import dev.meowspool.Prefs

private val Light = lightColorScheme(primary = Color(0xFF7B5EA7), secondaryContainer = Color(0xFFE9DDFF))
private val Dark = darkColorScheme(primary = Color(0xFFD0BCFF), secondaryContainer = Color(0xFF4A3F6B))

/** UI typeface choices offered in Appearance. Named fonts are fetched on-device via Google Play services (Downloadable Fonts); no font files are bundled. */
enum class UiFont(val label: String, private val googleName: String?) {
    DEFAULT("Default", null),
    INTER("Inter", "Inter"),
    GOOGLE_SANS("Google Sans", "Google Sans Flex");

    fun family(): FontFamily? = googleName?.let { name ->
        val g = GoogleFont(name)
        FontFamily(GFont(g, weight = FontWeight.Normal), GFont(g, weight = FontWeight.Medium), GFont(g, weight = FontWeight.Bold))
    }

    companion object {
        fun fromPref(): UiFont = entries.firstOrNull { it.name == Prefs.uiFont } ?: DEFAULT
    }
}

private fun typographyFor(family: FontFamily?): Typography {
    if (family == null) return Typography()
    val b = Typography()
    fun androidx.compose.ui.text.TextStyle.f() = copy(fontFamily = family)
    return Typography(
        displayLarge = b.displayLarge.f(), displayMedium = b.displayMedium.f(), displaySmall = b.displaySmall.f(),
        headlineLarge = b.headlineLarge.f(), headlineMedium = b.headlineMedium.f(), headlineSmall = b.headlineSmall.f(),
        titleLarge = b.titleLarge.f(), titleMedium = b.titleMedium.f(), titleSmall = b.titleSmall.f(),
        bodyLarge = b.bodyLarge.f(), bodyMedium = b.bodyMedium.f(), bodySmall = b.bodySmall.f(),
        labelLarge = b.labelLarge.f(), labelMedium = b.labelMedium.f(), labelSmall = b.labelSmall.f(),
    )
}

/** [mode]: 0 system, 1 light, 2 dark. Dynamic (wallpaper) colour on Android 12+ when [dynamic]. [font] picks the UI typeface. */
@Composable
fun MeowSpoolTheme(mode: Int, dynamic: Boolean, font: UiFont = UiFont.DEFAULT, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    val typography = remember(font) { typographyFor(font.family()) }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
