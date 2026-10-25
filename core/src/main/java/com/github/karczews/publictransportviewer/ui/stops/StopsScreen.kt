package com.github.karczews.publictransportviewer.ui.stops

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.toColorInt
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.karczews.publictransportviewer.data.db.entity.StopEntity
import com.github.karczews.publictransportviewer.data.model.StopDeparture
import com.github.karczews.publictransportviewer.data.model.VehicleType
import java.util.Calendar
import kotlin.math.abs

@Composable
fun StopsScreen(
    viewModel: StopsViewModel,
    modifier: Modifier = Modifier,
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val stops by viewModel.stops.collectAsStateWithLifecycle()
    val selectedStop by viewModel.selectedStop.collectAsStateWithLifecycle()
    val departures by viewModel.departures.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Search stops") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("search_field"),
        )
        Spacer(Modifier.height(12.dp))

        if (selectedStop != null) {
            DeparturesSection(
                stop = selectedStop!!,
                departures = departures,
                onBack = viewModel::clearSelection,
            )
        } else {
            StopsList(
                stops = stops,
                query = query,
                onStopClick = viewModel::onStopSelected,
            )
        }
    }
}

@Composable
private fun StopsList(
    stops: List<StopEntity>,
    query: String,
    onStopClick: (StopEntity) -> Unit,
) {
    when {
        query.isBlank() -> Hint("Type a stop name to search")
        stops.isEmpty() -> Hint("No stops found")
        else -> LazyColumn(Modifier.fillMaxSize()) {
            items(stops, key = { it.stopId }) { stop ->
                StopRow(stop = stop, onClick = { onStopClick(stop) })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StopRow(stop: StopEntity, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().testTag("stop_row_${stop.stopId}")) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 4.dp)) {
            Text(stop.stopName, style = MaterialTheme.typography.bodyLarge)
            if (!stop.stopCode.isNullOrBlank()) {
                Text(
                    "Code ${stop.stopCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DeparturesSection(
    stop: StopEntity,
    departures: List<StopDeparture>,
    onBack: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("back_button")) { Text("< Back") }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(stop.stopName, style = MaterialTheme.typography.titleMedium)
            if (!stop.stopCode.isNullOrBlank()) {
                Text(
                    "Code ${stop.stopCode}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    if (departures.isEmpty()) {
        Hint("No departures in the next hour")
    } else {
        LazyColumn(Modifier.fillMaxSize()) {
            items(departures, key = { "${it.tripId}_${it.scheduledDepartureSecondsOfDay}" }) {
                DepartureRow(it)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun DepartureRow(departure: StopDeparture) {
    val color = parseRouteColor(departure.routeColor)
    val minutesAway = computeMinutesAway(
        scheduledSecondsOfDay = departure.scheduledDepartureSecondsOfDay,
        delaySeconds = departure.delaySeconds ?: 0,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                departure.routeShortName,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                departure.tripHeadsign ?: typeName(departure.vehicleType),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                formatScheduledTime(departure.scheduledDepartureSecondsOfDay),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (minutesAway <= 0) "now" else "$minutesAway min",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            departure.delaySeconds?.takeIf { it != 0 }?.let { delay ->
                val minutes = delay / 60
                val (sign, c) = if (delay > 0) "+" to Color(0xFFB00020) else "-" to Color(0xFF1B5E20)
                Text(
                    "$sign${abs(minutes)} min",
                    style = MaterialTheme.typography.bodySmall,
                    color = c,
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun typeName(type: VehicleType): String = when (type) {
    VehicleType.TRAM -> "Tram"
    VehicleType.BUS -> "Bus"
}

private fun parseRouteColor(hex: String?): Color {
    if (hex.isNullOrBlank()) return Color(0xFF455A64)
    return runCatching {
        val cleaned = hex.removePrefix("#")
        Color("#$cleaned".toColorInt())
    }.getOrElse { Color(0xFF455A64) }
}

private fun formatScheduledTime(secondsOfDay: Int): String {
    val normalized = secondsOfDay % (24 * 3600)
    val h = normalized / 3600
    val m = (normalized % 3600) / 60
    return "%02d:%02d".format(h, m)
}

private fun computeMinutesAway(scheduledSecondsOfDay: Int, delaySeconds: Int): Int {
    val cal = Calendar.getInstance()
    val nowSeconds = cal.get(Calendar.HOUR_OF_DAY) * 3600 +
        cal.get(Calendar.MINUTE) * 60 +
        cal.get(Calendar.SECOND)
    return ((scheduledSecondsOfDay + delaySeconds - nowSeconds) / 60).coerceAtLeast(0)
}
