package com.github.karczews.publictransportviewer.data.source

import com.github.karczews.publictransportviewer.data.model.ActivePeriod
import com.github.karczews.publictransportviewer.data.model.AlertCause
import com.github.karczews.publictransportviewer.data.model.AlertEffect
import com.github.karczews.publictransportviewer.data.model.ServiceAlert
import com.github.karczews.publictransportviewer.data.network.GtfsRtApi
import com.google.transit.realtime.GtfsRealtime

interface AlertDataSource {
    suspend fun getAlerts(): List<ServiceAlert>
}

class GtfsRtAlertDataSource(private val api: GtfsRtApi) : AlertDataSource {

    override suspend fun getAlerts(): List<ServiceAlert> {
        val feedMessage = api.fetchAlerts().getOrThrow()
        return feedMessage.entityList
            .filter { it.hasAlert() }
            .map { entity -> mapAlert(entity.id, entity.alert) }
    }

    private fun mapAlert(entityId: String, alert: GtfsRealtime.Alert): ServiceAlert {
        val activePeriods = alert.activePeriodList.map {
            ActivePeriod(
                startEpochSeconds = if (it.hasStart()) it.start else null,
                endEpochSeconds = if (it.hasEnd()) it.end else null,
            )
        }
        val routeIds = alert.informedEntityList
            .filter { it.hasRouteId() }
            .map { it.routeId }
            .distinct()
        val stopIds = alert.informedEntityList
            .filter { it.hasStopId() }
            .map { it.stopId }
            .distinct()
        val url = if (alert.hasUrl()) extractText(alert.url) else null

        return ServiceAlert(
            alertId = entityId,
            headerText = if (alert.hasHeaderText()) extractText(alert.headerText) else "",
            descriptionText = if (alert.hasDescriptionText()) extractText(alert.descriptionText) else "",
            cause = mapCause(alert.cause),
            effect = mapEffect(alert.effect),
            activePeriods = activePeriods,
            affectedRouteIds = routeIds,
            affectedStopIds = stopIds,
            url = url,
        )
    }

    private fun extractText(ts: GtfsRealtime.TranslatedString): String =
        ts.translationList.firstOrNull()?.text.orEmpty()

    private fun mapCause(cause: GtfsRealtime.Alert.Cause): AlertCause = when (cause) {
        GtfsRealtime.Alert.Cause.OTHER_CAUSE -> AlertCause.OTHER_CAUSE
        GtfsRealtime.Alert.Cause.TECHNICAL_PROBLEM -> AlertCause.TECHNICAL_PROBLEM
        GtfsRealtime.Alert.Cause.STRIKE -> AlertCause.STRIKE
        GtfsRealtime.Alert.Cause.DEMONSTRATION -> AlertCause.DEMONSTRATION
        GtfsRealtime.Alert.Cause.ACCIDENT -> AlertCause.ACCIDENT
        GtfsRealtime.Alert.Cause.HOLIDAY -> AlertCause.HOLIDAY
        GtfsRealtime.Alert.Cause.WEATHER -> AlertCause.WEATHER
        GtfsRealtime.Alert.Cause.MAINTENANCE -> AlertCause.MAINTENANCE
        GtfsRealtime.Alert.Cause.CONSTRUCTION -> AlertCause.CONSTRUCTION
        GtfsRealtime.Alert.Cause.POLICE_ACTIVITY -> AlertCause.POLICE_ACTIVITY
        GtfsRealtime.Alert.Cause.MEDICAL_EMERGENCY -> AlertCause.MEDICAL_EMERGENCY
        else -> AlertCause.UNKNOWN_CAUSE
    }

    private fun mapEffect(effect: GtfsRealtime.Alert.Effect): AlertEffect = when (effect) {
        GtfsRealtime.Alert.Effect.NO_SERVICE -> AlertEffect.NO_SERVICE
        GtfsRealtime.Alert.Effect.REDUCED_SERVICE -> AlertEffect.REDUCED_SERVICE
        GtfsRealtime.Alert.Effect.SIGNIFICANT_DELAYS -> AlertEffect.SIGNIFICANT_DELAYS
        GtfsRealtime.Alert.Effect.DETOUR -> AlertEffect.DETOUR
        GtfsRealtime.Alert.Effect.ADDITIONAL_SERVICE -> AlertEffect.ADDITIONAL_SERVICE
        GtfsRealtime.Alert.Effect.MODIFIED_SERVICE -> AlertEffect.MODIFIED_SERVICE
        GtfsRealtime.Alert.Effect.STOP_MOVED -> AlertEffect.STOP_MOVED
        GtfsRealtime.Alert.Effect.OTHER_EFFECT -> AlertEffect.OTHER_EFFECT
        else -> AlertEffect.UNKNOWN_EFFECT
    }
}
