package com.ngbautoroad.ui.tutorial

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ============================================================================
// CENTRAL DE AJUDA — v6.3.0
// Ícone "?" no topo de cada aba que abre um BottomSheet com FAQ e explicações.
// ============================================================================

data class HelpItem(
    val question: String,
    val answer: String,
    val icon: ImageVector = Icons.Default.HelpOutline
)

/**
 * Botão de ajuda (ícone ?) que abre o BottomSheet.
 * Usar no topo de cada tela principal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpButton(screenId: String) {
    var showSheet by remember { mutableStateOf(false) }

    IconButton(onClick = { showSheet = true }) {
        Icon(Icons.Default.HelpOutline, contentDescription = "Ajuda", tint = MaterialTheme.colorScheme.primary)
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState) {
            Surface(color = MaterialTheme.colorScheme.surface) {
                HelpContent(screenId = screenId, onDismiss = { showSheet = false })
            }
        }
    }
}

@Composable
private fun HelpContent(screenId: String, onDismiss: () -> Unit) {
    val items = HelpData.getForScreen(screenId)

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Central de Ajuda", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                TextButton(onClick = onDismiss) { Text("Fechar") }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
        items(items) { item ->
            HelpItemCard(item)
        }
        item { Spacer(modifier = Modifier.height(32.dp)) }
    }
}
@OptIn(ExperimentalMaterial3Api::class)

@Composable
private fun HelpItemCard(item: HelpItem) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(item.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(item.question, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = Color.Gray
                )
            }
            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(item.answer, fontSize = 13.sp, color = Color.Gray, lineHeight = 18.sp)
            }
        }
    }
}

// ============================================================================
// CONTEÚDO DE AJUDA POR TELA
// ============================================================================

object HelpData {
    fun getForScreen(screenId: String): List<HelpItem> = when (screenId) {
        "dashboard" -> dashboardHelp
        "criteria" -> criteriaHelp
        "cards" -> cardsHelp
        "finance" -> financeHelp
        "settings" -> settingsHelp
        else -> generalHelp
    }

    private val dashboardHelp = listOf(
        HelpItem(
            "O que é o Score?",
            "O Score é uma nota de 0 a 100 que o app calcula para cada corrida baseado nos seus critérios pessoais (valor/km, distância, duração, bairro). Quanto maior, melhor a corrida para você.",
            Icons.Default.Star
        ),
        HelpItem(
            "Como funciona o Turno?",
            "Ao iniciar o turno, o app contabiliza todas as corridas e ganhos do período. Ao finalizar, você vê um resumo completo com total ganho, corridas feitas e score médio.",
            Icons.Default.Timer
        ),
        HelpItem(
            "O que são Perfis?",
            "Perfis são conjuntos de configurações salvas. Exemplo: 'Noturno' com peso maior em segurança, 'Dia' com peso em valor/km. Troque com 1 toque antes de começar a rodar.",
            Icons.Default.Person
        ),
        HelpItem(
            "Por que meus ganhos estão zerados?",
            "Ganhos só são registrados quando a corrida é confirmada como CONCLUÍDA. Se você recusou ou a corrida foi cancelada, não conta. Verifique se o AutoImport está ativo nas Configurações.",
            Icons.Default.AttachMoney
        )
    )

    private val criteriaHelp = listOf(
        HelpItem(
            "Como ajustar os pesos?",
            "Na aba 'Pesos', use os sliders para definir a importância de cada critério. A soma deve ser 100 pontos. Exemplo: Valor/Km = 35, Duração = 25, Distância = 20, Bairro = 20.",
            Icons.Default.Tune
        ),
        HelpItem(
            "O que é a Zona Neutra do AutoPilot?",
            "É a faixa de score onde o app NÃO decide por você. Exemplo: aceita acima de 75, recusa abaixo de 40, entre 40-75 é zona neutra — você decide.",
            Icons.Default.SmartToy
        ),
        HelpItem(
            "Como funcionam os Mapas e Zonas?",
            "Desenhe áreas no mapa que você quer evitar. Quando uma corrida tem destino em uma zona bloqueada, o score é penalizado automaticamente.",
            Icons.Default.Map
        ),
        HelpItem(
            "O que é o AutoPilot?",
            "O AutoPilot aceita ou recusa corridas automaticamente baseado no score. Você pode ativar 'Aceitar' (aceita boas corridas), 'Recusar' (recusa ruins) ou ambos.",
            Icons.Default.SmartToy
        ),
        HelpItem(
            "Os pesos salvam automaticamente?",
            "Sim! Toda alteração nos sliders é salva automaticamente. Mas para criar um PERFIL nomeado, use a aba Painel e toque em 'Salvar Perfil'.",
            Icons.Default.Save
        )
    )

    private val cardsHelp = listOf(
        HelpItem(
            "O que é o Card de Corrida?",
            "É a janela flutuante que aparece sobre outros apps quando uma corrida é detectada. Mostra o score, valor, distância e recomendação.",
            Icons.Default.Style
        ),
        HelpItem(
            "Posso personalizar o visual?",
            "Sim! Na Galeria de Cards, escolha entre diversos designs. Cada um mostra as mesmas informações de forma diferente.",
            Icons.Default.Palette
        ),
        HelpItem(
            "Como testar sem esperar corrida?",
            "Use o botão 'Simular' para ver como o card aparece com dados fictícios. Útil para testar designs e configurações.",
            Icons.Default.PlayCircle
        )
    )

    private val financeHelp = listOf(
        HelpItem(
            "Como funciona o AutoImport?",
            "Quando ativo, cada corrida CONCLUÍDA é automaticamente registrada como ganho no módulo financeiro. Você não precisa digitar nada.",
            Icons.Default.AutoAwesome
        ),
        HelpItem(
            "Posso registrar despesas?",
            "Sim! Registre combustível, manutenção, seguro e outros custos. O app calcula seu lucro líquido real (ganhos - despesas).",
            Icons.Default.Receipt
        ),
        HelpItem(
            "Como ver relatórios?",
            "Acesse Recursos Avançados > Relatório para gerar PDFs com resumo financeiro. Ou exporte para CSV para usar em planilhas.",
            Icons.Default.Assessment
        )
    )

    private val settingsHelp = listOf(
        HelpItem(
            "O que é o Ghost Mode?",
            "Oculta apps bancários quando você está em corrida. Protege sua privacidade financeira de passageiros que possam ver sua tela.",
            Icons.Default.VisibilityOff
        ),
        HelpItem(
            "O que é o Botão Flutuante?",
            "É um botão lateral na tela que dá acesso rápido ao app. Desativá-lo NÃO afeta o AutoPilot ou a detecção de corridas — é apenas conveniência visual.",
            Icons.Default.RadioButtonChecked
        ),
        HelpItem(
            "Como fazer backup?",
            "Na aba 'Adicionais', toque em 'Backup'. Todas as configurações, perfis e critérios são salvos. Para restaurar em outro celular, use 'Restaurar'.",
            Icons.Default.Backup
        ),
        HelpItem(
            "Preciso dar todas as permissões?",
            "As permissões essenciais são: Acessibilidade (detectar corridas), Notificações (canal primário) e Overlay (mostrar card). Sem elas, funções principais não operam.",
            Icons.Default.Security
        )
    )

    private val generalHelp = listOf(
        HelpItem(
            "Como o app funciona?",
            "O NGB AutoRoad detecta corridas dos apps de motorista, calcula um score baseado nos seus critérios e pode aceitar/recusar automaticamente. Tudo 100% local, sem enviar dados para nuvem.",
            Icons.Default.Info
        )
    )
}
