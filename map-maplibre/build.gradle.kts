plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.github.karczews.publictransportviewer.map.maplibre"
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
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.maplibre.compose)
    implementation(libs.maplibre.compose.location)

    // MapLibre render backend. maplibre-compose needs exactly ONE native runtime on the classpath:
    //   - OpenGL (default): widest device/emulator support  -> libs.maplibre.compose.runtime.opengl
    //   - Vulkan:           replace the line below with      -> libs.maplibre.compose.runtime.vulkan
    // Never add both. The runtime must match the maplibre-compose version; both use the
    // `maplibreCompose` version ref in gradle/libs.versions.toml, so bump them together.
    runtimeOnly(libs.maplibre.compose.runtime.opengl)

    testImplementation(libs.junit)
    androidTestImplementation(project(":core-testing"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
