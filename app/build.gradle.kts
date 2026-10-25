plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.koin.compiler)
}

val tomtomApiKey: String = findProperty("tomtomApiKey") as? String ?: ""

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
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
    buildTypes.configureEach {
        buildConfigField("String", "TOMTOM_API_KEY", "\"$tomtomApiKey\"")
    }
    lint {
        checkDependencies = true
    }
}

koinCompiler {
    // Compile-time dependency-graph validation (on by default; set explicitly to make the intent clear).
    compileSafety = true
    // Force the full-graph safety pass to run on every build instead of being skipped by
    // Kotlin's incremental compilation cache (DSL/module lambda changes don't invalidate it otherwise).
    strictSafety = true
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.work.runtime.ktx)
    implementation(libs.tomtom.sdk.init) {
        // TomTom pulls protobuf-javalite which duplicates classes in protobuf-java
        // (brought by org.mobilitydata:gtfs-realtime-bindings). protobuf-java is the
        // superset and required by the GTFS bindings' GeneratedMessageV3 code, so drop lite.
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
    implementation(libs.tomtom.sdk.map.display.compose) {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.koin.androidx.workmanager)
    implementation(libs.koin.annotations)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
