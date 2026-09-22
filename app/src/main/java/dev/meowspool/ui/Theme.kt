package dev.meowspool.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
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

// One UI style's own faces, fetched the same way as Ndot/NType above (see fetchSamsungFonts in app/build.gradle.kts).
private fun samsungSansFamily(ctx: Context): FontFamily? = assetFamily(ctx, "fonts/samsungsans.ttf")
private fun samsungOneFamily(ctx: Context): FontFamily? = assetFamily(ctx, "fonts/samsungone.ttf")

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

private fun typographyFor(heading: FontFamily?, body: FontFamily?, boldHeadings: Boolean = false): Typography {
    if (heading == null) return Typography()
    val base = Typography()
    fun TextStyle.h() = copy(fontFamily = heading, fontWeight = if (boldHeadings) FontWeight.Bold else fontWeight)
    fun TextStyle.b() = copy(fontFamily = body)
    return Typography(
        displayLarge = base.displayLarge.h(), displayMedium = base.displayMedium.h(), displaySmall = base.displaySmall.h(),
        headlineLarge = base.headlineLarge.h(), headlineMedium = base.headlineMedium.h(), headlineSmall = base.headlineSmall.h(),
        titleLarge = base.titleLarge.h(), titleMedium = base.titleMedium.h(), titleSmall = base.titleSmall.h(),
        bodyLarge = base.bodyLarge.b(), bodyMedium = base.bodyMedium.b(), bodySmall = base.bodySmall.b(),
        labelLarge = base.labelLarge.b(), labelMedium = base.labelMedium.b(), labelSmall = base.labelSmall.b(),
    )
}

/** "Material" is the normal opaque M3 look. "Glass" makes card/container surfaces translucent so the
 * soft gradient backdrop shows through, for a frosted-glass feel. "One UI" follows Samsung's One UI
 * design guide: big bold left-aligned titles (SamsungSans), SamsungOne body text, large 26/20/12dp
 * rounded corners, and generous 24dp margins. */
enum class UiStyle { MATERIAL, GLASS, ONE_UI;
    companion object { fun fromPref(v: String) = when (v) { "glass" -> GLASS; "oneui" -> ONE_UI; else -> MATERIAL } }
}

private val OneUiLight = lightColorScheme(primary = Color(0xFF1259A8), secondaryContainer = Color(0xFFD8E7FA))
private val OneUiDark = darkColorScheme(primary = Color(0xFF63A6ED), secondaryContainer = Color(0xFF1C3A57))

/** One UI's characteristic oversized rounded corners: 26dp for hero/large surfaces, 20dp for cards, 12dp for small controls. */
private val OneUiShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

/** [mode]: 0 system, 1 light, 2 dark. Dynamic (wallpaper) colour on Android 12+ when [dynamic].
 * [amoled] flattens dark-mode backgrounds/surfaces to true black (OLED power saving, no grey haze). [font] picks the UI typeface.
 * [style]: Material (opaque) or Glass (translucent containers over a gradient backdrop). */
@Composable
fun MeowSpoolTheme(mode: Int, dynamic: Boolean, font: UiFont = UiFont.DEFAULT, amoled: Boolean = false, style: UiStyle = UiStyle.MATERIAL, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    var scheme = when {
        style == UiStyle.ONE_UI -> if (dark) OneUiDark else OneUiLight
        dynamic && Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    if (dark && amoled) scheme = scheme.copy(
        background = Color.Black, surface = Color.Black,
        surfaceContainerLowest = Color.Black, surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF0F0F0F), surfaceContainerHigh = Color(0xFF161616), surfaceContainerHighest = Color(0xFF1C1C1C),
    )
    if (style == UiStyle.GLASS) {
        fun Color.glass(a: Float) = copy(alpha = a)
        scheme = scheme.copy(
            surfaceContainerLowest = scheme.surfaceContainerLowest.glass(0.55f),
            surfaceContainerLow = scheme.surfaceContainerLow.glass(0.55f),
            surfaceContainer = scheme.surfaceContainer.glass(0.6f),
            surfaceContainerHigh = scheme.surfaceContainerHigh.glass(0.65f),
            surfaceContainerHighest = scheme.surfaceContainerHighest.glass(0.7f),
            surfaceVariant = scheme.surfaceVariant.glass(0.6f),
            primaryContainer = scheme.primaryContainer.glass(0.55f),
            secondaryContainer = scheme.secondaryContainer.glass(0.55f),
            tertiaryContainer = scheme.tertiaryContainer.glass(0.55f),
            errorContainer = scheme.errorContainer.glass(0.7f),
            surface = scheme.surface.glass(0.9f),
        )
    }
    // If a picked font's asset never fetched (e.g. built offline), fall back to Default rather than crash.
    val heading: FontFamily?
    val body: FontFamily?
    val boldHeadings: Boolean
    if (style == UiStyle.ONE_UI) {
        heading = remember(ctx) { samsungSansFamily(ctx) }
        body = remember(ctx) { samsungOneFamily(ctx) ?: heading }
        boldHeadings = true
    } else {
        heading = remember(font) { headingFamily(ctx, font) }
        body = remember(heading, font.titlesOnly) { if (font.titlesOnly) InterFamily else heading }
        boldHeadings = false
    }
    val typography = remember(heading, body, boldHeadings) { typographyFor(heading, body, boldHeadings) }
    val shapes = if (style == UiStyle.ONE_UI) OneUiShapes else Shapes()
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}

