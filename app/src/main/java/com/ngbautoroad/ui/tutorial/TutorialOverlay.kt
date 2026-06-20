package com.ngbautoroad.ui.tutorial

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngbautoroad.data.prefs.PrefsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ============================================================================
// TUTORIAL GUIADO — v6.3.0
// Exibe tooltips interativos no primeiro acesso a cada tela.
// Cada tela tem uma lista de TutorialStep que são exibidos em sequência.
// O progresso é salvo no PrefsManager (tutorialCompletedScreens).
// ============================================================================

data class TutorialStep(
    val title: String,
    val description: String,
    val icon: ImageVector = Icons.Default.Info,
    val highlightArea: String = "" // Identificador da área destacada (para futuro uso)
)

/**
 * Composable que exibe o tutorial guiado para uma tela específica.
 * Mostra um card flutuante com passos sequenciais.
 *
 * @param screenId Identificador único da tela (ex: "dashboard", "criteria", "settings")
 * @param steps Lista de passos do tutorial para esta tela
 * @param prefsManager Para verificar/salvar progresso
 * @param onDismiss Callback quando o tutorial é fechado
 */
@Composable
fun TutorialOverlay(
    screenId: String,
    steps: List<TutorialStep>,
    prefsManager: PrefsManager,
    onDismiss: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var currentStep by remember { mutableStateOf(0) }
    var showTutorial by remember { mutableStateOf(false) }

    // Verificar se já completou o tutorial desta tela
    LaunchedEffect(screenId) {
        val completed = prefsManager.tutorialCompletedScreensFlow.first()
        showTutorial = !completed.contains(screenId)
    }

    if (showTutorial && steps.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { /* Bloquear cliques no fundo */ },
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Indicador de progresso
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        steps.forEachIndexed { index, _ ->
                            Box(
                                modifier = Modifier
                                    .size(if (index == currentStep) 10.dp else 8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index <= currentStep) MaterialTheme.colorScheme.primary
                                        else Color.Gray.copy(alpha = 0.3f)
                                    )
                            )
                            if (index < steps.size - 1) Spacer(modifier = Modifier.width(6.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Ícone
                    Icon(
                        steps[currentStep].icon,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Título
                    Text(
                        steps[currentStep].title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Descrição
                    Text(
                        steps[currentStep].description,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Botões
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Botão Pular
                        TextButton(onClick = {
                            scope.launch {
                                prefsManager.markTutorialCompleted(screenId)
                            }
                            showTutorial = false
                            onDismiss()
                        }) {
                            Text("Pular")
                        }

                        // Botão Próximo / Concluir
                        Button(onClick = {
                            if (currentStep < steps.size - 1) {
                                currentStep++
                            } else {
                                scope.launch {
                                    prefsManager.markTutorialCompleted(screenId)
                                }
                                showTutorial = false
                                onDismiss()
                            }
                        }) {
                            Text(if (currentStep < steps.size - 1) "Próximo" else "Entendi!")
                        }
                    }

                    // Contador
                    Text(
                        "${currentStep + 1} de ${steps.size}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

// ============================================================================
// DEFINIÇÕES DE TUTORIAL POR TELA
// ============================================================================

object TutorialContent {

    val dashboardSteps = listOf(
        TutorialStep(
            title = "Bem-vindo ao NGB AutoRoad!",
            description = "Este é o seu painel principal. Aqui você vê o resumo do dia: corridas, ganhos e score médio.",
            icon = Icons.Default.Home
        ),
        TutorialStep(
            title = "Perfis Rápidos",
            description = "Selecione um perfil de critérios antes de iniciar o turno. Cada perfil tem pesos e configurações diferentes (Dia, Noite, Fim de Semana).",
            icon = Icons.Default.Person
        ),
        TutorialStep(
            title = "Iniciar Turno",
            description = "Toque em 'Iniciar Turno' para começar a contabilizar corridas e ganhos do período. Ao finalizar, você terá um resumo completo.",
            icon = Icons.Default.PlayArrow
        ),
        TutorialStep(
            title = "Score de Corrida",
            description = "Quando uma corrida chegar, o app calcula automaticamente um score de 0 a 100 baseado nos seus critérios. Quanto maior, melhor a corrida para você!",
            icon = Icons.Default.Star
        )
    )

    val criteriaSteps = listOf(
        TutorialStep(
            title = "Critérios de Avaliação",
            description = "Aqui você configura COMO o app avalia cada corrida. Defina pesos para valor/km, duração, distância e mais.",
            icon = Icons.Default.Tune
        ),
        TutorialStep(
            title = "Aba Painel",
            description = "Visão geral com perfis salvos, status da IA e acesso rápido às configurações mais usadas.",
            icon = Icons.Default.Dashboard
        ),
        TutorialStep(
            title = "Aba Pesos e Valores",
            description = "Ajuste os sliders para definir a importância de cada critério. A soma deve ser 100 pontos. Defina também valores mínimos aceitáveis.",
            icon = Icons.Default.Balance
        ),
        TutorialStep(
            title = "Aba AutoPilot",
            description = "Configure a aceitação/recusa automática. Defina o score mínimo para aceitar e máximo para recusar. O app decide por você!",
            icon = Icons.Default.SmartToy
        )
    )

    val settingsSteps = listOf(
        TutorialStep(
            title = "Configurações",
            description = "Personalize o app conforme sua necessidade. Divido em 3 abas: App, Sistema e Adicionais.",
            icon = Icons.Default.Settings
        ),
        TutorialStep(
            title = "Aba Sistema",
            description = "Controle o Ghost Mode (oculta apps bancários), serviços de acessibilidade e o botão flutuante lateral.",
            icon = Icons.Default.Security
        ),
        TutorialStep(
            title = "Backup e Restauração",
            description = "Na aba Adicionais, faça backup de todas as suas configurações e restaure quando trocar de celular.",
            icon = Icons.Default.Backup
        )
    )

    val financeSteps = listOf(
        TutorialStep(
            title = "Controle Financeiro",
            description = "Gerencie ganhos, despesas, veículos e veja seu lucro real. Tudo calculado automaticamente.",
            icon = Icons.Default.AccountBalanceWallet
        ),
        TutorialStep(
            title = "Ganhos Automáticos",
            description = "Quando o AutoImport está ativo, cada corrida concluída é registrada automaticamente como ganho.",
            icon = Icons.Default.AutoAwesome
        ),
        TutorialStep(
            title = "Despesas e Veículos",
            description = "Registre combustível, manutenção e outros custos. O app calcula seu lucro líquido real.",
            icon = Icons.Default.Receipt
        )
    )

    val cardsSteps = listOf(
        TutorialStep(
            title = "Galeria de Cards",
            description = "Personalize a aparência do card de corrida que aparece na tela. Escolha entre diversos designs.",
            icon = Icons.Default.Style
        ),
        TutorialStep(
            title = "Simulação",
            description = "Use o botão 'Simular' para testar como o card aparece com dados fictícios, sem precisar esperar uma corrida real.",
            icon = Icons.Default.PlayCircle
        )
    )
}
