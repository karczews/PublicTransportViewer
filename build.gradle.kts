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
    alias(libs.plugins.dependency.guard) apply false
    alias(libs.plugins.dependency.analysis)
}

// Formats root-level scripts (settings.gradle.kts, build.gradle.kts).
// Module scripts are covered by each module's own spotless block.
spotless {
    kotlinGradle {
        target("*.gradle.kts")
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
    }
}

// Dependency Analysis Gradle Plugin (autonomousapps): unused / mis-declared dependencies.
// `buildHealth` fails on unused dependencies and wrong scopes (runtimeOnly). Transitive-declaration
// and api-vs-implementation advice is warn-only: with AGP 9.4.1 above the plugin's supported range
// (<= 9.3.1) that advice contains false positives (see excludes below). Ratchet to fail once the
// plugin supports the AGP in use.
dependencyAnalysis {
    structure {
        // androidx *-ktx artifacts re-export their non-ktx counterpart; don't force a downgrade.
        ignoreKtx(true)
        bundle("koin") {
            includeGroup("io.insert-koin")
        }
        bundle("lifecycle") {
            includeGroup("androidx.lifecycle")
        }
        bundle("room") {
            includeGroup("androidx.room")
        }
        bundle("compose-material3") {
            includeGroup("androidx.compose.material3")
        }
    }
    issues {
        all {
            onUnusedDependencies {
                severity("fail")
            }
            onUsedTransitiveDependencies {
                severity("warn")
            }
            onIncorrectConfiguration {
                severity("warn")
            }
            onRuntimeOnly {
                severity("fail")
            }
            onCompileOnly {
                severity("fail")
            }
            onRedundantPlugins {
                severity("fail")
            }
            // KSP-only processor: never referenced from source, but required for codegen.
            onUnusedAnnotationProcessors {
                severity("ignore")
            }
        }
        // False positives with AGP 9.4.1, verified by direct-import grep:
        // - both TomTom artifacts ARE imported by map-tomtom main sources.
        project(":map-tomtom") {
            onUnusedDependencies {
                exclude(
                    "com.tomtom.sdk:init",
                    "com.tomtom.sdk.maps:map-display-compose-standard",
                )
            }
        }
        // - core unit tests import com.google.transit.realtime.GtfsRealtime directly.
        project(":core") {
            onUnusedDependencies {
                exclude("org.mobilitydata:gtfs-realtime-bindings")
            }
        }
    }
}
