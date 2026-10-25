package com.github.karczews.publictransportviewer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gtfs_routes")
data class RouteEntity(
    @PrimaryKey
    @ColumnInfo(name = "route_id") val routeId: String,
    @ColumnInfo(name = "agency_id") val agencyId: String?,
    @ColumnInfo(name = "route_short_name") val routeShortName: String,
    @ColumnInfo(name = "route_long_name") val routeLongName: String,
    @ColumnInfo(name = "route_type") val routeType: Int,
    @ColumnInfo(name = "route_color") val routeColor: String?,
    @ColumnInfo(name = "route_text_color") val routeTextColor: String?,
)
