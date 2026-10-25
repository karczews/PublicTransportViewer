// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.koin.compiler) apply false
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt) apply false
}

// Formats root-level scripts (settings.gradle.kts, build.gradle.kts).
// Module scripts are covered by each module's own spotless block.
spotless {
    kotlinGradle {
        target("*.gradle.kts")
        ktfmt().kotlinlangStyle()
    }
}
