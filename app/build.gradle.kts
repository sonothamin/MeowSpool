import java.net.URL

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "dev.meowspool"
    // material3 1.5.0-alpha22 requires compiling against API 37 (see AGP/compileSdk compatibility table);
    // AGP 9.1.1 is the minimum AGP that supports compileSdk 37.
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.meowspool"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    // No composeOptions/kotlinCompilerExtensionVersion needed: the Compose Compiler Gradle plugin
    // (applied above) derives the compiler version from the Kotlin version automatically.
}
dependencies {
    // Real OneUI icon set (github.com/OneUIProject/oneui-icons) for One UI style; resources are looked
    // up by name at runtime (see OneUiIcon in ui/Theme.kt) with a Material fallback, since the exact
    // ic_oui_* filenames couldn't be verified from this environment (GitHub's file browser blocks
    // automated access here) — a wrong guess degrades to the Material icon instead of failing.
    implementation("io.github.oneuiproject:icons:1.1.0")
    implementation(platform("androidx.compose:compose-bom:2025.12.00"))
    // Expressive (LoadingIndicator, ButtonGroup, SplitButtonLayout, HorizontalFloatingToolbar, FAB menu)
    // isn't in the BOM's pinned stable 1.4.0 yet — those components only exist from 1.5.0-alpha onward.
    // Pinned to alpha22 specifically: it's the release where ButtonGroup was promoted to non-experimental
    // and SplitButtonLayout is still the current (non-deprecated) name — later alphas rename/reshuffle
    // these APIs, so this version is a deliberate, narrower target rather than "whatever's newest".
    implementation("androidx.compose.material3:material3:1.5.0-alpha22")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.zxing:core:3.5.3")
}

// Ndot/NType are Nothing's own branded fonts ("NOTHING Tech. All Rights Reserved." per
// github.com/xeji01/nothingfont) — not ours to redistribute in this repo's git history.
// Fetch them into assets at build time instead; app/src/main/assets/fonts/ is gitignored.
// A failed/offline fetch just leaves the files missing, and the app treats that as
// "these fonts aren't offered" (see UiFont.isAvailable in ui/Theme.kt) rather than failing the build.
tasks.register("fetchNothingFonts") {
    val dir = File(projectDir, "src/main/assets/fonts")
    val files = mapOf(
        "ndot.otf" to "https://raw.githubusercontent.com/xeji01/nothingfont/main/fonts/Ndot57-Regular.otf",
        "ntype.otf" to "https://raw.githubusercontent.com/xeji01/nothingfont/main/fonts/NType82-Headline.otf",
    )
    doLast {
        dir.mkdirs()
        files.forEach { (name, url) ->
            val f = File(dir, name)
            if (f.exists()) return@forEach
            try {
                println("MeowSpool: fetching $name from nothingfont…")
                URL(url).openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
            } catch (e: Exception) {
                println("MeowSpool: couldn't fetch $name (${e.message}); that font just won't be offered.")
            }
        }
    }
}
tasks.named("preBuild") { dependsOn("fetchNothingFonts") }

// Samsung's own One UI faces, same not-ours-to-redistribute situation as the Nothing fonts above.
// Fetched on demand for the "One UI" style (see UiStyle.ONE_UI in ui/Theme.kt): SamsungSans for
// titles/headings, SamsungOne for body text. Missing fetch => One UI style just falls back to the
// default typeface rather than failing the build.
tasks.register("fetchSamsungFonts") {
    val dir = File(projectDir, "src/main/assets/fonts")
    val files = mapOf(
        "samsungsans.ttf" to "https://raw.githubusercontent.com/Odrha23/samsung-sans/master/SamsungSans-Regular.ttf",
        "samsungone.ttf" to "https://raw.githubusercontent.com/putrairvaan/Font-TTF/master/SamsungOne-400.ttf",
    )
    doLast {
        dir.mkdirs()
        files.forEach { (name, url) ->
            val f = File(dir, name)
            if (f.exists()) return@forEach
            try {
                println("MeowSpool: fetching $name for One UI style…")
                URL(url).openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
            } catch (e: Exception) {
                println("MeowSpool: couldn't fetch $name (${e.message}); One UI style will use the default typeface.")
            }
        }
    }
}
tasks.named("preBuild") { dependsOn("fetchSamsungFonts") }
