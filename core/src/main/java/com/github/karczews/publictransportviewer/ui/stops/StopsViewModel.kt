package com.github.karczews.publictransportviewer.ui.stops

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.karczews.publictransportviewer.data.db.entity.StopEntity
import com.github.karczews.publictransportviewer.data.model.StopDeparture
import com.github.karczews.publictransportviewer.data.repository.StopRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.annotation.KoinViewModel

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@KoinViewModel
class StopsViewModel(
    private val repository: StopRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedStop = MutableStateFlow<StopEntity?>(null)
    val selectedStop: StateFlow<StopEntity?> = _selectedStop.asStateFlow()

    val stops: StateFlow<List<StopEntity>> = _query
        .debounce(300)
        .mapLatest { q -> repository.searchStops(q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var departuresJob: Job? = null
    private val _departures = MutableStateFlow<List<StopDeparture>>(emptyList())
    val departures: StateFlow<List<StopDeparture>> = _departures.asStateFlow()

    fun onQueryChange(text: String) {
        _query.value = text
        if (text.isBlank()) {
            _selectedStop.value = null
            _departures.value = emptyList()
            departuresJob?.cancel()
        }
    }

    fun onStopSelected(stop: StopEntity) {
        if (_selectedStop.value?.stopId == stop.stopId) return
        _selectedStop.value = stop
        departuresJob?.cancel()
        departuresJob = viewModelScope.launch {
            repository.observeDeparturesForStop(stop.stopId).collect { _departures.value = it }
        }
    }

    fun clearSelection() {
        _selectedStop.value = null
        _departures.value = emptyList()
        departuresJob?.cancel()
        departuresJob = null
    }
}
