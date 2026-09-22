package dev.meowspool.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
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
    SAMSUNG_SANS("Samsung Sans", false),
    DEFAULT("Default", false);

    companion object {
        /** Falls back to Samsung Sans on Samsung hardware (when it's actually fetched), Default elsewhere,
         * so Samsung phones get their own look right from onboarding rather than needing a menu visit. */
        fun fromPref(): UiFont {
            values().firstOrNull { it.name == Prefs.uiFont }?.let { return it }
            return if (android.os.Build.MANUFACTURER.equals("samsung", ignoreCase = true)) SAMSUNG_SANS else DEFAULT
        }
    }
}

private fun assetPath(f: UiFont): String? = when (f) {
    UiFont.NDOT -> "fonts/ndot.otf"
    UiFont.NTYPE -> "fonts/ntype.otf"
    UiFont.SAMSUNG_SANS -> "fonts/samsungsans.ttf"
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
    UiFont.NDOT, UiFont.NTYPE, UiFont.SAMSUNG_SANS -> assetPath(f)?.let { assetFamily(ctx, it) }
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

/** The "Pixel" look: Google's own apps (Settings, Phone, Pixel Launcher) sit on the more
 * generous end of the official M3 Expressive shape scale rather than the conservative 4/8/12/16/28dp defaults.
 * Every value below is a real M3 token (see the shape-corner table), just the roomier sibling of each role:
 * small→extra-small-of-old, medium/large use the "increased" expressive steps, extraLarge stays the spec max. */
private val PixelShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp), // large-increased
    extraLarge = RoundedCornerShape(28.dp),
)

/** M3's legacy easing/duration system, still the spec for screen-level enter/exit transitions
 * (component motion uses spring physics instead — see MaterialTheme.motionScheme). */
object MotionTokens {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f) // enters the screen, 400ms
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f) // exits the screen, 200ms
    const val DurationEnter = 400
    const val DurationExit = 200
}

/** [mode]: 0 system, 1 light, 2 dark. Dynamic (wallpaper) colour on Android 12+ when [dynamic].
 * [amoled] flattens dark-mode backgrounds/surfaces to true black (OLED power saving, no grey haze). [font] picks the UI typeface. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MeowSpoolTheme(mode: Int, dynamic: Boolean, font: UiFont = UiFont.DEFAULT, amoled: Boolean = false, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    var scheme = when {
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    if (dark && amoled) scheme = scheme.copy(
        background = Color.Black, surface = Color.Black,
        surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF0F0F0F), surfaceContainerHigh = Color(0xFF161616), surfaceContainerHighest = Color(0xFF1C1C1C),
    )
    // If a picked font's asset never fetched (e.g. built offline), fall back to Default rather than crash.
    val heading = remember(font) { headingFamily(ctx, font) }
    val body = remember(heading, font.titlesOnly) { if (font.titlesOnly) InterFamily else heading }
    val typography = remember(heading, body) { typographyFor(heading, body) }
    // Expressive theme: spring-based MotionScheme.expressive() instead of the static utility easing curves —
    // this is what gives buttons/FABs/switches their "bounce" rather than a flat linear move.
    MaterialExpressiveTheme(colorScheme = scheme, typography = typography, shapes = PixelShapes, motionScheme = MotionScheme.expressive(), content = content)
}
