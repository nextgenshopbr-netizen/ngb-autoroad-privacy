package com.ngbautoroad.ui.card

// ============================================================================
// ARQUIVO: CardTab.kt
// LOCALIZAÇÃO: ui/card/CardTab.kt
// RESPONSABILIDADE: Gerenciamento de cards visuais (2 slots) + simulação
// COMPOSABLES:
//   - CardTab (L33-230): Tela principal com 2 slots de card e botões
//   - CardSlotItem (L233-303): Card individual com preview e ações
//   - GalleryDialog (L305-406): Dialog de galeria para escolher card
//   - GalleryCardItem (L408-474): Item da galeria com preview
//   - RenameDialog (L476-507): Dialog para renomear card
//   - PreviewDialog (L509-658): Simulação com dados aleatórios + critérios reais
//   - PreviewField (L660-671): Campo individual no preview
//   - generateRandomRide (L676-696): Gera RideData aleatório realista
// DEPENDÊNCIAS:
//   - data/model/CardGallery.kt → GalleryCard, CardField
//   - data/model/RideData.kt → CriteriaWeights, DriverThresholds, RideData
//   - domain/RideScorer.kt → calcula score na simulação
//   - data/prefs/PrefsManager.kt → card1ModelId, card2ModelId, activeSlot
// DEPENDENTES:
//   - Nenhum (tela final de UI)
// LÓGICA DO PREVIEW:
//   - Renderiza APENAS os campos do card ativo (galleryCard.fields)
//   - Cores: vermelho se viola threshold, accent se OK
//   - Botão "Nova Simulação" gera novos dados aleatórios
// ============================================================================

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import com.ngbautoroad.data.model.*
import com.ngbautoroad.data.prefs.PrefsManager
import com.ngbautoroad.domain.RideScorer
import com.ngbautoroad.service.OverlayService
import com.ngbautoroad.ui.editor.CardEditorActivity
import com.ngbautoroad.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

