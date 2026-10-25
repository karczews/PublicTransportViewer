package com.github.karczews.publictransportviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.karczews.publictransportviewer.map.tomtom.TomTomMapSdk
import com.github.karczews.publictransportviewer.ui.PublicTransportViewerApp
import com.github.karczews.publictransportviewer.ui.theme.PublicTransportViewerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PublicTransportViewerTheme {
                PublicTransportViewerApp(mapSdks = listOf(TomTomMapSdk(BuildConfig.TOMTOM_API_KEY)))
            }
        }
    }
}
