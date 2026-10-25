package com.github.karczews.publictransportviewer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.karczews.publictransportviewer.data.db.entity.StopEntity

@Dao
interface StopDao {

    @Query("SELECT * FROM gtfs_stops WHERE stop_id = :stopId")
    suspend fun getStopById(stopId: String): StopEntity?

    @Query("SELECT * FROM gtfs_stops WHERE stop_name LIKE '%' || :query || '%' LIMIT 50")
    suspend fun searchStopsByName(query: String): List<StopEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stops: List<StopEntity>)

    @Query("DELETE FROM gtfs_stops")
    suspend fun deleteAll()
}
