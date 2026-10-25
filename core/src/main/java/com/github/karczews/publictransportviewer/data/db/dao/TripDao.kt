package com.github.karczews.publictransportviewer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.karczews.publictransportviewer.data.db.entity.TripEntity

@Dao
interface TripDao {

    @Query("SELECT * FROM gtfs_trips WHERE trip_id = :tripId")
    suspend fun getTripById(tripId: String): TripEntity?

    @Query("SELECT * FROM gtfs_trips WHERE route_id = :routeId")
    suspend fun getTripsByRouteId(routeId: String): List<TripEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(trips: List<TripEntity>)

    @Query("DELETE FROM gtfs_trips")
    suspend fun deleteAll()
}
