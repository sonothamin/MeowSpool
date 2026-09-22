plugins {
    id("com.android.application") version "9.1.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.0" apply false
    // Expressive components (LoadingIndicator, ButtonGroup, SplitButton, FloatingToolbar, FAB menu) only
    // exist behind the Compose Compiler Gradle plugin path (Kotlin 2.0+), not the old kotlinCompilerExtensionVersion.
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
}
