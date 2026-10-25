package com.github.karczews.publictransportviewer.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.github.karczews.publictransportviewer.data.source.GtfsStaticDataSource
import org.koin.android.annotation.KoinWorker

@KoinWorker
class GtfsRefreshWorker(
    appContext: Context,
    params: WorkerParameters,
    private val gtfsStaticDataSource: GtfsStaticDataSource,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return gtfsStaticDataSource.downloadAndImport().fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val WORK_NAME = "gtfs_refresh"
    }
}
