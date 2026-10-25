package com.github.karczews.publictransportviewer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.karczews.publictransportviewer.data.db.entity.RouteEntity

@Dao
interface RouteDao {

    @Query("SELECT * FROM gtfs_routes WHERE route_id = :routeId")
    suspend fun getRouteById(routeId: String): RouteEntity?

    @Query("SELECT * FROM gtfs_routes")
    suspend fun getAllRoutes(): List<RouteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(routes: List<RouteEntity>)

    @Query("DELETE FROM gtfs_routes")
    suspend fun deleteAll()
}
