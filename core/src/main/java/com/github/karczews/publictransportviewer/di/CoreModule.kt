package com.github.karczews.publictransportviewer.di

import android.content.Context
import androidx.room.Room
import com.github.karczews.publictransportviewer.data.db.GtfsDatabase
import com.github.karczews.publictransportviewer.data.db.dao.RouteDao
import com.github.karczews.publictransportviewer.data.db.dao.ShapePointDao
import com.github.karczews.publictransportviewer.data.db.dao.StopDao
import com.github.karczews.publictransportviewer.data.db.dao.StopTimeDao
import com.github.karczews.publictransportviewer.data.db.dao.TripDao
import com.github.karczews.publictransportviewer.data.network.GtfsRtApi
import com.github.karczews.publictransportviewer.data.network.GtfsRtApiImpl
import com.github.karczews.publictransportviewer.data.repository.AlertRepository
import com.github.karczews.publictransportviewer.data.repository.DefaultAlertRepository
import com.github.karczews.publictransportviewer.data.repository.DefaultRouteDisplayRepository
import com.github.karczews.publictransportviewer.data.repository.DefaultStopRepository
import com.github.karczews.publictransportviewer.data.repository.DefaultVehicleRepository
import com.github.karczews.publictransportviewer.data.repository.RouteDisplayRepository
import com.github.karczews.publictransportviewer.data.repository.StopRepository
import com.github.karczews.publictransportviewer.data.repository.VehicleRepository
import com.github.karczews.publictransportviewer.data.source.AlertDataSource
import com.github.karczews.publictransportviewer.data.source.GtfsRtAlertDataSource
import com.github.karczews.publictransportviewer.data.source.GtfsRtTripUpdateDataSource
import com.github.karczews.publictransportviewer.data.source.GtfsRtVehicleDataSource
import com.github.karczews.publictransportviewer.data.source.GtfsStaticDataSource
import com.github.karczews.publictransportviewer.data.source.TripUpdateDataSource
import com.github.karczews.publictransportviewer.data.source.VehicleDataSource
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Koin module wiring the whole graph.
 *
 * Infrastructure, data sources and repositories are declared as [Single] provider functions (the equivalent of Dagger
 * `@Provides`) because they need custom construction or interface binding. The [ComponentScan] picks up class-level
 * definitions in the base package — the `@KoinViewModel` view models and the `@KoinWorker` worker.
 *
 * The Koin compiler plugin validates this graph at compile time (`compileSafety`); `android.content.Context` is part of
 * the framework whitelist and is supplied at runtime by `androidContext()`.
 */
@Module
@ComponentScan("com.github.karczews.publictransportviewer")
class CoreModule {

    @Single
    fun okHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

    @Single
    fun gtfsDatabase(context: Context): GtfsDatabase =
        Room.databaseBuilder(context, GtfsDatabase::class.java, "gtfs.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Single fun routeDao(db: GtfsDatabase): RouteDao = db.routeDao()

    @Single fun stopDao(db: GtfsDatabase): StopDao = db.stopDao()

    @Single fun tripDao(db: GtfsDatabase): TripDao = db.tripDao()

    @Single fun stopTimeDao(db: GtfsDatabase): StopTimeDao = db.stopTimeDao()

    @Single fun shapePointDao(db: GtfsDatabase): ShapePointDao = db.shapePointDao()

    @Single fun gtfsRtApi(client: OkHttpClient): GtfsRtApi = GtfsRtApiImpl(client)

    @Single fun vehicleDataSource(api: GtfsRtApi): VehicleDataSource = GtfsRtVehicleDataSource(api)

    @Single fun tripUpdateDataSource(api: GtfsRtApi): TripUpdateDataSource = GtfsRtTripUpdateDataSource(api)

    @Single fun alertDataSource(api: GtfsRtApi): AlertDataSource = GtfsRtAlertDataSource(api)

    @Single
    fun gtfsStaticDataSource(client: OkHttpClient, db: GtfsDatabase): GtfsStaticDataSource =
        GtfsStaticDataSource(client, db)

    @Single
    fun vehicleRepository(
        dataSource: VehicleDataSource,
        routeDao: RouteDao,
        tripDao: TripDao,
    ): VehicleRepository = DefaultVehicleRepository(dataSource, routeDao, tripDao)

    @Single
    fun routeDisplayRepository(
        tripDao: TripDao,
        routeDao: RouteDao,
        shapePointDao: ShapePointDao,
        stopTimeDao: StopTimeDao,
        stopDao: StopDao,
    ): RouteDisplayRepository = DefaultRouteDisplayRepository(tripDao, routeDao, shapePointDao, stopTimeDao, stopDao)

    @Single
    fun alertRepository(
        alertDataSource: AlertDataSource,
        routeDao: RouteDao,
    ): AlertRepository = DefaultAlertRepository(alertDataSource, routeDao)

    @Single
    fun stopRepository(
        stopDao: StopDao,
        stopTimeDao: StopTimeDao,
        tripDao: TripDao,
        routeDao: RouteDao,
        tripUpdateDataSource: TripUpdateDataSource,
    ): StopRepository = DefaultStopRepository(stopDao, stopTimeDao, tripDao, routeDao, tripUpdateDataSource)

    companion object {
        private const val TIMEOUT_SECONDS = 30L
    }
}
