package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: RideAccessibilityService.kt
// v5.4.1 — REESCRITO para resolver:
//   1. Detecção de corridas Uber (Compose deep tree + multi-window)
//   2. Stealth mode para apps bancários (remove overlay quando banco está ativo)
// MUDANÇAS CRÍTICAS:
//   - maxDepth: 10 → 30 (Compose tem árvores profundas)
//   - FLAG_RETRIEVE_INTERACTIVE_WINDOWS adicionado
//   - packageNames REMOVIDO do filtro (precisa detectar bancos)
//   - Filtragem por package feita no código (mais flexível)
//   - getWindows() como fallback para multi-window
//   - Throttle Uber: 300ms → 100ms
//   - Stealth mode: remove overlay quando app bancário está em foreground
// ============================================================================

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ngbautoroad.data.model.Platform
import com.ngbautoroad.data.model.RideData
import com.ngbautoroad.data.model.RideType

class RideAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RideA11y"
        
        // Packages de apps bancários brasileiros que detectam overlay/accessibility
        val BANK_PACKAGES = setOf(
            "com.itau", "com.itau.empresas",
            "br.com.bradesco", "com.bradesco.next",
            "br.com.bb.android", "br.com.bb.android.empresas",
            "br.com.santander.way", "br.com.santander.app",
            "com.nu.production",
            "br.com.original.bank",
            "com.mercadopago.wallet",
            "br.com.intermedium",
            "com.picpay",
            "br.com.c6bank.app",
            "com.btgpactual.pangea",
            "br.com.xp.carteira",
            "com.neon",
            "br.com.caixa.tem", "br.gov.caixa.tem",
            "br.com.gabba.Caixa",
            "com.pagseguro.seller",
            "br.com.sicoob.app",
            "br.com.sicredi.app",
            "com.stone.conta",
            "br.com.daycoval.app",
            "br.com.bancointer",
            "com.safra.pocket"
        )
        
        // Packages de apps de corrida monitorados
        val RIDE_PACKAGES = setOf(
            "com.ubercab.driver",
            "com.ubercab",  // Uber rider app (caso driver use este)
            "com.ninety9.driver",
            "com.machfrankfurt.android",
            "com.cabify.driver"
        )
        
        // Flag para stealth mode — acessível pelo OverlayService
        @Volatile
        var stealthModeActive = false
            private set
    }

    private var lastProcessedTime = 0L
    private var lastRideHash: Int = 0
    private var lastRideHashTime = 0L
    private val DUPLICATE_WINDOW_MS = 10_000L
    private var lastForegroundPackage = ""

    // Throttle reduzido para Uber (100ms), outros mantêm 300-400ms
    private val THROTTLE_BY_PLATFORM = mapOf(
        "com.ubercab.driver" to 100L,
        "com.ubercab" to 100L,
        "com.ninety9.driver" to 300L,
        "com.machfrankfurt.android" to 400L,
        "com.cabify.driver" to 300L
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AccessibilityService conectado — v5.4.1 (multi-window + stealth)")
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            notificationTimeout = 50
            // NÃO definir packageNames — precisamos detectar bancos para stealth mode
            // A filtragem é feita no código
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val packageName = event.packageName?.toString() ?: return
        val eventType = event.eventType

        // =====================================================================
        // STEALTH MODE: Detectar apps bancários
        // =====================================================================
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (isBankApp(packageName)) {
                if (!stealthModeActive) {
                    Log.d(TAG, "STEALTH ON — App bancário detectado: $packageName")
                    stealthModeActive = true
                    // Notificar OverlayService para remover overlay imediatamente
                    OverlayService.onStealthModeChanged?.invoke(true)
                }
                lastForegroundPackage = packageName
                return // Não processar nada quando banco está ativo
            } else if (stealthModeActive && !isBankApp(packageName)) {
                // Saiu do banco — desativar stealth mode
                Log.d(TAG, "STEALTH OFF — Saiu do banco, app atual: $packageName")
                stealthModeActive = false
                OverlayService.onStealthModeChanged?.invoke(false)
            }
            lastForegroundPackage = packageName
        }

        // Se stealth mode ativo, não processar NADA
        if (stealthModeActive) return

        // =====================================================================
        // DETECÇÃO DE CORRIDAS: Apenas para packages de apps de corrida
        // =====================================================================
        if (!isRideApp(packageName)) return

        val throttle = THROTTLE_BY_PLATFORM[packageName] ?: 200L
        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < throttle) return
        lastProcessedTime = now

        val platform = detectPlatform(packageName) ?: return

        // Estratégia multi-window: tentar rootInActiveWindow primeiro,
        // depois iterar getWindows() se necessário
        val allTexts = collectTextsFromAllSources(packageName)
        
        if (allTexts.isEmpty()) {
            Log.d(TAG, "Nenhum texto coletado de $packageName")
            return
        }

        try {
            val rideData = when (platform) {
                Platform.UBER -> parseUberRide(allTexts)
                Platform.NINETY_NINE -> parse99Ride(allTexts)
                Platform.INDRIVE -> parseInDriveRide(allTexts)
                Platform.CABIFY -> parseCabifyRide(allTexts)
                else -> null
            }

            rideData?.let { ride ->
                if (ride.rideValue > 0) {
                    val hash = "${ride.platform}_${String.format("%.2f", ride.rideValue)}_${String.format("%.1f", ride.dropoffDistance)}_${ride.pickupNeighborhood}".hashCode()
                    if (hash == lastRideHash && (now - lastRideHashTime) < DUPLICATE_WINDOW_MS) {
                        return
                    }
                    lastRideHash = hash
                    lastRideHashTime = now

                    Log.i(TAG, "✓ CORRIDA: ${ride.platform} R$${ride.rideValue} | pickup=${ride.pickupDistance}km | trip=${ride.dropoffDistance}km/${ride.rideDuration}min | ★${ride.passengerRating}")
                    OverlayService.onRideDetected?.invoke(ride)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrompido")
    }

    // =========================================================================
    // COLETA DE TEXTO — Multi-source (rootInActiveWindow + getWindows)
    // =========================================================================

    /**
     * Coleta textos de TODAS as fontes disponíveis:
     * 1. rootInActiveWindow (janela principal)
     * 2. getWindows() — todas as janelas ativas (popups, dialogs, overlays)
     * 3. event.source como fallback
     *
     * Isso resolve o problema com Compose que pode usar janelas separadas
     * para dialogs/bottom sheets de ofertas de corrida.
     */
    private fun collectTextsFromAllSources(targetPackage: String): List<String> {
        val allTexts = mutableListOf<String>()
        
        // Fonte 1: rootInActiveWindow
        val root = rootInActiveWindow
        if (root != null) {
            traverseNode(root, allTexts, 0)
            root.recycle()
        }

        // Fonte 2: getWindows() — captura TODAS as janelas incluindo popups/dialogs
        try {
            val windowList = windows
            for (window in windowList) {
                val windowRoot = window.root ?: continue
                // Só coletar de janelas do package alvo ou janelas sem package (system dialogs)
                val windowPackage = windowRoot.packageName?.toString() ?: ""
                if (windowPackage == targetPackage || windowPackage.isEmpty()) {
                    val windowTexts = mutableListOf<String>()
                    traverseNode(windowRoot, windowTexts, 0)
                    // Adicionar apenas textos que ainda não temos
                    for (text in windowTexts) {
                        if (text !in allTexts) allTexts.add(text)
                    }
                }
                windowRoot.recycle()
            }
        } catch (e: Exception) {
            Log.d(TAG, "getWindows() falhou: ${e.message}")
        }

        return allTexts
    }

    // =========================================================================
    // PARSER UBER DRIVER — v5.4.1 (Compose-aware, deep tree)
    // =========================================================================
    // Textos reais da screenshot do usuário (19/06/2026):
    //   "UberX" (badge no topo)
    //   "R$ 6,98" (valor grande)
    //   "★ 5,00 (26)" (rating)
    //   "8 minutos (3.4 km) de distância" (pickup)
    //   "Rua Ariedson Claivor Denti, Líder / Vila Real" (endereço embarque)
    //   "Viagem de 3 minutos (1.0 km)" (viagem)
    //   "R. Ângelo Sachet - Desbravador, Chapecó - SC, 89811-450, Brasil" (destino)
    //   "Selecionar" ou "Aceitar" (botão)
    // =========================================================================
    private fun parseUberRide(allText: List<String>): RideData? {
        var rideValue = 0.0
        var pickupDistance = 0.0
        var dropoffDistance = 0.0
        var duration = 0.0
        var rating = 0.0
        var stops = 0
        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""
        var hasAcceptButton = false
        var rideTypeBadge = ""

        for (text in allText) {
            // ---- BOTÃO ACEITAR/SELECIONAR ----
            if (text.equals("Aceitar", ignoreCase = true) ||
                text.equals("Selecionar", ignoreCase = true) ||
                text.equals("Accept", ignoreCase = true) ||
                text.equals("Select", ignoreCase = true)) {
                hasAcceptButton = true
            }

            // ---- TIPO DE CORRIDA ----
            if (rideTypeBadge.isBlank()) {
                val trimmed = text.trim()
                if (trimmed.matches(Regex("""(?i)(UberX|Uber\s*X|Comfort|Black|Flash|Promo|Green|Prioridade|Priority|UberX\s*Share)"""))) {
                    rideTypeBadge = trimmed
                }
            }

            // ---- VALOR DA CORRIDA ----
            // Formatos: "R$ 6,98", "R$16,60", "R$ 79,35"
            if (rideValue == 0.0) {
                val valueMatch = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(text)
                if (valueMatch != null) {
                    val v = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (v in 1.0..999.0) rideValue = v
                }
            }

            // ---- RATING ----
            // Formatos: "★ 5,00 (26)", "4,90 (275)", "5.00 (26)"
            if (rating == 0.0) {
                val ratingMatch = Regex("""★?\s*([4-5][.,]\d{1,2})\s*\(\d+\)""").find(text)
                if (ratingMatch != null) {
                    val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (r in 1.0..5.0) rating = r
                }
            }

            // ---- DISTÂNCIA ATÉ EMBARQUE ----
            // Formato: "8 minutos (3.4 km) de distância"
            val pickupMatch = Regex("""(\d+)\s*minutos?\s*\((\d+[.,]\d+)\s*km\)\s*de\s*dist""", RegexOption.IGNORE_CASE).find(text)
            if (pickupMatch != null && pickupDistance == 0.0) {
                pickupDistance = pickupMatch.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            // ---- VIAGEM (duração + distância) ----
            // Formato: "Viagem de 3 minutos (1.0 km)"
            val tripMatch = Regex("""[Vv]iagem\s+de\s+(\d+)\s*minutos?\s*\((\d+[.,]\d+)\s*km\)""").find(text)
            if (tripMatch != null && dropoffDistance == 0.0) {
                duration = tripMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                dropoffDistance = tripMatch.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            // ---- BAIRROS ----
            if (pickupNeighborhood.isBlank() && pickupDistance > 0 && dropoffDistance == 0.0) {
                val neighborhoodMatch = Regex("""[/\-]\s*([A-ZÀ-Ú][a-zà-ú]+(?:\s+[A-ZÀ-Ú][a-zà-ú]+)*)""").find(text)
                if (neighborhoodMatch != null) {
                    pickupNeighborhood = neighborhoodMatch.groupValues[1].trim().take(30)
                }
            }
            if (dropoffNeighborhood.isBlank() && dropoffDistance > 0) {
                val neighborhoodMatch = Regex("""[/\-]\s*([A-ZÀ-Ú][a-zà-ú]+(?:\s+[A-ZÀ-Ú][a-zà-ú]+)*)""").find(text)
                if (neighborhoodMatch != null) {
                    dropoffNeighborhood = neighborhoodMatch.groupValues[1].trim().take(30)
                }
            }

            // ---- PARADAS ----
            if (stops == 0) {
                val stopMatch = Regex("""(\d+)\s*parada""", RegexOption.IGNORE_CASE).find(text)
                if (stopMatch != null) stops = stopMatch.groupValues[1].toIntOrNull() ?: 1
                else if (text.contains("parada", ignoreCase = true)) stops = 1
            }
        }

        // Fallback: distâncias soltas em "X.X km"
        if (dropoffDistance == 0.0 && pickupDistance == 0.0) {
            val allDistances = mutableListOf<Double>()
            for (text in allText) {
                Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).findAll(text).forEach { m ->
                    val d = m.groupValues[1].replace(",", ".").toDoubleOrNull()
                    if (d != null && d > 0) allDistances.add(d)
                }
            }
            if (allDistances.size >= 2) {
                pickupDistance = allDistances[0]
                dropoffDistance = allDistances[1]
            } else if (allDistances.size == 1) {
                dropoffDistance = allDistances[0]
            }
        }

        // Fallback: duração
        if (duration == 0.0) {
            for (text in allText) {
                val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
                if (durMatch != null) {
                    val d = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    if (d > 0 && d < 120) { duration = d; break }
                }
            }
        }

        // VALIDAÇÃO RELAXADA: aceitar se tem valor OU botão aceitar
        // (antes exigia ambos, agora aceita qualquer um)
        if (rideValue == 0.0 && !hasAcceptButton) return null

        val detectedType = if (rideTypeBadge.isNotBlank()) {
            RideType.fromBadgeText(rideTypeBadge, Platform.UBER)
        } else {
            RideType.UBER_X
        }

        return RideData(
            platform = Platform.UBER,
            rideType = detectedType,
            rideValue = rideValue,
            rideDuration = duration,
            pickupDistance = pickupDistance,
            dropoffDistance = dropoffDistance,
            passengerRating = rating,
            intermediateStops = stops,
            pickupNeighborhood = pickupNeighborhood,
            dropoffNeighborhood = dropoffNeighborhood
        )
    }

    // =========================================================================
    // PARSER 99 DRIVER
    // =========================================================================
    private fun parse99Ride(allText: List<String>): RideData? {
        var rideValue = 0.0
        var distance = 0.0
        var pickupDistance = 0.0
        var duration = 0.0
        var rating = 0.0
        var stops = 0
        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""

        for (text in allText) {
            if (rideValue == 0.0) {
                val valueMatch = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(text)
                if (valueMatch != null) {
                    val v = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (v in 1.0..999.0) rideValue = v
                }
            }

            if (rating == 0.0) {
                val ratingMatch = Regex("""([4-5][.,]\d{1,2})\s*★""").find(text)
                    ?: Regex("""★?\s*([4-5][.,]\d{1,2})""").find(text)
                if (ratingMatch != null) {
                    val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (r in 3.0..5.0) rating = r
                }
            }

            if (distance == 0.0) {
                val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(text)
                if (distMatch != null) {
                    distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                }
            }

            if (duration == 0.0) {
                val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
                if (durMatch != null) {
                    val d = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    if (d > 0 && d < 120) duration = d
                }
            }

            if (pickupNeighborhood.isBlank()) {
                val m = Regex("""[/\-]\s*([A-ZÀ-Ú][a-zà-ú]+(?:\s+[A-ZÀ-Ú][a-zà-ú]+)*)""").find(text)
                if (m != null && rideValue > 0) pickupNeighborhood = m.groupValues[1].trim().take(30)
            }
        }

        if (rideValue == 0.0) return null

        return RideData(
            platform = Platform.NINETY_NINE,
            rideValue = rideValue,
            pickupDistance = pickupDistance,
            dropoffDistance = distance,
            rideDuration = duration,
            passengerRating = rating,
            intermediateStops = stops,
            pickupNeighborhood = pickupNeighborhood,
            dropoffNeighborhood = dropoffNeighborhood
        )
    }

    // =========================================================================
    // PARSER INDRIVE
    // =========================================================================
    private fun parseInDriveRide(allText: List<String>): RideData? {
        var rideValue = 0.0
        var distance = 0.0
        var duration = 0.0
        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""

        for (text in allText) {
            if (rideValue == 0.0) {
                val valueMatch = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(text)
                if (valueMatch != null) {
                    val v = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (v in 1.0..999.0) rideValue = v
                }
            }

            if (distance == 0.0) {
                val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(text)
                if (distMatch != null) {
                    distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                }
            }

            if (duration == 0.0) {
                val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
                if (durMatch != null) {
                    val d = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    if (d > 0 && d < 120) duration = d
                }
            }

            if (pickupNeighborhood.isBlank()) {
                val m = Regex("""[/\-]\s*([A-ZÀ-Ú][a-zà-ú]+(?:\s+[A-ZÀ-Ú][a-zà-ú]+)*)""").find(text)
                if (m != null && rideValue > 0) pickupNeighborhood = m.groupValues[1].trim().take(30)
            }
        }

        if (rideValue == 0.0) return null

        return RideData(
            platform = Platform.INDRIVE,
            rideValue = rideValue,
            dropoffDistance = distance,
            rideDuration = duration,
            pickupNeighborhood = pickupNeighborhood,
            dropoffNeighborhood = dropoffNeighborhood
        )
    }

    // =========================================================================
    // PARSER CABIFY
    // =========================================================================
    private fun parseCabifyRide(allText: List<String>): RideData? {
        var rideValue = 0.0
        var distance = 0.0
        var duration = 0.0
        var rating = 0.0
        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""

        for (text in allText) {
            if (rideValue == 0.0) {
                val valueMatch = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(text)
                if (valueMatch != null) {
                    val v = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (v in 1.0..999.0) rideValue = v
                }
            }

            if (distance == 0.0) {
                val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(text)
                if (distMatch != null) distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            if (duration == 0.0) {
                val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
                if (durMatch != null) {
                    val d = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    if (d > 0 && d < 120) duration = d
                }
            }

            if (rating == 0.0) {
                val ratingMatch = Regex("""([4-5][.,]\d{1,2})\s*[★⭐]?""").find(text)
                if (ratingMatch != null) {
                    val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (r in 3.0..5.0) rating = r
                }
            }
        }

        if (rideValue == 0.0) return null

        return RideData(
            platform = Platform.CABIFY,
            rideValue = rideValue,
            dropoffDistance = distance,
            rideDuration = duration,
            passengerRating = rating,
            pickupNeighborhood = pickupNeighborhood,
            dropoffNeighborhood = dropoffNeighborhood
        )
    }

    // =========================================================================
    // UTILIDADES
    // =========================================================================

    private fun isBankApp(packageName: String): Boolean {
        return BANK_PACKAGES.any { packageName.startsWith(it) }
    }

    private fun isRideApp(packageName: String): Boolean {
        return RIDE_PACKAGES.contains(packageName)
    }

    private fun detectPlatform(packageName: String): Platform? {
        return Platform.entries.find { it.packageName == packageName }
            ?: if (packageName == "com.ubercab") Platform.UBER else null
    }

    /**
     * Coleta todos os textos visíveis na árvore de acessibilidade.
     * v5.4.1: maxDepth=30 (era 10) para suportar Compose deep trees.
     */
    private fun collectAllText(node: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        traverseNode(node, texts, 0)
        return texts
    }

    /**
     * Percorre nós recursivamente.
     * v5.4.1: maxDepth=30 para Compose (era 10).
     * Compose gera árvores com 15-25 níveis de profundidade.
     */
    private fun traverseNode(node: AccessibilityNodeInfo, texts: MutableList<String>, depth: Int) {
        if (depth > 30) return // Compose pode ter até 25 níveis
        
        // Extrair texto do nó
        node.text?.toString()?.let { t ->
            if (t.isNotBlank()) texts.add(t.trim())
        }
        
        // Extrair contentDescription (usado por Compose semantics)
        node.contentDescription?.toString()?.let { cd ->
            if (cd.isNotBlank() && cd !in texts) texts.add(cd.trim())
        }
        
        // Percorrer filhos
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            try {
                traverseNode(child, texts, depth + 1)
            } finally {
                child.recycle()
            }
        }
    }
}
