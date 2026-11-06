plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dependency.guard)
    alias(libs.plugins.dependency.analysis)
}

dependencyGuard {
    configuration("releaseRuntimeClasspath")
}

android {
    namespace = "com.github.karczews.publictransportviewer.core.testing"
    compileSdk = 37

    defaultConfig {
        minSdk = 32
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

spotless {
    // Spotless cannot infer Android source sets, so the target is set explicitly.
    kotlin {
        target("src/**/*.kt")
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
    }
}

detekt {
    config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.test.runner)
    detektPlugins(libs.detekt.compose.rules)
}
