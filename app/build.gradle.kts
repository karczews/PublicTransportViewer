plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.spotless)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dependency.guard)
    alias(libs.plugins.dependency.analysis)
}

dependencyGuard {
    // One baseline per flavor; each covers that flavor's full transitive closure.
    configuration("tomtomReleaseRuntimeClasspath") {
        allowedFilter = { !it.contains("junit") && !it.contains("espresso") }
    }
    configuration("maplibreReleaseRuntimeClasspath") {
        allowedFilter = { !it.contains("junit") && !it.contains("espresso") }
    }
}

android {
    namespace = "com.github.karczews.publictransportviewer"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.github.karczews.publictransportviewer"
        minSdk = 32
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.github.karczews.publictransportviewer.KoinTestRunner"

        ndk {
            abiFilters.addAll(listOf("arm64-v8a", "x86_64"))
        }
        missingDimensionStrategy("tomtom-sdk-version", "complete")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // Per-flavor app_name; set explicitly, AGP 9 may default it off.
        resValues = true
    }
    flavorDimensions += "mapSdk"
    productFlavors {
        create("tomtom") {
            dimension = "mapSdk"
            applicationIdSuffix = ".tomtom"
            resValue("string", "app_name", "PTV TomTom")
        }
        create("maplibre") {
            dimension = "mapSdk"
            applicationIdSuffix = ".maplibre"
            resValue("string", "app_name", "PTV MapLibre")
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    lint {
        checkDependencies = true
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
    implementation(project(":core"))
    "tomtomImplementation"(project(":map-tomtom"))
    "maplibreImplementation"(project(":map-maplibre"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.work.runtime.ktx)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.koin.annotations)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestRuntimeOnly(project(":core-testing"))
    debugImplementation(libs.androidx.compose.ui.tooling)
    detektPlugins(libs.detekt.compose.rules)
}
