package com.github.karczews.publictransportviewer.ui

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.github.karczews.publictransportviewer.core.R
import com.github.karczews.publictransportviewer.map.MapSdk
import com.github.karczews.publictransportviewer.ui.alerts.AlertsScreen
import com.github.karczews.publictransportviewer.ui.alerts.AlertsViewModel
import com.github.karczews.publictransportviewer.ui.home.MapSdkHost
import com.github.karczews.publictransportviewer.ui.home.MapSdkInitializer
import com.github.karczews.publictransportviewer.ui.stops.StopsScreen
import com.github.karczews.publictransportviewer.ui.stops.StopsViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun PublicTransportViewerApp(mapSdks: List<MapSdk>, modifier: Modifier = Modifier) {
    require(mapSdks.isNotEmpty()) { "At least one MapSdk is required" }
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }
    val scope = rememberCoroutineScope()
    val appContext = LocalContext.current.applicationContext
    val initializer = remember {
        MapSdkInitializer(scope, initialize = { it.initialize(appContext) })
    }

    NavigationSuiteScaffold(
        modifier = modifier,
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label,
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it },
                )
            }
        },
    ) {
        when (currentDestination) {
            AppDestinations.HOME -> {
                MapSdkHost(mapSdks = mapSdks, initializer = initializer)
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
