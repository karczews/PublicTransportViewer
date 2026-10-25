package com.github.karczews.publictransportviewer.data.network

object GtfsEndpoints {

    private const val BASE = "https://otwarte.miasto.lodz.pl/wp-content/uploads"

    fun vehiclePositionsUrl(year: Int = 2025, month: Int = 6): String =
        "$BASE/$year/${"%02d".format(month)}/vehicle_positions.bin"

    fun tripUpdatesUrl(year: Int = 2025, month: Int = 6): String =
        "$BASE/$year/${"%02d".format(month)}/trip_updates.bin"

    fun alertsUrl(year: Int = 2025, month: Int = 6): String =
        "$BASE/$year/${"%02d".format(month)}/alerts.bin"

    fun gtfsZipUrl(year: Int = 2025, month: Int = 6): String =
        "$BASE/$year/${"%02d".format(month)}/GTFS.zip"
}
