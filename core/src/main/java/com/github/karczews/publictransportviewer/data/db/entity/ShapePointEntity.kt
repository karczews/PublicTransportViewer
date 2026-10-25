package com.github.karczews.publictransportviewer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "gtfs_shapes",
    primaryKeys = ["shape_id", "shape_pt_sequence"],
    indices = [Index("shape_id")],
)
data class ShapePointEntity(
    @ColumnInfo(name = "shape_id") val shapeId: String,
    @ColumnInfo(name = "shape_pt_sequence") val shapePtSequence: Int,
    @ColumnInfo(name = "shape_pt_lat") val shapePtLat: Double,
    @ColumnInfo(name = "shape_pt_lon") val shapePtLon: Double,
)
