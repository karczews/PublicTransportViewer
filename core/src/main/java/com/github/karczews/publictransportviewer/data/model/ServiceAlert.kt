package com.github.karczews.publictransportviewer.data.model

data class ServiceAlert(
    val alertId: String,
    val headerText: String,
    val descriptionText: String,
    val cause: AlertCause,
    val effect: AlertEffect,
    val activePeriods: List<ActivePeriod>,
    val affectedRouteIds: List<String>,
    val affectedRouteShortNames: List<String> = emptyList(),
    val affectedStopIds: List<String>,
    val url: String? = null,
)

data class ActivePeriod(
    val startEpochSeconds: Long?,
    val endEpochSeconds: Long?,
)

enum class AlertCause {
    UNKNOWN_CAUSE,
    OTHER_CAUSE,
    TECHNICAL_PROBLEM,
    STRIKE,
    DEMONSTRATION,
    ACCIDENT,
    HOLIDAY,
    WEATHER,
    MAINTENANCE,
    CONSTRUCTION,
    POLICE_ACTIVITY,
    MEDICAL_EMERGENCY,
}

enum class AlertEffect {
    NO_SERVICE,
    REDUCED_SERVICE,
    SIGNIFICANT_DELAYS,
    DETOUR,
    ADDITIONAL_SERVICE,
    MODIFIED_SERVICE,
    OTHER_EFFECT,
    UNKNOWN_EFFECT,
    STOP_MOVED,
}
