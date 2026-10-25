package com.github.karczews.publictransportviewer.map.tomtom

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.karczews.publictransportviewer.map.MapSdk
import com.github.karczews.publictransportviewer.ui.home.HomeScreen
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import com.tomtom.sdk.common.configuration.buildSdkConfiguration
import com.tomtom.sdk.init.TomTomSdk
import com.tomtom.sdk.telemetry.UserConsent

class TomTomMapSdk(private val apiKey: String) : MapSdk {
    override val id = "tomtom"
    override val displayName = "TomTom"

    override suspend fun initialize(context: Context) {
        if (!TomTomSdk.isInitialized) {
            TomTomSdk.initialize(
                context = context,
                sdkConfiguration = buildSdkConfiguration(
                    context = context,
                    apiKey = apiKey,
                    telemetryUserConsent = { UserConsent.TelemetryOn },
                ),
            )
        }
    }

    @Composable
    override fun HomeScreen(viewModel: HomeViewModel, modifier: Modifier) {
        HomeScreen(viewModel = viewModel, modifier = modifier)
    }
}
