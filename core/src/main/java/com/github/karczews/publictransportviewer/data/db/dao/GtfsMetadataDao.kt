package com.github.karczews.publictransportviewer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.karczews.publictransportviewer.data.db.entity.GtfsMetadataEntity

@Dao
interface GtfsMetadataDao {

    @Query("SELECT value FROM gtfs_metadata WHERE `key` = :key")
    suspend fun getValue(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setValue(entity: GtfsMetadataEntity)

    @Query("DELETE FROM gtfs_metadata")
    suspend fun deleteAll()
}
