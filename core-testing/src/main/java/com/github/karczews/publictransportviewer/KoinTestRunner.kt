package com.github.karczews.publictransportviewer

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Swaps in [TestPublicTransportApp] for instrumented tests so Koin is started with fake
 * repositories instead of the production graph.
 */
class KoinTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, TestPublicTransportApp::class.java.name, context)
    }
}
