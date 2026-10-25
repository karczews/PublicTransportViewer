package com.github.karczews.publictransportviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.github.karczews.publictransportviewer.core.R
import com.github.karczews.publictransportviewer.ui.alerts.AlertsScreen
import com.github.karczews.publictransportviewer.ui.alerts.AlertsViewModel
import com.github.karczews.publictransportviewer.ui.home.HomeScreen
import com.github.karczews.publictransportviewer.ui.home.HomeViewModel
import com.github.karczews.publictransportviewer.ui.stops.StopsScreen
import com.github.karczews.publictransportviewer.ui.stops.StopsViewModel
import com.github.karczews.publictransportviewer.ui.theme.PublicTransportViewerTheme
import com.tomtom.sdk.common.configuration.buildSdkConfiguration
import com.tomtom.sdk.init.TomTomSdk
import com.tomtom.sdk.telemetry.UserConsent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    private val sdkInitialized = MutableStateFlow(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        initTomTomSdk()
        setContent {
            PublicTransportViewerTheme {
                val isReady by sdkInitialized.collectAsStateWithLifecycle()
                if (isReady) {
                    PublicTransportViewerApp()
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

    private fun initTomTomSdk() {
        lifecycleScope.launch {
            if (!TomTomSdk.isInitialized) {
                TomTomSdk.initialize(
                    context = application,
                    sdkConfiguration = buildSdkConfiguration(
                        context = application,
                        apiKey = BuildConfig.TOMTOM_API_KEY,
                        telemetryUserConsent = { UserConsent.TelemetryOn },
                    ),
                )
            }
            sdkInitialized.value = true
        }
    }
}

@Composable
fun PublicTransportViewerApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        when (currentDestination) {
            AppDestinations.HOME -> {
                val homeViewModel: HomeViewModel = koinViewModel()
                HomeScreen(viewModel = homeViewModel)
            }
            AppDestinations.STOPS -> {
                val stopsViewModel: StopsViewModel = koinViewModel()
                StopsScreen(viewModel = stopsViewModel)
            }
            AppDestinations.ALERTS -> {
                val alertsViewModel: AlertsViewModel = koinViewModel()
                AlertsScreen(viewModel = alertsViewModel)
            }
        }
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
) {
    HOME("Home", R.drawable.ic_home),
    STOPS("Stops", R.drawable.ic_bus),
    ALERTS("Alerts", R.drawable.ic_alert),
}
