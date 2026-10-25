package com.github.karczews.publictransportviewer.data.repository

import com.github.karczews.publictransportviewer.data.db.dao.RouteDao
import com.github.karczews.publictransportviewer.data.model.ServiceAlert
import com.github.karczews.publictransportviewer.data.source.AlertDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface AlertRepository {
    fun observeAlerts(): Flow<List<ServiceAlert>>
}

class DefaultAlertRepository(
    private val alertDataSource: AlertDataSource,
    private val routeDao: RouteDao,
) : AlertRepository {

    private var lastAlerts: List<ServiceAlert> = emptyList()

    override fun observeAlerts(): Flow<List<ServiceAlert>> = flow {
        while (true) {
            val alerts = try {
                val raw = alertDataSource.getAlerts()
                enrichAlerts(raw).also { lastAlerts = it }
            } catch (_: Exception) {
                lastAlerts
            }
            emit(alerts)
            delay(POLL_INTERVAL_MS)
        }
    }

    private suspend fun enrichAlerts(alerts: List<ServiceAlert>): List<ServiceAlert> {
        val allRouteIds = alerts.flatMap { it.affectedRouteIds }.distinct()
        val routeNames = allRouteIds.mapNotNull { id ->
            routeDao.getRouteById(id)?.let { id to it.routeShortName }
        }.toMap()

        return alerts.map { alert ->
            alert.copy(
                affectedRouteShortNames = alert.affectedRouteIds.mapNotNull { routeNames[it] },
            )
        }
    }

    companion object {
        private const val POLL_INTERVAL_MS = 60_000L
    }
}
