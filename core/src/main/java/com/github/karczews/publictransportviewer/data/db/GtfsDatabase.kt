package com.github.karczews.publictransportviewer.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.github.karczews.publictransportviewer.data.db.dao.GtfsMetadataDao
import com.github.karczews.publictransportviewer.data.db.dao.RouteDao
import com.github.karczews.publictransportviewer.data.db.dao.ShapePointDao
import com.github.karczews.publictransportviewer.data.db.dao.StopDao
import com.github.karczews.publictransportviewer.data.db.dao.StopTimeDao
import com.github.karczews.publictransportviewer.data.db.dao.TripDao
import com.github.karczews.publictransportviewer.data.db.entity.GtfsMetadataEntity
import com.github.karczews.publictransportviewer.data.db.entity.RouteEntity
import com.github.karczews.publictransportviewer.data.db.entity.ShapePointEntity
import com.github.karczews.publictransportviewer.data.db.entity.StopEntity
import com.github.karczews.publictransportviewer.data.db.entity.StopTimeEntity
import com.github.karczews.publictransportviewer.data.db.entity.TripEntity

@Database(
    entities = [
        RouteEntity::class,
        StopEntity::class,
        TripEntity::class,
        StopTimeEntity::class,
        ShapePointEntity::class,
        GtfsMetadataEntity::class,
    ],
    version = 2,
)
abstract class GtfsDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao
    abstract fun stopDao(): StopDao
    abstract fun tripDao(): TripDao
    abstract fun stopTimeDao(): StopTimeDao
    abstract fun shapePointDao(): ShapePointDao
    abstract fun metadataDao(): GtfsMetadataDao
}
