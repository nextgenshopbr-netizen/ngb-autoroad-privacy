package com.ngbautoroad.service

// ============================================================================
// ARQUIVO: RideAccessibilityService.kt
// LOCALIZAÇÃO: service/RideAccessibilityService.kt
// RESPONSABILIDADE: Captura dados de corrida via AccessibilityService (sem OCR)
// BLOCOS:
//   - onAccessibilityEvent: Detecta pacotes de apps de corrida
//   - parseUberRide: Parser específico para Uber Driver
//   - parse99Ride: Parser específico para 99 Driver
//   - parseInDriveRide: Parser específico para inDrive
//   - parseCabifyRide: Parser específico para Cabify Driver
//   - extractNeighborhood: Regex para extrair bairros de texto
// DEPENDÊNCIAS:
//   - data/model/RideData.kt → RideData, Platform
//   - service/OverlayService.kt → envia RideData via Intent
// PROTEÇÕES:
//   - Deduplicação: hash de (platform+value+distance+timestamp/10s)
//   - Throttle adaptativo por plataforma (400-600ms)
//   - Regex defensivo: não crasha se texto não bate
// ============================================================================

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ngbautoroad.data.model.Platform
import com.ngbautoroad.data.model.RideData

/**
 * AccessibilityService que detecta quando um app de transporte exibe
 * uma oferta de corrida e extrai os dados via árvore de nós.
 *
 * v4.0 — Melhorias:
 * - Parsers melhorados para Uber, 99, inDrive, Cabify (item 2.1)
 * - Extração de bairros via padrões de texto (item 2.3)
 * - Supressão de duplicatas no serviço (item 2.2)
 * - Throttle adaptativo baseado no app ativo (item 2.4)
 */
class RideAccessibilityService : AccessibilityService() {

    private var lastProcessedTime = 0L
    private var lastRideHash: Int = 0
    private val DUPLICATE_WINDOW_MS = 2500L

