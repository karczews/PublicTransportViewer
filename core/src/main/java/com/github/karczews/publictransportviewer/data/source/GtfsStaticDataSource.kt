package com.github.karczews.publictransportviewer.data.source

import android.util.Log
import com.github.karczews.publictransportviewer.data.db.GtfsDatabase
import com.github.karczews.publictransportviewer.data.db.entity.GtfsMetadataEntity
import com.github.karczews.publictransportviewer.data.network.GtfsEndpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

class GtfsStaticDataSource(
    private val client: OkHttpClient,
    private val database: GtfsDatabase,
) {

    suspend fun downloadAndImport(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = GtfsEndpoints.gtfsZipUrl()
            Log.d(TAG, "Downloading GTFS from $url")
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            Log.d(TAG, "Response: ${response.code}")
            if (!response.isSuccessful) {
                error("HTTP ${response.code}: ${response.message}")
            }

            response.body!!.use { body ->
                val zipStream = ZipInputStream(body.byteStream())
                var entry = zipStream.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name in HANDLED_FILES) {
                        val bytes = zipStream.readBytes()
                        Log.d(TAG, "Parsing $name (${bytes.size} bytes)")
                        val reader = BufferedReader(
                            InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8)
                        )
                        when (name) {
                            "routes.txt" -> {
                                val routes = GtfsCsvParser.parseRoutes(reader)
                                database.routeDao().deleteAll()
                                database.routeDao().insertAll(routes)
                                Log.d(TAG, "Imported ${routes.size} routes")
                            }
                            "stops.txt" -> {
                                val stops = GtfsCsvParser.parseStops(reader)
                                database.stopDao().deleteAll()
                                database.stopDao().insertAll(stops)
                                Log.d(TAG, "Imported ${stops.size} stops")
                            }
                            "trips.txt" -> {
                                val trips = GtfsCsvParser.parseTrips(reader)
                                database.tripDao().deleteAll()
                                database.tripDao().insertAll(trips)
                                Log.d(TAG, "Imported ${trips.size} trips")
                            }
                            "stop_times.txt" -> {
                                database.stopTimeDao().deleteAll()
                                var count = 0
                                GtfsCsvParser.parseStopTimesStreaming(reader) { batch ->
                                    database.stopTimeDao().insertAll(batch)
                                    count += batch.size
                                }
                                Log.d(TAG, "Imported $count stop times")
                            }
                            "shapes.txt" -> {
                                database.shapePointDao().deleteAll()
                                var count = 0
                                GtfsCsvParser.parseShapesStreaming(reader) { batch ->
                                    database.shapePointDao().insertAll(batch)
                                    count += batch.size
                                }
                                Log.d(TAG, "Imported $count shape points")
                            }
                        }
                    }
                    zipStream.closeEntry()
                    entry = zipStream.nextEntry
                }
                zipStream.close()
            }

            database.metadataDao().setValue(
                GtfsMetadataEntity(
                    key = KEY_LAST_DOWNLOAD,
                    value = System.currentTimeMillis().toString(),
                )
            )
            Log.d(TAG, "GTFS import complete")
            Unit
        }
    }

    suspend fun isDataAvailable(): Boolean =
        database.metadataDao().getValue(KEY_LAST_DOWNLOAD) != null

    companion object {
        private const val TAG = "GtfsStaticDataSource"
        const val KEY_LAST_DOWNLOAD = "last_download_timestamp"
        private val HANDLED_FILES = setOf("routes.txt", "stops.txt", "trips.txt", "stop_times.txt", "shapes.txt")
    }
}
