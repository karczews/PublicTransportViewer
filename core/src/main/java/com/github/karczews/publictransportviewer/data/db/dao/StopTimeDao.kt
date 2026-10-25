package com.github.karczews.publictransportviewer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.karczews.publictransportviewer.data.db.entity.StopTimeEntity

@Dao
interface StopTimeDao {

    @Query("SELECT * FROM gtfs_stop_times WHERE trip_id = :tripId ORDER BY stop_sequence")
    suspend fun getStopTimesForTrip(tripId: String): List<StopTimeEntity>

    @Query("SELECT * FROM gtfs_stop_times WHERE stop_id = :stopId ORDER BY departure_time")
    suspend fun getStopTimesForStop(stopId: String): List<StopTimeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stopTimes: List<StopTimeEntity>)

    @Query("DELETE FROM gtfs_stop_times")
    suspend fun deleteAll()
}
