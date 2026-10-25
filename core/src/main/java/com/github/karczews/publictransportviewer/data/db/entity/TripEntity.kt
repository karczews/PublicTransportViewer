package com.github.karczews.publictransportviewer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "gtfs_trips",
    indices = [Index("route_id")],
)
data class TripEntity(
    @PrimaryKey
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "route_id") val routeId: String,
    @ColumnInfo(name = "service_id") val serviceId: String,
    @ColumnInfo(name = "trip_headsign") val tripHeadsign: String?,
    @ColumnInfo(name = "direction_id") val directionId: Int?,
    @ColumnInfo(name = "shape_id") val shapeId: String?,
)
