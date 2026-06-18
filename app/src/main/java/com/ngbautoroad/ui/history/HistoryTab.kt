package com.ngbautoroad.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.db.RideHistoryEntity
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

enum class HistoryFilter(val label: String) {
    TODAY("Hoje"),
    WEEK("Semana"),
    MONTH("Mês"),
    ALL("Todos"),
    ACCEPTED("Aceitas"),
    REFUSED("Recusadas"),
    CANCELLED("Canceladas")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTab(prefsManager: PrefsManager, database: AppDatabase) {
    val scope = rememberCoroutineScope()
    var rides by remember { mutableStateOf<List<RideHistoryEntity>>(emptyList()) }
    var selectedFilter by remember { mutableStateOf(HistoryFilter.TODAY) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun getStartOfDay(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getStartOfWeek(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        return cal.timeInMillis
    }

    fun getStartOfMonth(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis
    }

    fun loadData() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                val dao = database.rideHistoryDao()
                val result = withContext(Dispatchers.IO) {
                    when (selectedFilter) {
                        HistoryFilter.TODAY -> dao.getSince(getStartOfDay())
                        HistoryFilter.WEEK -> dao.getSince(getStartOfWeek())
                        HistoryFilter.MONTH -> dao.getSince(getStartOfMonth())
                        HistoryFilter.ALL -> dao.getAll()
                        HistoryFilter.ACCEPTED -> dao.getByStatus("ACCEPTED")
                        HistoryFilter.REFUSED -> dao.getByStatus("REFUSED")
                        HistoryFilter.CANCELLED -> dao.getByStatus("CANCELLED")
                    }
                }
                rides = result
            } catch (e: Exception) {
                errorMessage = "Erro ao carregar: ${e.message}"
                rides = emptyList()
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(selectedFilter) {
        loadData()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Filter chips - Period
        Text(
            "Filtros",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Period filters - FlowRow style (wrap content)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(HistoryFilter.TODAY, HistoryFilter.WEEK, HistoryFilter.MONTH, HistoryFilter.ALL).forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label, fontSize = 11.sp) },
                    modifier = Modifier.height(32.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Status filters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(HistoryFilter.ACCEPTED, HistoryFilter.REFUSED, HistoryFilter.CANCELLED).forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label, fontSize = 11.sp) },
                    modifier = Modifier.height(32.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = when (filter) {
                            HistoryFilter.ACCEPTED -> ScoreGreen.copy(alpha = 0.2f)
                            HistoryFilter.REFUSED -> ScoreRed.copy(alpha = 0.2f)
                            HistoryFilter.CANCELLED -> ScoreOrange.copy(alpha = 0.2f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        selectedLabelColor = when (filter) {
                            HistoryFilter.ACCEPTED -> ScoreGreen
                            HistoryFilter.REFUSED -> ScoreRed
                            HistoryFilter.CANCELLED -> ScoreOrange
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        }
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "${rides.size} corridas",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                if (rides.isNotEmpty()) {
                    val totalValue = rides.filter { it.status == "ACCEPTED" }.sumOf { it.rideValue }
                    Text(
                        String.format("Total: R$ %.2f", totalValue),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = ScoreGreen
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Error message
        errorMessage?.let { msg ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = ScoreRed.copy(alpha = 0.1f))
            ) {
                Text(
                    msg,
                    modifier = Modifier.padding(12.dp),
                    color = ScoreRed,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // List
        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (rides.isEmpty() && errorMessage == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Nenhuma corrida encontrada",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "As corridas detectadas aparecerão aqui",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rides) { ride ->
                    RideHistoryItem(ride = ride)
                }
            }
        }
    }
}

@Composable
fun RideHistoryItem(ride: RideHistoryEntity) {
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val scoreColor = when {
        ride.score >= 70 -> ScoreGreen
        ride.score >= 50 -> ScoreYellow
        ride.score >= 30 -> ScoreOrange
        else -> ScoreRed
    }
    val statusColor = when (ride.status) {
        "ACCEPTED" -> ScoreGreen
        "REFUSED" -> ScoreRed
        "CANCELLED" -> ScoreOrange
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = when (ride.status) {
        "ACCEPTED" -> "Aceita"
        "REFUSED" -> "Recusada"
        "CANCELLED" -> "Cancelada"
        "EXPIRED" -> "Expirada"
        else -> ride.status
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Score circle
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(scoreColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    String.format("%.0f", ride.score),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scoreColor
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        ride.platform.ifBlank { "—" },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        dateFormat.format(Date(ride.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        String.format("R$ %.2f", ride.rideValue),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        String.format("%.2f R$/km", ride.valuePerKm),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (ride.pickupNeighborhood.isNotBlank() || ride.dropoffNeighborhood.isNotBlank()) {
                        Text(
                            "${ride.pickupNeighborhood} → ${ride.dropoffNeighborhood}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
        }
    }
}
