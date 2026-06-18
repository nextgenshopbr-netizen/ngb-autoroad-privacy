package com.ngbautoroad.ui.history

// ============================================================================
// ARQUIVO: HistoryTab.kt
// LOCALIZAÇÃO: ui/history/HistoryTab.kt
// RESPONSABILIDADE: Histórico de corridas com busca, filtros, detalhes e exportação CSV
// COMPOSABLES:
//   - HistoryTab (L47): Tela principal com Flow reativo, busca e filtros
//   - RideHistoryItem (L256): Card individual de corrida no histórico
//   - RideDetailDialog (L395): Dialog com detalhes completos + scoreBreakdown
// DEPENDÊNCIAS:
//   - data/db/RideHistoryEntity.kt → RideHistoryDao, RideHistoryEntity
//   - data/prefs/PrefsManager.kt → preferências de filtro
//   - data/db/AppDatabase.kt → instância do banco
// PROTEÇÕES:
//   - LazyColumn com key(ride.id) para performance
//   - Flow reativo: atualiza automaticamente quando novas corridas são salvas
//   - Exportação CSV com tratamento de caracteres especiais
// ============================================================================

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ngbautoroad.data.db.AppDatabase
import com.ngbautoroad.data.db.RideHistoryEntity
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
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
    val dao = remember { database.rideHistoryDao() }

    var selectedFilter by remember { mutableStateOf(HistoryFilter.TODAY) }
    var searchQuery by remember { mutableStateOf("") }
    var showSearch by remember { mutableStateOf(false) }
    var selectedRide by remember { mutableStateOf<RideHistoryEntity?>(null) }

    // Item 5.1: Histórico reativo via Flow — atualiza automaticamente quando novas corridas chegam
    val rides by remember(selectedFilter, searchQuery) {
        val baseFlow: Flow<List<RideHistoryEntity>> = if (searchQuery.isNotBlank()) {
            dao.searchByNeighborhoodFlow(searchQuery)
        } else {
            when (selectedFilter) {
                HistoryFilter.TODAY -> dao.getSinceFlow(getStartOfDay())
                HistoryFilter.WEEK -> dao.getSinceFlow(getStartOfWeek())
                HistoryFilter.MONTH -> dao.getSinceFlow(getStartOfMonth())
                HistoryFilter.ALL -> dao.getAllFlow()
                HistoryFilter.ACCEPTED -> dao.getByStatusFlow("ACCEPTED")
                HistoryFilter.REFUSED -> dao.getByStatusFlow("REFUSED")
                HistoryFilter.CANCELLED -> dao.getByStatusFlow("CANCELLED")
            }
        }
        baseFlow
    }.collectAsState(initial = emptyList())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header com busca
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Histórico",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Row {
                // Item 5.3: Busca por bairro
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(
                        if (showSearch) Icons.Default.SearchOff else Icons.Default.Search,
                        contentDescription = "Buscar",
                        modifier = Modifier.size(20.dp)
                    )
                }
                // Item 5.4: Exportação CSV
                IconButton(onClick = {
                    scope.launch {
                        exportToCsv(rides)
                    }
                }) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Exportar CSV", modifier = Modifier.size(20.dp))
                }
            }
        }

        // Campo de busca
        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar por bairro") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpar")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                singleLine = true
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Filtros de período
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(HistoryFilter.values().toList()) { filter ->
                FilterChip(
                    selected = selectedFilter == filter && searchQuery.isBlank(),
                    onClick = {
                        selectedFilter = filter
                        searchQuery = ""
                        showSearch = false
                    },
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

        Spacer(modifier = Modifier.height(8.dp))

        // Summary card
        if (rides.isNotEmpty()) {
            val accepted = rides.filter { it.status == "ACCEPTED" }
            val totalValue = accepted.sumOf { it.rideValue }
            val avgScore = rides.map { it.score }.average()
            val avgVkm = accepted.filter { it.dropoffDistance > 0 }.map { it.valuePerKm }.let {
                if (it.isEmpty()) 0.0 else it.average()
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${rides.size}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("corridas", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("R$ %.2f".format(totalValue), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ScoreGreen)
                        Text("ganhos", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.0f".format(avgScore), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = ScoreYellow)
                        Text("score médio", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("%.2f".format(avgVkm), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("R$/km", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }

        // Lista
        if (rides.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
                        if (searchQuery.isNotBlank()) "Nenhum resultado para \"$searchQuery\""
                        else "Nenhuma corrida encontrada",
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
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(rides, key = { it.id }) { ride ->
                    RideHistoryItem(
                        ride = ride,
                        onClick = { selectedRide = ride }
                    )
                }
            }
        }
    }

    // Item 5.2: Detalhe da corrida
    selectedRide?.let { ride ->
        RideDetailDialog(ride = ride, onDismiss = { selectedRide = null })
    }
}

@Composable
fun RideHistoryItem(ride: RideHistoryEntity, onClick: () -> Unit) {
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Score circle
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(23.dp))
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

            Spacer(modifier = Modifier.width(10.dp))

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
                    if (ride.dropoffDistance > 0) {
                        Text(
                            String.format("%.2f R$/km • %.1f km", ride.valuePerKm, ride.dropoffDistance),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (ride.pickupNeighborhood.isNotBlank() || ride.dropoffNeighborhood.isNotBlank()) {
                        Text(
                            "${ride.pickupNeighborhood.ifBlank { "?" }} → ${ride.dropoffNeighborhood.ifBlank { "?" }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ride.hasViolations) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = "Violações",
                                modifier = Modifier.size(12.dp),
                                tint = ScoreOrange
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                        }
                        Text(
                            statusLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = statusColor
                        )
                    }
                }

                // Indicador de confiança do score (item 1.3)
                if (ride.totalCriteria > 0 && ride.criteriaUsed < ride.totalCriteria) {
                    Text(
                        "Dados parciais (${ride.criteriaUsed}/${ride.totalCriteria} critérios)",
                        style = MaterialTheme.typography.labelSmall,
                        color = ScoreYellow.copy(alpha = 0.8f),
                        fontSize = 9.sp
                    )
                }
            }
        }
    }
}

// Item 5.2: Dialog de detalhes da corrida
@Composable
fun RideDetailDialog(ride: RideHistoryEntity, onDismiss: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()) }
    val scoreColor = when {
        ride.score >= 70 -> ScoreGreen
        ride.score >= 50 -> ScoreYellow
        ride.score >= 30 -> ScoreOrange
        else -> ScoreRed
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Detalhes da Corrida",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(scoreColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            String.format("%.0f", ride.score),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = scoreColor
                        )
                    }
                }

                Divider()

                // Dados principais
                DetailRow("Plataforma", ride.platform.ifBlank { "—" })
                DetailRow("Data/Hora", dateFormat.format(Date(ride.timestamp)))
                DetailRow("Status", when (ride.status) {
                    "ACCEPTED" -> "✅ Aceita"
                    "REFUSED" -> "❌ Recusada"
                    "CANCELLED" -> "⚠️ Cancelada"
                    else -> ride.status
                })

                Divider()

                // Dados da corrida
                if (ride.rideValue > 0) DetailRow("Valor", "R$ %.2f".format(ride.rideValue))
                if (ride.dropoffDistance > 0) {
                    DetailRow("Distância", "%.1f km".format(ride.dropoffDistance))
                    DetailRow("R$/km", "%.2f".format(ride.valuePerKm))
                }
                if (ride.rideDuration > 0) {
                    DetailRow("Duração", "%.0f min".format(ride.rideDuration))
                    if (ride.rideDuration > 0) {
                        val vph = if (ride.rideDuration > 0) (ride.rideValue / ride.rideDuration) * 60.0 else 0.0
                        DetailRow("R$/hora", "%.2f".format(vph))
                    }
                }
                if (ride.pickupDistance > 0) DetailRow("Dist. embarque", "%.1f km".format(ride.pickupDistance))
                if (ride.passengerRating > 0) DetailRow("Avaliação", "%.1f ★".format(ride.passengerRating))
                if (ride.intermediateStops > 0) DetailRow("Paradas", "${ride.intermediateStops}")
                if (ride.pickupNeighborhood.isNotBlank()) DetailRow("Embarque", ride.pickupNeighborhood)
                if (ride.dropoffNeighborhood.isNotBlank()) DetailRow("Destino", ride.dropoffNeighborhood)

                // Confiança do score
                if (ride.totalCriteria > 0) {
                    Divider()
                    DetailRow("Critérios usados", "${ride.criteriaUsed} de ${ride.totalCriteria}")
                    DetailRow("Confiança", "${ride.confidencePercent}%")
                    if (ride.hasViolations) {
                        Text(
                            "⚠️ Esta corrida violou um ou mais critérios mínimos",
                            style = MaterialTheme.typography.labelSmall,
                            color = ScoreOrange
                        )
                    }
                }

                // Breakdown do score (se disponível)
                if (ride.scoreBreakdown.isNotBlank()) {
                    Divider()
                    Text(
                        "Detalhamento do Score",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        ride.scoreBreakdown,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Fechar")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

// Funções auxiliares de data
private fun getStartOfDay(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

private fun getStartOfWeek(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
    return cal.timeInMillis
}

private fun getStartOfMonth(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    return cal.timeInMillis
}

// Item 5.4: Exportação CSV
private suspend fun exportToCsv(rides: List<RideHistoryEntity>) {
    withContext(Dispatchers.IO) {
        try {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val sb = StringBuilder()
            sb.appendLine("Data,Plataforma,Valor,Distância,Duração,R$/km,Avaliação,Paradas,Embarque,Destino,Score,Status")
            for (ride in rides) {
                sb.appendLine(
                    "${dateFormat.format(Date(ride.timestamp))}," +
                    "${ride.platform}," +
                    "R$ %.2f,".format(ride.rideValue) +
                    "%.1f km,".format(ride.dropoffDistance) +
                    "%.0f min,".format(ride.rideDuration) +
                    "%.2f,".format(ride.valuePerKm) +
                    "%.1f,".format(ride.passengerRating) +
                    "${ride.intermediateStops}," +
                    "${ride.pickupNeighborhood}," +
                    "${ride.dropoffNeighborhood}," +
                    "%.0f,".format(ride.score) +
                    ride.status
                )
            }
            val file = java.io.File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                "ngb_historico_${System.currentTimeMillis()}.csv"
            )
            file.writeText(sb.toString())
        } catch (_: Exception) {}
    }
}