/** The soft diagonal gradient that shows through translucent Glass-mode surfaces. No-op (fully transparent) in Material mode. */
fun Modifier.glassBackdrop(style: UiStyle, dark: Boolean): Modifier = if (style != UiStyle.GLASS) this else this.then(
    Modifier.background(
        Brush.linearGradient(
            if (dark) listOf(Color(0xFF2A1F45), Color(0xFF1A2A44), Color(0xFF15201C))
            else listOf(Color(0xFFEADCFF), Color(0xFFD8E6FF), Color(0xFFDDF3E8)),
        )
    )
)

/**
 * Real OneUI icons (github.com/OneUIProject/oneui-icons, MIT), pulled in as the `io.github.oneuiproject:icons`
 * library. Its resources land in this app's own R class after Gradle's resource merge, under whatever name
 * that library ships (convention: `ic_oui_<name>`). Those exact filenames couldn't be verified from this
 * environment — GitHub's file browser isn't reachable here — so rather than hardcode a guess that might not
 * compile, each destination is tried against a short list of plausible names via [Resources.getIdentifier]
 * at runtime, and falls back to the Material icon already used everywhere else if none of them exist.
 */
private fun oneUiIconRes(ctx: Context, vararg candidates: String): Int? {
    val res = ctx.resources
    for (name in candidates) {
        val id = res.getIdentifier(name, "drawable", ctx.packageName)
        if (id != 0) return id
    }
    return null
}

/** Candidate `ic_oui_*` names per destination — see [oneUiIconRes]. Widen this list if you find the real names. */
private val oneUiCandidates: Map<Dest, List<String>> = mapOf(
    Dest.Home to listOf("ic_oui_home", "ic_oui_home_outline"),
    Dest.Direct to listOf("ic_oui_file_upload", "ic_oui_upload", "ic_oui_document"),
    Dest.Devices to listOf("ic_oui_bluetooth", "ic_oui_bluetooth_outline"),
    Dest.History to listOf("ic_oui_history", "ic_oui_time_history"),
    Dest.Paper to listOf("ic_oui_file", "ic_oui_file_text", "ic_oui_description"),
    Dest.Print to listOf("ic_oui_control", "ic_oui_settings_outline", "ic_oui_tune"),
    Dest.Server to listOf("ic_oui_server", "ic_oui_dns", "ic_oui_network"),
    Dest.Look to listOf("ic_oui_color_swatch", "ic_oui_palette", "ic_oui_paint"),
    Dest.Log to listOf("ic_oui_bug", "ic_oui_debug"),
    Dest.About to listOf("ic_oui_info", "ic_oui_info_outline"),
)

/** [Dest.icon] to use for the current style: a real OneUI vector when [UiStyle.ONE_UI] and a matching
 * resource actually resolved, otherwise the Material [ImageVector] this app already ships. Returns
 * either an [Int] drawable resource id or an [androidx.compose.ui.graphics.vector.ImageVector] — render
 * with painterResource(id) or rememberVectorPainter accordingly (see IconFor in ui/App.kt). */
@Composable
fun Dest.resolvedIconRes(style: UiStyle): Int? {
    if (style != UiStyle.ONE_UI) return null
    val ctx = LocalContext.current
    return remember(this, style) { oneUiCandidates[this]?.let { oneUiIconRes(ctx, *it.toTypedArray()) } }
}
