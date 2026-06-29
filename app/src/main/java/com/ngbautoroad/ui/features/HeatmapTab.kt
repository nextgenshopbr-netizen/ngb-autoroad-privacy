package com.ngbautoroad.ui.features

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// ============================================================================
// ARQUIVO: HeatmapTab.kt
// RESPONSABILIDADE: Exibir Heatmap de lucratividade com base nos dados históricos (IA Sprint 3)
// ============================================================================

@Composable
fun HeatmapTab() {

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Heatmap de Lucratividade",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = "Cruza dados históricos de corridas para identificar as melhores zonas, horários e dias da semana.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // TODO: Implementar heatmap visual real com dados do banco de corridas
        Card(
            modifier = Modifier.fillMaxWidth().weight(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(contentAlignment = androidx.compose.ui.Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    Text(
                        "Em desenvolvimento",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "O mapa de calor visual sera disponibilizado em breve.\n" +
                            "Ele cruzara seus dados historicos de corridas para\n" +
                            "identificar zonas, horarios e dias mais lucrativos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

