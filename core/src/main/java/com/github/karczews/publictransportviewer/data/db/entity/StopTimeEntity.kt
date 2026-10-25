package com.github.karczews.publictransportviewer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "gtfs_stop_times",
    primaryKeys = ["trip_id", "stop_sequence"],
    indices = [Index("stop_id")],
)
data class StopTimeEntity(
    @ColumnInfo(name = "trip_id") val tripId: String,
    @ColumnInfo(name = "stop_sequence") val stopSequence: Int,
    @ColumnInfo(name = "stop_id") val stopId: String,
    @ColumnInfo(name = "arrival_time") val arrivalTime: String,
    @ColumnInfo(name = "departure_time") val departureTime: String,
    @ColumnInfo(name = "pickup_type") val pickupType: Int?,
    @ColumnInfo(name = "drop_off_type") val dropOffType: Int?,
)
