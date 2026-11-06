plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dependency.guard)
    alias(libs.plugins.dependency.analysis)
}

dependencyGuard {
    configuration("releaseRuntimeClasspath") {
        allowedFilter = { !it.contains("junit") && !it.contains("espresso") }
    }
}

android {
    namespace = "com.github.karczews.publictransportviewer.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 32

        testInstrumentationRunner = "com.github.karczews.publictransportviewer.KoinTestRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
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

room {
    schemaDirectory("$projectDir/schemas")
}

koinCompiler {
    // Compile-time dependency-graph validation (on by default; set explicitly to make the intent
    // clear).
    compileSafety = true
    // Force the full-graph safety pass to run on every build instead of being skipped by
    // Kotlin's incremental compilation cache (DSL/module lambda changes don't invalidate it
    // otherwise).
    strictSafety = true
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.work.runtime.ktx)
    implementation(libs.okhttp)
    implementation(libs.gtfs.rt.bindings)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.koin.annotations)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(project(":core-testing"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugRuntimeOnly(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    detektPlugins(libs.detekt.compose.rules)
}
