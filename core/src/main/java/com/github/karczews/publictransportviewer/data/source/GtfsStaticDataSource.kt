package com.github.karczews.publictransportviewer.data.source

import android.util.Log
import com.github.karczews.publictransportviewer.data.db.GtfsDatabase
import com.github.karczews.publictransportviewer.data.db.entity.GtfsMetadataEntity
import com.github.karczews.publictransportviewer.data.network.GtfsEndpoints
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class GtfsStaticDataSource(
    private val client: OkHttpClient,
    private val database: GtfsDatabase,
) {

    suspend fun downloadAndImport(): Result<Unit> =
        withContext(Dispatchers.IO) {
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
                    importZip(zipStream)
                    zipStream.close()
                }

                database
                    .metadataDao()
                    .setValue(
                        GtfsMetadataEntity(
                            key = KEY_LAST_DOWNLOAD,
                            value = System.currentTimeMillis().toString(),
                        ),
                    )
                Log.d(TAG, "GTFS import complete")
                Unit
            }
        }

    suspend fun isDataAvailable(): Boolean = database.metadataDao().getValue(KEY_LAST_DOWNLOAD) != null

    private suspend fun importZip(zipStream: ZipInputStream) {
        var entry = zipStream.nextEntry
        while (entry != null) {
            if (entry.name in HANDLED_FILES) {
                importFile(zipStream, entry.name)
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
    }

    private suspend fun importFile(zipStream: ZipInputStream, name: String) {
        val bytes = zipStream.readBytes()
        Log.d(TAG, "Parsing $name (${bytes.size} bytes)")
        val reader =
            BufferedReader(
                InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8),
            )
        when (name) {
            "routes.txt" -> importRoutes(reader)
            "stops.txt" -> importStops(reader)
            "trips.txt" -> importTrips(reader)
            "stop_times.txt" -> importStopTimes(reader)
            "shapes.txt" -> importShapes(reader)
        }
    }

    private suspend fun importRoutes(reader: BufferedReader) {
        val routes = GtfsCsvParser.parseRoutes(reader)
        database.routeDao().deleteAll()
        database.routeDao().insertAll(routes)
        Log.d(TAG, "Imported ${routes.size} routes")
    }

    private suspend fun importStops(reader: BufferedReader) {
        val stops = GtfsCsvParser.parseStops(reader)
        database.stopDao().deleteAll()
        database.stopDao().insertAll(stops)
        Log.d(TAG, "Imported ${stops.size} stops")
    }

    private suspend fun importTrips(reader: BufferedReader) {
        val trips = GtfsCsvParser.parseTrips(reader)
        database.tripDao().deleteAll()
        database.tripDao().insertAll(trips)
        Log.d(TAG, "Imported ${trips.size} trips")
    }

    private suspend fun importStopTimes(reader: BufferedReader) {
        database.stopTimeDao().deleteAll()
        var count = 0
        GtfsCsvParser.parseStopTimesStreaming(reader) { batch ->
            database.stopTimeDao().insertAll(batch)
            count += batch.size
        }
        Log.d(TAG, "Imported $count stop times")
    }

    private suspend fun importShapes(reader: BufferedReader) {
        database.shapePointDao().deleteAll()
        var count = 0
        GtfsCsvParser.parseShapesStreaming(reader) { batch ->
            database.shapePointDao().insertAll(batch)
            count += batch.size
        }
        Log.d(TAG, "Imported $count shape points")
    }

    companion object {
        private const val TAG = "GtfsStaticDataSource"
        const val KEY_LAST_DOWNLOAD = "last_download_timestamp"
        private val HANDLED_FILES = setOf("routes.txt", "stops.txt", "trips.txt", "stop_times.txt", "shapes.txt")
    }
}
