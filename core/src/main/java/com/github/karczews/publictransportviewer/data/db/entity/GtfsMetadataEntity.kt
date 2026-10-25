package com.github.karczews.publictransportviewer.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "gtfs_metadata")
data class GtfsMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "key") val key: String,
    @ColumnInfo(name = "value") val value: String,
)