    // Throttle por plataforma (ms) — plataformas mais rápidas precisam de throttle menor
    private val THROTTLE_BY_PLATFORM = mapOf(
        "com.ubercab.driver" to 400L,
        "com.ninety9.driver" to 500L,
        "com.machfrankfurt.android" to 600L,
        "com.cabify.driver" to 500L
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 150
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
        val throttle = THROTTLE_BY_PLATFORM[packageName] ?: 400L
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
                if (ride.rideValue > 0 || ride.dropoffDistance > 0) {
                    // Supressão de duplicatas (item 2.2)
                    val hash = "${ride.platform}_${String.format("%.1f", ride.rideValue)}_${String.format("%.1f", ride.dropoffDistance)}".hashCode()
                    if (hash == lastRideHash && (now - lastProcessedTime) < DUPLICATE_WINDOW_MS) return
                    lastRideHash = hash
                    OverlayService.onRideDetected?.invoke(ride)
                }
            }
        } finally {
            rootNode.recycle()
        }
    }

    override fun onInterrupt() {}

    private fun detectPlatform(packageName: String): Platform? {
        return Platform.entries.find { it.packageName == packageName }
    }

    // --- Uber Parser (melhorado - item 2.1) ---
    private fun parseUberRide(root: AccessibilityNodeInfo): RideData? {
        var rideValue = 0.0
        var pickupDistance = 0.0
        var dropoffDistance = 0.0
        var duration = 0.0
        var rating = 0.0
        var stops = 0
        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""

        val allText = collectAllText(root)
        val allTextsJoined = allText.joinToString(" | ")

        // Detectar padrão de corrida Uber: "R$ XX,XX • X km • X min"
        val uberRidePattern = Regex("""R\$\s*(\d+[.,]\d{2})\s*[•·]\s*(\d+[.,]\d+)\s*km\s*[•·]\s*(\d+)\s*min""", RegexOption.IGNORE_CASE)
        val uberMatch = uberRidePattern.find(allTextsJoined)
        if (uberMatch != null) {
            rideValue = uberMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            dropoffDistance = uberMatch.groupValues[2].replace(",", ".").toDoubleOrNull() ?: 0.0
            duration = uberMatch.groupValues[3].toDoubleOrNull() ?: 0.0
        }

        for (text in allText) {
            // Valor da corrida: R$ XX,XX ou R$XX,XX
            if (rideValue == 0.0) {
                val valueMatch = Regex("""R\$\s*(\d+[.,]\d{2})""").find(text)
                if (valueMatch != null) {
                    rideValue = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                }
            }

            // Distâncias: múltiplos padrões
            val distMatches = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).findAll(text)
            for (dm in distMatches) {
                val dist = dm.groupValues[1].replace(",", ".").toDoubleOrNull() ?: continue
                if (pickupDistance == 0.0 && dist < 10.0) {
                    pickupDistance = dist
                } else if (dropoffDistance == 0.0) {
                    dropoffDistance = dist
                }
            }

            // Duração: X min, X minutos, ~X min
            if (duration == 0.0) {
                val durMatch = Regex("""~?(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
                if (durMatch != null) {
                    duration = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                }
            }

            // Rating: 4.XX ★ ou (4.XX)
            if (rating == 0.0) {
                val ratingMatch = Regex("""([4-5][.,]\d{1,2})\s*[★*]?""").find(text)
                if (ratingMatch != null) {
                    val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (r in 1.0..5.0) rating = r
                }
            }

            // Paradas
            if (stops == 0) {
                val stopMatch = Regex("""(\d+)\s*parada""", RegexOption.IGNORE_CASE).find(text)
                    ?: Regex("""(\d+)\s*stop""", RegexOption.IGNORE_CASE).find(text)
                stops = stopMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                if (stops == 0 && (text.contains("parada", ignoreCase = true) || text.contains("stop", ignoreCase = true))) {
                    stops = 1
                }
            }

            // Bairros (item 2.3) — padrão "Embarque em X" ou "Destino: X"
            if (pickupNeighborhood.isBlank()) {
                val pickupMatch = Regex("""(?:embarque|pickup|origem|partida)[:\s]+([A-ZÀ-Ú][a-zà-ú\s]+)""", RegexOption.IGNORE_CASE).find(text)
                if (pickupMatch != null) {
                    pickupNeighborhood = pickupMatch.groupValues[1].trim().take(30)
                }
            }
            if (dropoffNeighborhood.isBlank()) {
                val dropoffMatch = Regex("""(?:destino|desembarque|dropoff)[:\s]+([A-ZÀ-Ú][a-zà-ú\s]+)""", RegexOption.IGNORE_CASE).find(text)
                if (dropoffMatch != null) {
                    dropoffNeighborhood = dropoffMatch.groupValues[1].trim().take(30)
                }
            }
        }

        // Corrigir ordem pickup/dropoff se necessário
        if (pickupDistance > dropoffDistance && dropoffDistance > 0 && pickupDistance > 3.0) {
            val temp = pickupDistance
            pickupDistance = dropoffDistance
            dropoffDistance = temp
        }

        return RideData(
            platform = Platform.UBER,
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

    // --- 99 Parser (melhorado - item 2.1) ---
    private fun parse99Ride(root: AccessibilityNodeInfo): RideData? {
        val allText = collectAllText(root)
        var rideValue = 0.0
        var distance = 0.0
        var duration = 0.0
        var rating = 0.0
        var stops = 0
        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""

        for (text in allText) {
            // Valor: R$ XX,XX ou R$XX
            if (rideValue == 0.0) {
                val valueMatch = Regex("""R\$\s*(\d+[.,]?\d*)""").find(text)
                if (valueMatch != null) {
                    rideValue = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                }
            }

            // Distância
            if (distance == 0.0) {
                val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(text)
                if (distMatch != null) {
                    distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                }
            }

            // Duração
            if (duration == 0.0) {
                val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
                if (durMatch != null) duration = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            }

            // Rating
            if (rating == 0.0) {
                val ratingMatch = Regex("""([4-5][.,]\d{1,2})""").find(text)
                if (ratingMatch != null) {
                    val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (r in 1.0..5.0) rating = r
                }
            }

            // Paradas
            if (stops == 0 && text.contains("parada", ignoreCase = true)) stops = 1

            // Bairros (item 2.3)
            if (pickupNeighborhood.isBlank()) {
                val m = Regex("""(?:de|from|origem)[:\s]+([A-ZÀ-Ú][a-zà-ú\s]+)""", RegexOption.IGNORE_CASE).find(text)
                if (m != null) pickupNeighborhood = m.groupValues[1].trim().take(30)
            }
            if (dropoffNeighborhood.isBlank()) {
                val m = Regex("""(?:para|to|destino)[:\s]+([A-ZÀ-Ú][a-zà-ú\s]+)""", RegexOption.IGNORE_CASE).find(text)
                if (m != null) dropoffNeighborhood = m.groupValues[1].trim().take(30)
            }
        }

        return RideData(
            platform = Platform.NINETY_NINE,
            rideValue = rideValue,
            dropoffDistance = distance,
            rideDuration = duration,
            passengerRating = rating,
            intermediateStops = stops,
            pickupNeighborhood = pickupNeighborhood,
            dropoffNeighborhood = dropoffNeighborhood
        )
    }

    // --- inDrive Parser (melhorado - item 2.1) ---
    private fun parseInDriveRide(root: AccessibilityNodeInfo): RideData? {
        val allText = collectAllText(root)
        var rideValue = 0.0
        var distance = 0.0
        var duration = 0.0
        var pickupNeighborhood = ""
        var dropoffNeighborhood = ""

        for (text in allText) {
            if (rideValue == 0.0) {
                // inDrive pode mostrar valor sem "R$" às vezes
                val valueMatch = Regex("""R?\$?\s*(\d+[.,]\d{2})""").find(text)
                if (valueMatch != null) {
                    rideValue = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                }
            }

            if (distance == 0.0) {
                val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(text)
                if (distMatch != null) distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            if (duration == 0.0) {
                val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
                if (durMatch != null) duration = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            }

            // Bairros (item 2.3)
            if (pickupNeighborhood.isBlank()) {
                val m = Regex("""(?:de|from)[:\s]+([A-ZÀ-Ú][a-zà-ú\s]+)""", RegexOption.IGNORE_CASE).find(text)
                if (m != null) pickupNeighborhood = m.groupValues[1].trim().take(30)
            }
            if (dropoffNeighborhood.isBlank()) {
                val m = Regex("""(?:para|to)[:\s]+([A-ZÀ-Ú][a-zà-ú\s]+)""", RegexOption.IGNORE_CASE).find(text)
                if (m != null) dropoffNeighborhood = m.groupValues[1].trim().take(30)
            }
        }

        return RideData(
            platform = Platform.INDRIVE,
            rideValue = rideValue,
            dropoffDistance = distance,
            rideDuration = duration,
            pickupNeighborhood = pickupNeighborhood,
            dropoffNeighborhood = dropoffNeighborhood
        )
    }

    // --- Cabify Parser (melhorado - item 2.1) ---
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
                val valueMatch = Regex("""R\$\s*(\d+[.,]\d{2})""").find(text)
                if (valueMatch != null) rideValue = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            if (distance == 0.0) {
                val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(text)
                if (distMatch != null) distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            if (duration == 0.0) {
                val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
                if (durMatch != null) duration = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            }

            if (rating == 0.0) {
                val ratingMatch = Regex("""([4-5][.,]\d{1,2})""").find(text)
                if (ratingMatch != null) {
                    val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                    if (r in 1.0..5.0) rating = r
                }
            }

            // Bairros (item 2.3)
            if (pickupNeighborhood.isBlank()) {
                val m = Regex("""(?:recogida|pickup|embarque)[:\s]+([A-ZÀ-Ú][a-zà-ú\s]+)""", RegexOption.IGNORE_CASE).find(text)
                if (m != null) pickupNeighborhood = m.groupValues[1].trim().take(30)
            }
            if (dropoffNeighborhood.isBlank()) {
                val m = Regex("""(?:destino|destino|dropoff)[:\s]+([A-ZÀ-Ú][a-zà-ú\s]+)""", RegexOption.IGNORE_CASE).find(text)
                if (m != null) dropoffNeighborhood = m.groupValues[1].trim().take(30)
            }
        }

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

    // --- Utility ---
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
