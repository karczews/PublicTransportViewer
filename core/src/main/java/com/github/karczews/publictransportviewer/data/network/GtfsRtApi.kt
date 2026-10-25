package com.github.karczews.publictransportviewer.data.network

import com.google.transit.realtime.GtfsRealtime.FeedMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

interface GtfsRtApi {
    suspend fun fetchVehiclePositions(): Result<FeedMessage>
    suspend fun fetchTripUpdates(): Result<FeedMessage>
    suspend fun fetchAlerts(): Result<FeedMessage>
}

class GtfsRtApiImpl(private val client: OkHttpClient) : GtfsRtApi {

    override suspend fun fetchVehiclePositions(): Result<FeedMessage> =
        fetchFeed(GtfsEndpoints.vehiclePositionsUrl())

    override suspend fun fetchTripUpdates(): Result<FeedMessage> =
        fetchFeed(GtfsEndpoints.tripUpdatesUrl())

    override suspend fun fetchAlerts(): Result<FeedMessage> =
        fetchFeed(GtfsEndpoints.alertsUrl())

    private suspend fun fetchFeed(url: String): Result<FeedMessage> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${response.message}")
            }
            val bytes = response.body!!.bytes()
            FeedMessage.parseFrom(bytes)
        }
    }
}
