plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

val tomtomApiKey: String = findProperty("tomtomApiKey") as? String ?: ""

android {
    namespace = "com.github.karczews.publictransportviewer.map.tomtom"
    compileSdk = 37

    defaultConfig {
        minSdk = 32

        testInstrumentationRunner = "com.github.karczews.publictransportviewer.KoinTestRunner"
        missingDimensionStrategy("tomtom-sdk-version", "complete")
        buildConfigField("String", "TOMTOM_API_KEY", "\"$tomtomApiKey\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(project(":core"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.tomtom.sdk.init) {
        // TomTom pulls protobuf-javalite which duplicates classes in protobuf-java
        // (brought by org.mobilitydata:gtfs-realtime-bindings). protobuf-java is the
        // superset and required by the GTFS bindings' GeneratedMessageV3 code, so drop lite.
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
    implementation(libs.tomtom.sdk.map.display.compose) {
        exclude(group = "com.google.protobuf", module = "protobuf-javalite")
    }
    androidTestImplementation(project(":core-testing"))
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
