package dev.meowspool.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import dev.meowspool.Prefs
import dev.meowspool.R

private val Light = lightColorScheme(primary = Color(0xFF7B5EA7), secondaryContainer = Color(0xFFE9DDFF))
private val Dark = darkColorScheme(primary = Color(0xFFD0BCFF), secondaryContainer = Color(0xFF4A3F6B))

// Inter and Google Sans Flex are bundled as regular resources (both OFL-licensed).
private val InterFamily = FontFamily(Font(R.font.inter_regular), Font(R.font.inter_medium, FontWeight.Medium))
private val GoogleSansFamily = FontFamily(
    Font(R.font.gsf_regular), Font(R.font.gsf_medium, FontWeight.Medium), Font(R.font.gsf_bold, FontWeight.Bold),
)

/**
 * UI typeface choices offered in Appearance. Ndot and NType are Nothing's own branded fonts; rather than
 * committing "All Rights Reserved" binaries to this repo, they're fetched into assets/fonts at build time
 * (see app/build.gradle.kts) and only offered here — via [isAvailable] — when that fetch actually succeeded.
 * They're display faces, so when picked they dress titles/headings only; body text stays on Inter for legibility.
 */
enum class UiFont(val label: String, val titlesOnly: Boolean) {
    NDOT("Ndot", true),
    NTYPE("Ntype", true),
    INTER("Inter", false),
    GOOGLE_SANS("Google Sans", false),
    DEFAULT("Default", false);

    companion object {
        fun fromPref(): UiFont = values().firstOrNull { it.name == Prefs.uiFont } ?: DEFAULT
    }
}

private fun assetPath(f: UiFont): String? = when (f) {
    UiFont.NDOT -> "fonts/ndot.otf"
    UiFont.NTYPE -> "fonts/ntype.otf"
    else -> null
}

private fun assetFamily(ctx: Context, path: String): FontFamily? = try {
    ctx.assets.open(path).close() // throws if the fetch task never produced this file
    FontFamily(Typeface.createFromAsset(ctx.assets, path))
} catch (e: Exception) { null }

/** Whether this font can actually be offered right now. Always true except Ndot/NType, which need their fetched asset present. */
@Composable
fun UiFont.isAvailable(): Boolean {
    val path = assetPath(this) ?: return true
    val ctx = LocalContext.current
    return remember(path) { assetFamily(ctx, path) != null }
}

private fun headingFamily(ctx: Context, f: UiFont): FontFamily? = when (f) {
    UiFont.DEFAULT -> null
    UiFont.INTER -> InterFamily
    UiFont.GOOGLE_SANS -> GoogleSansFamily
    UiFont.NDOT, UiFont.NTYPE -> assetPath(f)?.let { assetFamily(ctx, it) }
}

private fun typographyFor(heading: FontFamily?, body: FontFamily?): Typography {
    if (heading == null) return Typography()
    val base = Typography()
    fun TextStyle.h() = copy(fontFamily = heading)
    fun TextStyle.b() = copy(fontFamily = body)
    return Typography(
        displayLarge = base.displayLarge.h(), displayMedium = base.displayMedium.h(), displaySmall = base.displaySmall.h(),
        headlineLarge = base.headlineLarge.h(), headlineMedium = base.headlineMedium.h(), headlineSmall = base.headlineSmall.h(),
        titleLarge = base.titleLarge.h(), titleMedium = base.titleMedium.h(), titleSmall = base.titleSmall.h(),
        bodyLarge = base.bodyLarge.b(), bodyMedium = base.bodyMedium.b(), bodySmall = base.bodySmall.b(),
        labelLarge = base.labelLarge.b(), labelMedium = base.labelMedium.b(), labelSmall = base.labelSmall.b(),
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
    // If a picked font's asset never fetched (e.g. built offline), fall back to Default rather than crash.
    val heading = remember(font) { headingFamily(ctx, font) }
    val body = remember(heading, font.titlesOnly) { if (font.titlesOnly) InterFamily else heading }
    val typography = remember(heading, body) { typographyFor(heading, body) }
    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
