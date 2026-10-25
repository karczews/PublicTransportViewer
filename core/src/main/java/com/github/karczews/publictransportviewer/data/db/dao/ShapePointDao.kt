package com.github.karczews.publictransportviewer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.github.karczews.publictransportviewer.data.db.entity.ShapePointEntity

@Dao
interface ShapePointDao {

    @Query("SELECT * FROM gtfs_shapes WHERE shape_id = :shapeId ORDER BY shape_pt_sequence")
    suspend fun getShapePoints(shapeId: String): List<ShapePointEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<ShapePointEntity>)

    @Query("DELETE FROM gtfs_shapes")
    suspend fun deleteAll()
}
