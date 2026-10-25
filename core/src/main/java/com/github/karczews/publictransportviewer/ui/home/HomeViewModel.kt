package com.github.karczews.publictransportviewer.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.karczews.publictransportviewer.data.model.RouteDisplayData
import com.github.karczews.publictransportviewer.data.model.VehiclePosition
import com.github.karczews.publictransportviewer.data.repository.RouteDisplayRepository
import com.github.karczews.publictransportviewer.data.repository.VehicleRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@KoinViewModel
class HomeViewModel(
    private val repository: VehicleRepository,
    private val routeDisplayRepository: RouteDisplayRepository,
) : ViewModel() {

    private val _vehiclePositions = MutableStateFlow<List<VehiclePosition>>(emptyList())
    val vehiclePositions: StateFlow<List<VehiclePosition>> = _vehiclePositions.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedVehicleId = MutableStateFlow<String?>(null)
    val selectedVehicleId: StateFlow<String?> = _selectedVehicleId.asStateFlow()

    private val _routeDisplay = MutableStateFlow<RouteDisplayData?>(null)
    val routeDisplay: StateFlow<RouteDisplayData?> = _routeDisplay.asStateFlow()

    private var routeLoadJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeVehiclePositions()
                .catch { e ->
                    Log.e("HomeViewModel", "Flow error", e)
                    _error.value = e.message
                }
                .collect { positions ->
                    Log.d("HomeViewModel", "Received ${positions.size} vehicles")
                    _vehiclePositions.value = positions
                    if (positions.isNotEmpty()) _error.value = null
                }
        }
    }

    fun onVehicleSelected(vehicle: VehiclePosition) {
        if (_selectedVehicleId.value == vehicle.vehicleId) {
            clearSelection()
            return
        }
        _selectedVehicleId.value = vehicle.vehicleId
        _routeDisplay.value = null
        routeLoadJob?.cancel()
        routeLoadJob = viewModelScope.launch {
            val route = vehicle.tripId?.let { routeDisplayRepository.loadRouteForTrip(it) }
            if (_selectedVehicleId.value == vehicle.vehicleId) {
                _routeDisplay.value = route
            }
        }
    }

    fun clearSelection() {
        _selectedVehicleId.value = null
        _routeDisplay.value = null
        routeLoadJob?.cancel()
    }
}

/**
 * The route overlay to render: only when a route is loaded and its vehicle is still selected
 * and present in the latest feed. Mirrors the info card's presence gate so a feed update that
 * drops the vehicle hides the route instead of leaving it stale on the map.
 */
fun visibleRouteDisplay(
    routeDisplay: RouteDisplayData?,
    vehicles: List<VehiclePosition>,
    selectedVehicleId: String?,
): RouteDisplayData? =
    routeDisplay?.takeIf { selectedVehicleId != null && vehicles.any { it.vehicleId == selectedVehicleId } }
