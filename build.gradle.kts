plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.2.21" apply false
    // Expressive components (LoadingIndicator, ButtonGroup, SplitButton, FloatingToolbar, FAB menu) only
    // exist behind the Compose Compiler Gradle plugin path (Kotlin 2.0+), not the old kotlinCompilerExtensionVersion.
    // Kotlin 2.2.21 is required for Gradle 9.3.x compatibility (2.1.0 only tested up through Gradle ~8.10).
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.21" apply false
}