@Composable
fun CardTab(prefsManager: PrefsManager) {
    val scope = rememberCoroutineScope()
    val activeSlot by prefsManager.activeCardSlotFlow.collectAsState(initial = 1)
    val card1Name by prefsManager.card1NameFlow.collectAsState(initial = "Card 1")
    val card2Name by prefsManager.card2NameFlow.collectAsState(initial = "Card 2")
    val card1ModelId by prefsManager.card1ModelIdFlow.collectAsState(initial = 1)
    val card2ModelId by prefsManager.card2ModelIdFlow.collectAsState(initial = 5)
    val favorites by prefsManager.galleryFavoritesFlow.collectAsState(initial = emptySet())
    val weights by prefsManager.criteriaWeightsFlow.collectAsState(initial = CriteriaWeights())
    val thresholds by prefsManager.driverThresholdsFlow.collectAsState(initial = DriverThresholds())

    var showGallery by remember { mutableStateOf(false) }
    var galleryTargetSlot by remember { mutableIntStateOf(1) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameSlot by remember { mutableIntStateOf(1) }
    var showPreview by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Card Slot Selector
        Text(
            "Slots de Card",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Selecione o card ativo que será exibido no overlay",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Slot 1
        CardSlotItem(
            slotName = card1Name,
            modelName = CardGallery.getById(card1ModelId)?.name ?: "Padrão",
            isActive = activeSlot == 1,
            onActivate = { scope.launch { prefsManager.setActiveCardSlot(1) } },
            onOpenGallery = { galleryTargetSlot = 1; showGallery = true },
            onRename = { renameSlot = 1; showRenameDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Slot 2
        CardSlotItem(
            slotName = card2Name,
            modelName = CardGallery.getById(card2ModelId)?.name ?: "Padrão",
            isActive = activeSlot == 2,
            onActivate = { scope.launch { prefsManager.setActiveCardSlot(2) } },
            onOpenGallery = { galleryTargetSlot = 2; showGallery = true },
            onRename = { renameSlot = 2; showRenameDialog = true }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Slot 3 - Custom
        val context = LocalContext.current
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { scope.launch { prefsManager.setActiveCardSlot(3) } },
            colors = CardDefaults.cardColors(
                containerColor = if (activeSlot == 3)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.surfaceVariant
            ),
            border = if (activeSlot == 3) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        Icons.Default.Build,
                        contentDescription = "Ícone",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Custom (exclusivo)",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Editor visual completo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    if (activeSlot == 3) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Ativo",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(
                        onClick = {
                            context.startActivity(Intent(context, CardEditorActivity::class.java))
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Editar", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Preview / Simulação
        Text(
            "Simulação",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Veja como o card aparece com dados aleatórios e seus critérios",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        try {
                            val canDrawOverlay = android.provider.Settings.canDrawOverlays(context)
                            if (!canDrawOverlay) {
                                android.widget.Toast.makeText(context, "Permissão de overlay necessária.", android.widget.Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                val notifGranted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (!notifGranted) {
                                    android.widget.Toast.makeText(context, "Ative a permissão de Notificações.", android.widget.Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                            }
                            val ride = generateRandomRide()
                            val serviceRunning = OverlayService.isRunning()
                            if (!serviceRunning) {
                                OverlayService.start(context)
                            }
                            var retries = 0
                            var callback = OverlayService.onRideDetected
                            while (callback == null && retries < 3) {
                                delay(800)
                                callback = OverlayService.onRideDetected
                                retries++
                            }
                            if (callback != null) {
                                callback.invoke(ride)
                                // Minimizar o app para o overlay ficar visível
                                (context as? android.app.Activity)?.moveTaskToBack(true)
                            } else {
                                android.widget.Toast.makeText(context, "Serviço não iniciou. Verifique permissões.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "Erro ao simular: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.Visibility, contentDescription = "Ícone")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Simular")
            }

            // Botão que abre o overlay flutuante REAL na tela
            Button(
                onClick = {
                    scope.launch {
                        try {
                            android.util.Log.d("NGB_TESTAR_CARD", "[1] Botão Testar clicado")
                            // Verificar permissão de overlay antes de tentar
                            val canDrawOverlay = android.provider.Settings.canDrawOverlays(context)
                            android.util.Log.d("NGB_TESTAR_CARD", "[2] canDrawOverlays=$canDrawOverlay")
                            if (!canDrawOverlay) {
                                android.widget.Toast.makeText(context, "Permissão de overlay necessária. Ative nas configurações.", android.widget.Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            // Verificar permissão de notificação (necessária para foreground service no Android 13+)
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                val notifPerm = android.Manifest.permission.POST_NOTIFICATIONS
                                val notifGranted = context.checkSelfPermission(notifPerm) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                android.util.Log.d("NGB_TESTAR_CARD", "[3] POST_NOTIFICATIONS granted=$notifGranted")
                                if (!notifGranted) {
                                    android.widget.Toast.makeText(context, "Ative a permissão de Notificações nas configurações.", android.widget.Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                            }
                            // Gerar corrida aleatória
                            val ride = generateRandomRide()
                            android.util.Log.d("NGB_TESTAR_CARD", "[4] Corrida gerada: plataforma=${ride.platform} valor=${ride.rideValue} dist=${ride.dropoffDistance}km")
                            // v5.2.1: Iniciar OverlayService apenas se não está rodando
                            val serviceRunning = OverlayService.isRunning()
                            android.util.Log.d("NGB_TESTAR_CARD", "[5] OverlayService.isRunning()=$serviceRunning")
                            if (!serviceRunning) {
                                try {
                                    android.util.Log.d("NGB_TESTAR_CARD", "[6] Iniciando OverlayService...")
                                    OverlayService.start(context)
                                    android.util.Log.d("NGB_TESTAR_CARD", "[7] OverlayService.start() chamado")
                                } catch (e: Exception) {
                                    android.util.Log.e("NGB_TESTAR_CARD", "[ERR] Falha ao iniciar OverlayService: ${e.javaClass.simpleName}: ${e.message}", e)
                                    android.widget.Toast.makeText(context, "Falha ao iniciar serviço: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    return@launch
                                }
                            }
                            // Aguardar até 3 tentativas (3x800ms = 2.4s máx)
                            var retries = 0
                            var callback = OverlayService.onRideDetected
                            android.util.Log.d("NGB_TESTAR_CARD", "[8] onRideDetected callback=${callback != null} (tentativa 0)")
                            while (callback == null && retries < 3) {
                                delay(800)
                                callback = OverlayService.onRideDetected
                                retries++
                                android.util.Log.d("NGB_TESTAR_CARD", "[8.$retries] onRideDetected callback=${callback != null} (tentativa $retries)")
                            }
                            val finalCallback = callback
                            if (finalCallback != null) {
                                android.util.Log.d("NGB_TESTAR_CARD", "[9] Invocando callback com corrida simulada")
                                finalCallback.invoke(ride)
                                android.util.Log.d("NGB_TESTAR_CARD", "[10] Callback invocado com sucesso")
                                // Minimizar o app para o overlay ficar visível
                                (context as? android.app.Activity)?.moveTaskToBack(true)
                            } else {
                                android.util.Log.w("NGB_TESTAR_CARD", "[ERR] callback nulo após $retries tentativas. OverlayService.isRunning()=${OverlayService.isRunning()}")
                                android.widget.Toast.makeText(context, "Serviço não iniciou. Verifique permissão de acessibilidade.", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            val msg = when {
                                e.javaClass.simpleName.contains("ForegroundService") ->
                                    "Não foi possível iniciar o serviço em background. Tente novamente."
                                e.javaClass.simpleName.contains("Security") ->
                                    "Permissão negada. Verifique as configurações do app."
                                else -> "Erro ao testar: ${e.message}"
                            }
                            android.util.Log.e("NGB_TESTAR_CARD", "[ERR] Exceção: ${e.javaClass.simpleName}: ${e.message}", e)
                            android.util.Log.e("NGB_TESTAR_CARD", "[ERR] StackTrace: ${e.stackTraceToString().take(1000)}")
                            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ScoreGreen
                )
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = "Testar")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Testar")
            }
        }
    }

    // Gallery Dialog
    if (showGallery) {
        GalleryDialog(
            favorites = favorites,
            onDismiss = { showGallery = false },
            onSelectCard = { cardId ->
                scope.launch {
                    prefsManager.setCardModelId(galleryTargetSlot, cardId)
                }
                showGallery = false
            },
            onToggleFavorite = { cardId ->
                scope.launch { prefsManager.toggleGalleryFavorite(cardId) }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog) {
        RenameDialog(
            currentName = if (renameSlot == 1) card1Name else card2Name,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newName ->
                scope.launch { prefsManager.setCardName(renameSlot, newName) }
                showRenameDialog = false
            }
        )
    }

    // Preview Dialog
    if (showPreview) {
        PreviewDialog(
            activeSlot = activeSlot,
            card1ModelId = card1ModelId,
            card2ModelId = card2ModelId,
            weights = weights,
            thresholds = thresholds,
            onDismiss = { showPreview = false }
        )
    }
}

@Composable
fun CardSlotItem(
    slotName: String,
    modelName: String,
    isActive: Boolean,
    onActivate: () -> Unit,
    onOpenGallery: () -> Unit,
    onRename: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActivate() },
        colors = CardDefaults.cardColors(
            containerColor = if (isActive)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isActive) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (isActive) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Ativo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Icon(
                        Icons.Default.RadioButtonUnchecked,
                        contentDescription = "Ícone",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        slotName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        modelName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row {
                IconButton(onClick = onRename, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Renomear", modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onOpenGallery, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Collections, contentDescription = "Galeria", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryDialog(
    favorites: Set<Int>,
    onDismiss: () -> Unit,
    onSelectCard: (Int) -> Unit,
    onToggleFavorite: (Int) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<CardGallery.CardCategory?>(null) }
    var showFavoritesOnly by remember { mutableStateOf(false) }

    val displayCards = CardGallery.allCards.filter { card ->
        val categoryMatch = selectedCategory == null || card.category == selectedCategory
        val favoriteMatch = !showFavoritesOnly || favorites.contains(card.id)
        categoryMatch && favoriteMatch
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Galeria de Cards",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        IconButton(onClick = { showFavoritesOnly = !showFavoritesOnly }) {
                            Icon(
                                if (showFavoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favoritos",
                                tint = if (showFavoritesOnly) ScoreRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Fechar")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Category filter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("Todos", style = MaterialTheme.typography.labelSmall) }
                    )
                    CardGallery.CardCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "${displayCards.size} cards",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(displayCards) { card ->
                        GalleryCardItem(
                            card = card,
                            isFavorite = favorites.contains(card.id),
                            onSelect = { onSelectCard(card.id) },
                            onToggleFavorite = { onToggleFavorite(card.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GalleryCardItem(
    card: CardGallery.GalleryCard,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clickable { onSelect() },
        shape = RoundedCornerShape(card.borderRadius.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(card.backgroundColor)
        ),
        border = if (card.showBorder) BorderStroke(1.dp, Color(card.borderColor)) else null
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    card.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(card.textColor),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                // Mostrar campos do card
                Text(
                    card.fields.joinToString(" • ") { it.shortLabel },
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = Color(card.textColor).copy(alpha = 0.8f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    card.category.label + " • ${card.fields.size} campos",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = Color(card.textColor).copy(alpha = 0.6f)
                )
            }

            // Favorite button
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(28.dp)
            ) {
                Icon(
                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = "Favorito",
                    tint = if (isFavorite) ScoreRed else Color(card.textColor).copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Renomear Card") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nome do card") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) {
                Text("Salvar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}


// v5.2.1: Cores dinâmicas por critério (verde/amarelo/laranja/vermelho)
private fun getCriteriaScoreColor(value: Double, threshold: Double, isMinimum: Boolean, accentColor: Color): Color {
    if (threshold <= 0.0) return accentColor // Critério desativado
    val ratio = if (isMinimum) value / threshold else threshold / value
    return when {
        ratio >= 1.2 -> ScoreGreen    // Muito acima do mínimo / muito abaixo do máximo
        ratio >= 1.0 -> ScoreGreen    // Atende o critério
        ratio >= 0.8 -> ScoreYellow   // Quase atende (80-100%)
        ratio >= 0.6 -> ScoreOrange   // Abaixo (60-80%)
        else -> ScoreRed              // Muito abaixo (<60%)
    }
}

@Composable
fun PreviewDialog(
    activeSlot: Int,
    card1ModelId: Int,
    card2ModelId: Int,
    weights: CriteriaWeights,
    thresholds: DriverThresholds,
    onDismiss: () -> Unit
) {
    // Gerar corrida aleatória
    var randomRide by remember { mutableStateOf(generateRandomRide()) }
    var previewFontScale by remember { mutableFloatStateOf(1.0f) }
    var previewWidth by remember { mutableFloatStateOf(1f) }

    // Calcular score com os critérios reais do motorista
    val scorer = remember(weights, thresholds) {
        RideScorer(weights = weights, driverThresholds = thresholds)
    }
    val result = remember(randomRide, scorer) { scorer.calculateScore(randomRide) }

    // Obter card ativo
    val activeCard = when (activeSlot) {
        1 -> CardGallery.getById(card1ModelId)
        2 -> CardGallery.getById(card2ModelId)
        else -> null
    }

    val scoreColor = when {
        result.totalScore >= 70 -> ScoreGreen
        result.totalScore >= 50 -> ScoreYellow
        result.totalScore >= 30 -> ScoreOrange
        else -> ScoreRed
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Simulação do Card",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Dados aleatórios + seus critérios reais",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Card Preview
                Card(
                    modifier = Modifier.fillMaxWidth(previewWidth),
                    shape = RoundedCornerShape((activeCard?.borderRadius ?: 12).dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(activeCard?.backgroundColor ?: 0xFF101830)
                    ),
                    border = if (activeCard?.showBorder != false)
                        BorderStroke(2.dp, scoreColor)
                    else null
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        // Score + Platform
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                randomRide.platform.displayName,
                                color = Color(activeCard?.textColor ?: 0xFFFFFFFF),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                String.format("%.0f", result.totalScore),
                                color = scoreColor,
                                style = MaterialTheme.typography.headlineMedium.copy(fontSize = (24 * previewFontScale).sp),
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            // Acessibilidade A-/A+
                            Icon(
                                Icons.Default.TextDecrease,
                                contentDescription = "Diminuir fonte",
                                tint = Color(activeCard?.textColor ?: 0xFFFFFFFF).copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp).clickable {
                                    previewFontScale = (previewFontScale - 0.1f).coerceIn(0.7f, 2.0f)
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.TextIncrease,
                                contentDescription = "Aumentar fonte",
                                tint = Color(activeCard?.textColor ?: 0xFFFFFFFF).copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp).clickable {
                                    previewFontScale = (previewFontScale + 0.1f).coerceIn(0.7f, 2.0f)
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val textColor = Color(activeCard?.textColor ?: 0xFFFFFFFF)
                        val accentColor = Color(activeCard?.accentColor ?: 0xFF4F6BFF)

                        // Renderizar APENAS os campos que o card ativo possui
                        val cardFields = activeCard?.fields ?: CardGallery.CardField.entries.toList()

                        cardFields.forEach { field ->
                            when (field) {
                                CardGallery.CardField.RIDE_VALUE -> {
                                    val color = if (thresholds.isRideValueActive()) getCriteriaScoreColor(randomRide.rideValue, thresholds.minRideValue, true, accentColor) else accentColor
                                    PreviewField("Valor", String.format("R$ %.2f", randomRide.rideValue), textColor, color)
                                }
                                CardGallery.CardField.VALUE_PER_KM -> {
                                    val color = if (thresholds.isValuePerKmActive()) getCriteriaScoreColor(randomRide.valuePerKm, thresholds.minValuePerKm, true, accentColor) else accentColor
                                    PreviewField("R$/KM", String.format("R$ %.2f", randomRide.valuePerKm), textColor, color)
                                }
                                CardGallery.CardField.VALUE_PER_HOUR -> {
                                    val color = if (thresholds.isValuePerHourActive()) getCriteriaScoreColor(randomRide.valuePerHour, thresholds.minValuePerHour, true, accentColor) else accentColor
                                    PreviewField("R$/Hora", String.format("R$ %.2f", randomRide.valuePerHour), textColor, color)
                                }
                                CardGallery.CardField.PICKUP_DISTANCE -> {
                                    val color = if (thresholds.isPickupDistanceActive()) getCriteriaScoreColor(randomRide.pickupDistance, thresholds.maxPickupDistance, false, accentColor) else accentColor
                                    PreviewField("Embarque", String.format("%.1f km", randomRide.pickupDistance), textColor, color)
                                }
                                CardGallery.CardField.DROPOFF_DISTANCE -> {
                                    PreviewField("Destino", String.format("%.1f km", randomRide.dropoffDistance), textColor, accentColor)
                                }
                                CardGallery.CardField.DURATION -> {
                                    val color = if (thresholds.isDurationActive()) getCriteriaScoreColor(randomRide.rideDuration, thresholds.maxDuration, false, accentColor) else accentColor
                                    PreviewField("Duração", String.format("%.0f min", randomRide.rideDuration), textColor, color)
                                }
                                CardGallery.CardField.PASSENGER_RATING -> {
                                    if (randomRide.passengerRating > 0) {
                                        val color = if (thresholds.isPassengerRatingActive()) getCriteriaScoreColor(randomRide.passengerRating, thresholds.minPassengerRating, true, accentColor) else accentColor
                                        PreviewField("Aval.", String.format("%.1f ★", randomRide.passengerRating), textColor, color)
                                    }
                                }
                                CardGallery.CardField.STOPS -> {
                                    val color = if (thresholds.isStopsActive()) getCriteriaScoreColor(randomRide.intermediateStops.toDouble(), thresholds.maxStops.toDouble(), false, accentColor) else accentColor
                                    PreviewField("Paradas", "${randomRide.intermediateStops}", textColor, color)
                                }
                                CardGallery.CardField.PICKUP_NEIGHBORHOOD -> {
                                    PreviewField("B. Embarque", randomRide.pickupNeighborhood.ifBlank { "-" }, textColor, accentColor)
                                }
                                CardGallery.CardField.DROPOFF_NEIGHBORHOOD -> {
                                    PreviewField("B. Destino", randomRide.dropoffNeighborhood.ifBlank { "-" }, textColor, accentColor)
                                }
                                CardGallery.CardField.RIDE_TYPE -> {
                                    PreviewField("Tipo", randomRide.rideType.displayName, textColor, accentColor)
                                }
                                CardGallery.CardField.PLATFORM -> {} // Já mostrado no header
                                CardGallery.CardField.SCORE -> {} // Já mostrado no header
                                CardGallery.CardField.SCORE_BAR -> {} // Mostrado abaixo
                            }
                        }

                        // Score bar
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = (result.totalScore / 100.0).toFloat().coerceIn(0f, 1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = scoreColor,
                            trackColor = Color(activeCard?.textColor ?: 0xFFFFFFFF).copy(alpha = 0.2f),
                        )
                    }
                }

                                Spacer(modifier = Modifier.height(8.dp))
                // Controle de largura (resize)
                Text("Largura do card", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = previewWidth,
                    onValueChange = { previewWidth = it },
                    valueRange = 0.6f..1f,
                    steps = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(onClick = { randomRide = generateRandomRide() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Atualizar", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Nova Simulação")
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Fechar")
                    }
                }
            }
        }
    }
}

@Composable
fun PreviewField(label: String, value: String, textColor: Color, accentColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.7f))
        Text(value, style = MaterialTheme.typography.labelSmall, color = accentColor, fontWeight = FontWeight.Bold)
    }
}

/**
 * Gera uma corrida com valores aleatórios realistas
 */
fun generateRandomRide(): RideData {
    val platforms = listOf(Platform.UBER, Platform.NINETY_NINE, Platform.INDRIVE, Platform.CABIFY)
    val neighborhoods = listOf("Centro", "Barra", "Pituba", "Itapuã", "Brotas", "Paralela",
        "Ondina", "Rio Vermelho", "Liberdade", "Cabula", "Imbuí", "Stella Maris")

    val dropoffDist = Random.nextDouble(2.0, 25.0)
    val rideValue = dropoffDist * Random.nextDouble(1.0, 3.5)
    val duration = Random.nextDouble(8.0, 45.0)

    return RideData(
        platform = platforms.random(),
        rideValue = rideValue,
        rideDuration = duration,
        pickupDistance = Random.nextDouble(0.3, 5.0),
        dropoffDistance = dropoffDist,
        passengerRating = Random.nextDouble(3.5, 5.0),
        intermediateStops = if (Random.nextFloat() < 0.3f) Random.nextInt(1, 3) else 0,
        pickupNeighborhood = neighborhoods.random(),
        dropoffNeighborhood = neighborhoods.random(),
        isSimulation = true  // Não salvar no histórico/financeiro
    )
}
