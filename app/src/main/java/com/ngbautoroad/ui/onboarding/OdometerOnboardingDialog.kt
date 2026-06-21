package com.ngbautoroad.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ngbautoroad.data.db.FinanceDatabase
import com.ngbautoroad.data.db.VehicleProfileEntity
import com.ngbautoroad.data.prefs.PrefsManager
import kotlinx.coroutines.launch

/**
 * Dialog de onboarding obrigatório que aparece quando:
 * 1. Nenhum veículo cadastrado com odômetro > 0
 * 2. Primeiro uso do app
 *
 * Não pode ser dispensado sem informar o odômetro.
 * Resolve RUPTURA #1 (Cold Start): app inútil sem odômetro inicial.
 *
 * v6.7.0: Corrigido edge case — se não existe veículo ativo, o dialog
 * NÃO marca onboarding como completo. Mostra mensagem orientando
 * o motorista a cadastrar o veículo primeiro.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun OdometerOnboardingDialog(
    prefsManager: PrefsManager,
    financeDb: FinanceDatabase,
    onComplete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var odometerInput by remember { mutableStateOf("") }
    var isNewCar by remember { mutableStateOf<Boolean?>(null) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var hasActiveVehicle by remember { mutableStateOf<Boolean?>(null) }

    // Verificar se existe veículo ativo
    LaunchedEffect(Unit) {
        val vehicle = financeDb.vehicleProfileDao().getActiveVehicleSync()
        hasActiveVehicle = vehicle != null
    }

    AlertDialog(
        onDismissRequest = { /* Não pode dispensar — obrigatório */ },
        icon = {
            Icon(
                Icons.Default.Speed,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                "Configuração Inicial",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (hasActiveVehicle == false) {
                    // Sem veículo cadastrado — orientar a cadastrar primeiro
                    Text(
                        "Antes de configurar o odômetro, você precisa cadastrar seu veículo.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Vá em Finanças → Veículos e adicione seu carro. " +
                        "Depois este diálogo aparecerá novamente para configurar o odômetro.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    // Veículo existe — pedir odômetro
                    Text(
                        "Para que o sistema de manutenção e financeiro funcione corretamente, " +
                        "precisamos saber a quilometragem atual do seu veículo.",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        "Seu carro é zero km?",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = isNewCar == true,
                            onClick = {
                                isNewCar = true
                                odometerInput = "0"
                            },
                            label = { Text("Sim, é novo") }
                        )
                        FilterChip(
                            selected = isNewCar == false,
                            onClick = {
                                isNewCar = false
                                odometerInput = ""
                            },
                            label = { Text("Não, já tem km") }
                        )
                    }

                    if (isNewCar == false) {
                        OutlinedTextField(
                            value = odometerInput,
                            onValueChange = {
                                odometerInput = it.filter { c -> c.isDigit() }
                                showError = false
                            },
                            label = { Text("Quilometragem atual (km)") },
                            placeholder = { Text("Ex: 15000") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = showError
                        )
                        if (showError) {
                            Text(
                                errorMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Text(
                            "Consulte o painel do veículo e informe o valor exato.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    if (isNewCar == true) {
                        Text(
                            "Ótimo! O sistema começará a rastrear a partir de 0 km.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (hasActiveVehicle == false) {
                // Sem veículo — botão "Entendi" que NÃO marca onboarding como completo
                Button(
                    onClick = { onComplete() }
                ) {
                    Text("Entendi, vou cadastrar")
                }
            } else {
                Button(
                    onClick = {
                        val odometer = odometerInput.toIntOrNull() ?: -1
                        if (isNewCar == null) {
                            showError = true
                            errorMessage = "Selecione se o carro é novo ou não"
                            return@Button
                        }
                        if (isNewCar == false && odometer <= 0) {
                            showError = true
                            errorMessage = "Informe a quilometragem atual"
                            return@Button
                        }
                        val finalOdometer = if (isNewCar == true) 0 else odometer
                        scope.launch {
                            // Atualizar o veículo ativo com o odômetro
                            val vehicleDao = financeDb.vehicleProfileDao()
                            val activeVehicle = vehicleDao.getActiveVehicleSync()
                            if (activeVehicle != null) {
                                vehicleDao.updateOdometer(
                                    activeVehicle.id,
                                    finalOdometer,
                                    System.currentTimeMillis()
                                )
                                // Marcar onboarding como completo SOMENTE se salvou com sucesso
                                prefsManager.setOdometerOnboardingDone(true)
                            }
                            onComplete()
                        }
                    },
                    enabled = isNewCar != null && (isNewCar == true || (odometerInput.toIntOrNull() ?: 0) > 0)
                ) {
                    Text("Confirmar")
                }
            }
        },
        dismissButton = null // Não pode dispensar
    )
}
