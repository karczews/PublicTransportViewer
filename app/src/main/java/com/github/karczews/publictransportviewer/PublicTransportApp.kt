package com.github.karczews.publictransportviewer

import android.app.Application
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.github.karczews.publictransportviewer.data.source.GtfsStaticDataSource
import com.github.karczews.publictransportviewer.data.worker.GtfsRefreshWorker
import com.github.karczews.publictransportviewer.di.CoreModule
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.androidx.workmanager.koin.workManagerFactory
import org.koin.core.annotation.KoinApplication
import org.koin.plugin.module.dsl.startKoin

/**
 * Aggregator type for the Koin compiler plugin: declares which annotated modules make up the
 * application graph. `startKoin<PublicTransportKoinConfiguration>()` loads [CoreModule] and
 * triggers the compile-time full-graph safety check.
 */
@KoinApplication(modules = [CoreModule::class]) internal object PublicTransportKoinConfiguration

class PublicTransportApp : Application() {

  private val gtfsStaticDataSource: GtfsStaticDataSource by inject()

  private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onCreate() {
    super.onCreate()
    startKoin<PublicTransportKoinConfiguration> {
      androidLogger()
      androidContext(this@PublicTransportApp)
      // Registers KoinWorkerFactory and initializes WorkManager (the default
      // WorkManagerInitializer is removed in the manifest). Must run after androidContext().
      workManagerFactory()
    }
    scheduleGtfsRefresh()
    triggerInitialGtfsDownloadIfNeeded()
  }

  private fun scheduleGtfsRefresh() {
    val periodicRequest = PeriodicWorkRequestBuilder<GtfsRefreshWorker>(7, TimeUnit.DAYS).build()
    WorkManager.getInstance(this)
      .enqueueUniquePeriodicWork(
        GtfsRefreshWorker.WORK_NAME,
        ExistingPeriodicWorkPolicy.KEEP,
        periodicRequest,
      )
  }

  private fun triggerInitialGtfsDownloadIfNeeded() {
    appScope.launch {
      if (!gtfsStaticDataSource.isDataAvailable()) {
        Log.d("PublicTransportApp", "Starting initial GTFS download")
        val result = gtfsStaticDataSource.downloadAndImport()
        Log.d("PublicTransportApp", "GTFS download result: $result")
        result.exceptionOrNull()?.let {
          Log.e("PublicTransportApp", "GTFS download failed", it)
        }
      } else {
        Log.d("PublicTransportApp", "GTFS data already available")
      }
    }
  }
}
