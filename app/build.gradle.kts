plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "dev.meowspool"
    compileSdk = 34
    defaultConfig {
        applicationId = "dev.meowspool"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.material3:material3")
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
                java.net.URI(url).toURL().openStream().use { input -> f.outputStream().use { input.copyTo(it) } }
            } catch (e: Exception) {
                println("MeowSpool: couldn't fetch $name (${e.message}); that font just won't be offered.")
            }
        }
    }
}
tasks.named("preBuild") { dependsOn("fetchNothingFonts") }
