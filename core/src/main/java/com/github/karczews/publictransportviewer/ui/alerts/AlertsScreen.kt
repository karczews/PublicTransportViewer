package com.github.karczews.publictransportviewer.ui.alerts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.karczews.publictransportviewer.data.model.AlertEffect
import com.github.karczews.publictransportviewer.data.model.ServiceAlert

@Composable
fun AlertsScreen(
    viewModel: AlertsViewModel,
    modifier: Modifier = Modifier,
) {
    val alerts by viewModel.alerts.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Text("Service Alerts", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            alerts.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No active alerts",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(alerts, key = { it.alertId }) { alert ->
                        AlertCard(alert)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlertCard(alert: ServiceAlert) {
    var expanded by rememberSaveable(alert.alertId) { mutableStateOf(false) }
    val effectColor = effectColor(alert.effect)

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(effectColor),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    effectLabel(alert.effect),
                    style = MaterialTheme.typography.labelSmall,
                    color = effectColor,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                alert.headerText.ifBlank { "Alert" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )

            if (alert.affectedRouteShortNames.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    alert.affectedRouteShortNames.forEach { name ->
                        RouteBadge(name)
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    if (alert.descriptionText.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            alert.descriptionText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (alert.affectedStopIds.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Affected stops: ${alert.affectedStopIds.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RouteBadge(name: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

private fun effectLabel(effect: AlertEffect): String = when (effect) {
    AlertEffect.NO_SERVICE -> "NO SERVICE"
    AlertEffect.REDUCED_SERVICE -> "REDUCED SERVICE"
    AlertEffect.SIGNIFICANT_DELAYS -> "DELAYS"
    AlertEffect.DETOUR -> "DETOUR"
    AlertEffect.ADDITIONAL_SERVICE -> "ADDITIONAL SERVICE"
    AlertEffect.MODIFIED_SERVICE -> "MODIFIED SERVICE"
    AlertEffect.STOP_MOVED -> "STOP MOVED"
    AlertEffect.OTHER_EFFECT -> "ALERT"
    AlertEffect.UNKNOWN_EFFECT -> "ALERT"
}

private fun effectColor(effect: AlertEffect): Color = when (effect) {
    AlertEffect.NO_SERVICE -> Color(0xFFB00020)
    AlertEffect.REDUCED_SERVICE -> Color(0xFFE65100)
    AlertEffect.SIGNIFICANT_DELAYS -> Color(0xFFFF6F00)
    AlertEffect.DETOUR -> Color(0xFF0277BD)
    AlertEffect.ADDITIONAL_SERVICE -> Color(0xFF2E7D32)
    AlertEffect.MODIFIED_SERVICE -> Color(0xFF6A1B9A)
    AlertEffect.STOP_MOVED -> Color(0xFF00838F)
    AlertEffect.OTHER_EFFECT -> Color(0xFF455A64)
    AlertEffect.UNKNOWN_EFFECT -> Color(0xFF455A64)
}
