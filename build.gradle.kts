plugins {
    id("com.android.application") version "9.1.1" apply false
    // org.jetbrains.kotlin.android removed: AGP 9.0+ has built-in Kotlin support, so this plugin
    // must not be applied (see developer.android.com/build/migrate-to-built-in-kotlin).
    // Expressive components (LoadingIndicator, ButtonGroup, SplitButton, FloatingToolbar, FAB menu) only
    // exist behind the Compose Compiler Gradle plugin path (Kotlin 2.0+), not the old kotlinCompilerExtensionVersion.
    // Kotlin 2.2.21 is required for Gradle 9.3.x compatibility (2.1.0 only tested up through Gradle ~8.10).
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
