package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: RideAccessibilityService.kt
// LOCALIZAÇÃO: service/RideAccessibilityService.kt
// RESPONSABILIDADE: Captura dados de corrida via AccessibilityService (sem OCR)
// BLOCOS:
//   - onAccessibilityEvent (L72): Detecta pacotes de apps de corrida
//   - parseUberRide (L114): Parser para Uber Driver (baseado em telas reais 06/2026)
//   - parse99Ride (L240): Parser para 99 Driver
//   - parseInDriveRide (L310): Parser para inDrive
//   - parseCabifyRide (L360): Parser para Cabify Driver
//   - collectAllText (L410): Coleta textos da árvore de acessibilidade
//   - traverseNode (L420): Percorre nós recursivamente
// DEPENDÊNCIAS:
//   - data/model/RideData.kt → RideData, Platform
//   - service/OverlayService.kt → envia RideData via callback onRideDetected
// DEPENDENTES:
//   - OverlayService.kt → recebe RideData para exibir card
//   - HistoryTab → exibe corridas salvas
// PROTEÇÕES:
//   - Deduplicação: hash de (platform+value+distance) com janela de 10s
//   - Throttle adaptativo por plataforma (300-500ms)
//   - Regex defensivo: não crasha se texto não bate
//   - Validação de dados: rideValue > 0 obrigatório
// ============================================================================

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ngbautoroad.data.model.Platform
import com.ngbautoroad.data.model.RideData
import com.ngbautoroad.data.model.RideType

/**
 * AccessibilityService que detecta quando um app de transporte exibe
 * uma oferta de corrida e extrai os dados via árvore de nós.
 *
 * v4.2 — Reescrito baseado em telas reais do Uber Driver (06/2026):
 * - Formato real: "R$ 16,60" (valor isolado)
 * - Formato real: "★ 4,90 (275)" (rating com estrela)
 * - Formato real: "9 minutos (3.9 km) de distância" (pickup)
 * - Formato real: "Viagem de 17 minutos (7.2 km)" (viagem)
 * - Formato real: "Rua X, Bairro / Bairro2" (endereço com bairros)
 */
class RideAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "RideAccessibility"
    }

    private var lastProcessedTime = 0L
    private var lastRideHash: Int = 0
    private var lastRideHashTime = 0L
    private val DUPLICATE_WINDOW_MS = 10_000L // 10 segundos anti-duplicata

    // Throttle por plataforma (ms)
    private val THROTTLE_BY_PLATFORM = mapOf(
        "com.ubercab.driver" to 300L,
        "com.ninety9.driver" to 400L,
        "com.machfrankfurt.android" to 500L,
        "com.cabify.driver" to 400L
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "AccessibilityService conectado")
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
            packageNames = arrayOf(
                Platform.UBER.packageName,
                Platform.NINETY_NINE.packageName,
                Platform.INDRIVE.packageName,
                Platform.CABIFY.packageName
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val packageName = event.packageName?.toString() ?: return
        val throttle = THROTTLE_BY_PLATFORM[packageName] ?: 300L
        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < throttle) return
        lastProcessedTime = now

        val platform = detectPlatform(packageName) ?: return
        val rootNode = rootInActiveWindow ?: return

        try {
            val rideData = when (platform) {
                Platform.UBER -> parseUberRide(rootNode)
                Platform.NINETY_NINE -> parse99Ride(rootNode)
                Platform.INDRIVE -> parseInDriveRide(rootNode)
                Platform.CABIFY -> parseCabifyRide(rootNode)
                else -> null
            }

            rideData?.let { ride ->
                // Validação: precisa ter pelo menos o valor da corrida
                if (ride.rideValue > 0) {
                    // Supressão de duplicatas com janela de 10s
                    val hash = "${ride.platform}_${String.format("%.2f", ride.rideValue)}_${String.format("%.1f", ride.dropoffDistance)}".hashCode()
                    if (hash == lastRideHash && (now - lastRideHashTime) < DUPLICATE_WINDOW_MS) {
                        Log.d(TAG, "Duplicata suprimida: ${ride.platform} R$${ride.rideValue}")
                        return
                    }
                    lastRideHash = hash
                    lastRideHashTime = now

                    Log.d(TAG, "Corrida detectada: ${ride.platform} R$${ride.rideValue} | ${ride.dropoffDistance}km | ${ride.rideDuration}min | ★${ride.passengerRating}")
                    OverlayService.onRideDetected?.invoke(ride)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar evento: ${e.message}")
        } finally {
            rootNode.recycle()
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "AccessibilityService interrompido")
    }

    private fun detectPlatform(packageName: String): Platform? {
        return Platform.entries.find { it.packageName == packageName }
    }

    // =========================================================================
    // PARSER UBER DRIVER — Baseado em telas reais capturadas em 18/06/2026
    // =========================================================================
    // Textos reais observados na tela:
    //   "UberX" (tipo da corrida)
    //   "R$ 16,60" (valor, texto grande isolado)
    //   "4,90 (275)" ou "★ 4,90 (275)" (rating do passageiro)
    //   "9 minutos (3.9 km) de distância" (distância até embarque)
    //   "Rua Ivan Bassani Bartolomei, Líder / Vila Real" (endereço embarque)
    //   "Viagem de 17 minutos (7.2 km)" (duração e distância da viagem)
    //   "R. Benjamin Constant, 164 D - Centro, Chapecó - SC" (endereço destino)
    //   "Aceitar" ou "Selecionar" (botão de ação)
    // =========================================================================
    private fun parseUberRide(root: AccessibilityNodeInfo): RideData? {
        var rideValue = 0.0
        var pickupDistance = 0.0
        var dropoffDistance = 0.0
        var duration = 0.0
        var rating = 0.0
        var stops = 0
        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""
        var hasAcceptButton = false
        var rideTypeBadge = "" // Badge no topo: "UberX", "Comfort", "Black", etc.

        val allText = collectAllText(root)

        for (text in allText) {
            // Detectar botão "Aceitar" ou "Selecionar" — confirma que é tela de oferta
            if (text.equals("Aceitar", ignoreCase = true) ||
                text.equals("Selecionar", ignoreCase = true) ||
                text.equals("Accept", ignoreCase = true)) {
                hasAcceptButton = true
            }

            // ---- TIPO DE CORRIDA (badge no topo) ----
            // Formato real: "UberX", "Comfort", "Black", "Flash", "Promo", "Green"
            if (rideTypeBadge.isBlank()) {
                val trimmed = text.trim()
                if (trimmed.matches(Regex("""(?i)(UberX|Uber\s*X|Comfort|Black|Flash|Promo|Green|Prioridade|Priority)"""))) {
                    rideTypeBadge = trimmed
                }
            }

            // ---- VALOR DA CORRIDA ----
            // Formato real: "R$ 16,60" ou "R$ 9,69" (texto isolado ou com ícone)
            if (rideValue == 0.0) {
                val valueMatch = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(text)
                if (valueMatch != null) {
                    val v = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (v in 1.0..999.0) rideValue = v
                }
            }

            // ---- RATING DO PASSAGEIRO ----
            // Formato real: "4,90 (275)" ou "★ 4,90 (275)" ou "4.95 (405)"
            if (rating == 0.0) {
                val ratingMatch = Regex("""★?\s*([4-5][.,]\d{1,2})\s*\(\d+\)""").find(text)
                if (ratingMatch != null) {
                    val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (r in 1.0..5.0) rating = r
                }
            }

            // ---- DISTÂNCIA ATÉ EMBARQUE (pickup) ----
            // Formato real: "9 minutos (3.9 km) de distância"
            // Formato real: "11 minutos (6.0 km) de distância"
            val pickupMatch = Regex("""(\d+)\s*minutos?\s*\((\d+[.,]\d+)\s*km\)\s*de\s*dist""", RegexOption.IGNORE_CASE).find(text)
            if (pickupMatch != null && pickupDistance == 0.0) {
                pickupDistance = pickupMatch.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            // ---- DURAÇÃO E DISTÂNCIA DA VIAGEM (dropoff) ----
            // Formato real: "Viagem de 17 minutos (7.2 km)"
            // Formato real: "Viagem de 8 minutos (2.7 km)"
            val tripMatch = Regex("""[Vv]iagem\s+de\s+(\d+)\s*minutos?\s*\((\d+[.,]\d+)\s*km\)""").find(text)
            if (tripMatch != null && dropoffDistance == 0.0) {
                duration = tripMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                dropoffDistance = tripMatch.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            // ---- BAIRROS ----
            // Formato real: "Rua Ivan Bassani Bartolomei, Líder / Vila Real"
            // Formato real: "R. Benjamin Constant, 164 D - Centro, Chapecó - SC"
            // Extrair bairro após " / " ou " - " no endereço
            if (pickupNeighborhood.isBlank() && pickupDistance > 0 && dropoffDistance == 0.0) {
                // Estamos na seção de embarque
                val neighborhoodMatch = Regex("""[/\-]\s*([A-ZÀ-Ú][a-zà-ú]+(?:\s+[A-ZÀ-Ú][a-zà-ú]+)*)""").find(text)
                if (neighborhoodMatch != null) {
                    pickupNeighborhood = neighborhoodMatch.groupValues[1].trim().take(30)
                }
            }
            if (dropoffNeighborhood.isBlank() && dropoffDistance > 0) {
                // Estamos na seção de destino
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

        // Fallback: se não encontrou "Viagem de X minutos (Y km)" mas tem distâncias soltas
        if (dropoffDistance == 0.0 && pickupDistance == 0.0) {
            // Tentar extrair quaisquer distâncias em km da tela
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

        // Fallback: duração se não encontrou no padrão "Viagem de"
        if (duration == 0.0) {
            for (text in allText) {
                val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
                if (durMatch != null) {
                    val d = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    if (d > 0 && d < 120) { duration = d; break }
                }
            }
        }

        // Só retornar se parece ser uma tela de oferta de corrida
        if (rideValue == 0.0 && !hasAcceptButton) return null

        // Detectar tipo de corrida a partir do badge
        val detectedType = if (rideTypeBadge.isNotBlank()) {
            RideType.fromBadgeText(rideTypeBadge, Platform.UBER)
        } else {
            RideType.UBER_X // Default quando não detecta badge
        }

        Log.d(TAG, "Uber parsed: [${detectedType.displayName}] R$$rideValue | pickup=${pickupDistance}km | trip=${dropoffDistance}km/${duration}min | ★$rating")

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
    // Formato 99: similar ao Uber mas com variações
    //   "R$ XX,XX" (valor)
    //   "X,X km" (distância)
    //   "X min" (duração)
    //   "X.XX ★" (rating)
    // =========================================================================
    private fun parse99Ride(root: AccessibilityNodeInfo): RideData? {
        val allText = collectAllText(root)
        var rideValue = 0.0
        var distance = 0.0
        var pickupDistance = 0.0
        var duration = 0.0
        var rating = 0.0
        var stops = 0
        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""

        for (text in allText) {
            // Valor: R$ XX,XX
            if (rideValue == 0.0) {
                val valueMatch = Regex("""R\$\s*(\d{1,4}[.,]\d{2})""").find(text)
                if (valueMatch != null) {
                    val v = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (v in 1.0..999.0) rideValue = v
                }
            }

            // Distância até embarque: "X min (Y km)"
            val pickupMatch = Regex("""(\d+)\s*min(?:utos?)?\s*\((\d+[.,]\d+)\s*km\)""", RegexOption.IGNORE_CASE).find(text)
            if (pickupMatch != null && pickupDistance == 0.0) {
                pickupDistance = pickupMatch.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            // Distância da viagem
            if (distance == 0.0) {
                val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(text)
                if (distMatch != null) {
                    val d = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (d > 0) distance = d
                }
            }

            // Duração
            if (duration == 0.0) {
                val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
                if (durMatch != null) {
                    val d = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                    if (d > 0 && d < 120) duration = d
                }
            }

            // Rating: "4.90" ou "4,90 ★"
            if (rating == 0.0) {
                val ratingMatch = Regex("""([4-5][.,]\d{1,2})\s*[★⭐]?""").find(text)
                if (ratingMatch != null) {
                    val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (r in 3.0..5.0) rating = r
                }
            }

            // Paradas
            if (stops == 0 && text.contains("parada", ignoreCase = true)) stops = 1

            // Bairros
            if (pickupNeighborhood.isBlank()) {
                val m = Regex("""[/\-]\s*([A-ZÀ-Ú][a-zà-ú]+(?:\s+[A-ZÀ-Ú][a-zà-ú]+)*)""").find(text)
                if (m != null && rideValue > 0) pickupNeighborhood = m.groupValues[1].trim().take(30)
            }
        }

        if (rideValue == 0.0) return null

        // Se só encontrou uma distância, é a do dropoff
        val dropoffDist = if (distance > pickupDistance && pickupDistance > 0) distance else distance

        return RideData(
            platform = Platform.NINETY_NINE,
            rideValue = rideValue,
            pickupDistance = pickupDistance,
            dropoffDistance = dropoffDist,
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
    private fun parseInDriveRide(root: AccessibilityNodeInfo): RideData? {
        val allText = collectAllText(root)
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

            // Bairros
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
    private fun parseCabifyRide(root: AccessibilityNodeInfo): RideData? {
        val allText = collectAllText(root)
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

    /**
     * Coleta todos os textos visíveis na árvore de acessibilidade.
     * Percorre recursivamente todos os nós e extrai text + contentDescription.
     */
    private fun collectAllText(node: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        traverseNode(node, texts)
        return texts
    }

    private fun traverseNode(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) texts.add(it.trim()) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) texts.add(it.trim()) }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, texts)
            child.recycle()
        }
    }
}
