package com.github.karczews.publictransportviewer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gtfs_stops",
    indices = [Index("stop_name")],
)
data class StopEntity(
    @PrimaryKey
    @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "stop_name") val stopName: String,
    @ColumnInfo(name = "stop_lat") val stopLat: Double,
    @ColumnInfo(name = "stop_lon") val stopLon: Double,
    @ColumnInfo(name = "stop_code") val stopCode: String?,
    @ColumnInfo(name = "location_type") val locationType: Int,
    @ColumnInfo(name = "parent_station") val parentStation: String?,
)
