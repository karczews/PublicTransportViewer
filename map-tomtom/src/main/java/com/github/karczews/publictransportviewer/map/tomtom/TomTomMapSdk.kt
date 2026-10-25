package com.github.karczews.publictransportviewer.map.tomtom

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.karczews.publictransportviewer.map.MapSdk
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import com.tomtom.sdk.common.configuration.buildSdkConfiguration
import com.tomtom.sdk.init.TomTomSdk
import com.tomtom.sdk.telemetry.UserConsent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TomTomMapSdk(private val apiKey: String = BuildConfig.TOMTOM_API_KEY) : MapSdk {
    override val id = "tomtom"
    override val displayName = "TomTom"

    override suspend fun initialize(context: Context) {
        // TomTom requires initialization on a worker thread; callers run on the main dispatcher.
        withContext(Dispatchers.IO) {
            if (!TomTomSdk.isInitialized) {
                TomTomSdk.initialize(
                    context = context,
                    sdkConfiguration = buildSdkConfiguration(
                        context = context,
                        apiKey = apiKey,
                        // No consent UX yet: stay opted out rather than enabling telemetry by default.
                        telemetryUserConsent = { UserConsent.TelemetryOff },
                    ),
                )
            }
        }
    }

    @Composable
    override fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier) {
        TomTomHomeScreen(viewModel = viewModel, modifier = modifier)
    }
}
