package com.ngbautoroad.service

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
 * Funciona como trigger complementar ao OCR (MediaProjection).
 * Detecta: Uber, 99, inDrive, Cabify
 */
class RideAccessibilityService : AccessibilityService() {

    private var lastProcessedTime = 0L
    private val throttleMs = 300L // Evitar processar múltiplos eventos seguidos

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 200
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

        val now = System.currentTimeMillis()
        if (now - lastProcessedTime < throttleMs) return
        lastProcessedTime = now

        val packageName = event.packageName?.toString() ?: return
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

    // --- Uber Parser ---
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

        for (text in allText) {
            // Valor da corrida: R$ XX,XX ou R$XX,XX
            val valueMatch = Regex("""R\$\s*(\d+[.,]\d{2})""").find(text)
            if (valueMatch != null && rideValue == 0.0) {
                rideValue = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            // Distância: X,X km ou X.X km
            val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(text)
            if (distMatch != null) {
                val dist = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                if (pickupDistance == 0.0) {
                    pickupDistance = dist
                } else if (dropoffDistance == 0.0) {
                    dropoffDistance = dist
                }
            }

            // Duração: X min ou X minutos
            val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
            if (durMatch != null && duration == 0.0) {
                duration = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            }

            // Rating: 4.XX ou 5.0
            val ratingMatch = Regex("""([4-5][.,]\d{1,2})""").find(text)
            if (ratingMatch != null && rating == 0.0) {
                val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                if (r in 1.0..5.0) rating = r
            }

            // Paradas
            if (text.contains("parada", ignoreCase = true) || text.contains("stop", ignoreCase = true)) {
                val stopMatch = Regex("""(\d+)\s*parada""", RegexOption.IGNORE_CASE).find(text)
                stops = stopMatch?.groupValues?.get(1)?.toIntOrNull() ?: 1
            }
        }

        // Se a primeira distância é menor, provavelmente é pickup
        if (pickupDistance > dropoffDistance && dropoffDistance > 0) {
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

    // --- 99 Parser ---
    private fun parse99Ride(root: AccessibilityNodeInfo): RideData? {
        val allText = collectAllText(root)
        var rideValue = 0.0
        var distance = 0.0
        var duration = 0.0
        var rating = 0.0

        for (text in allText) {
            val valueMatch = Regex("""R\$\s*(\d+[.,]\d{2})""").find(text)
            if (valueMatch != null && rideValue == 0.0) {
                rideValue = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(text)
            if (distMatch != null && distance == 0.0) {
                distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
            if (durMatch != null && duration == 0.0) {
                duration = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            }

            val ratingMatch = Regex("""([4-5][.,]\d{1,2})""").find(text)
            if (ratingMatch != null && rating == 0.0) {
                val r = ratingMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
                if (r in 1.0..5.0) rating = r
            }
        }

        return RideData(
            platform = Platform.NINETY_NINE,
            rideValue = rideValue,
            dropoffDistance = distance,
            rideDuration = duration,
            passengerRating = rating
        )
    }

    // --- inDrive Parser ---
    private fun parseInDriveRide(root: AccessibilityNodeInfo): RideData? {
        val allText = collectAllText(root)
        var rideValue = 0.0
        var distance = 0.0
        var duration = 0.0

        for (text in allText) {
            val valueMatch = Regex("""R\$\s*(\d+[.,]\d{2})""").find(text)
            if (valueMatch != null && rideValue == 0.0) {
                rideValue = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(text)
            if (distMatch != null && distance == 0.0) {
                distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
            if (durMatch != null && duration == 0.0) {
                duration = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            }
        }

        return RideData(
            platform = Platform.INDRIVE,
            rideValue = rideValue,
            dropoffDistance = distance,
            rideDuration = duration
        )
    }

    // --- Cabify Parser ---
    private fun parseCabifyRide(root: AccessibilityNodeInfo): RideData? {
        val allText = collectAllText(root)
        var rideValue = 0.0
        var distance = 0.0
        var duration = 0.0

        for (text in allText) {
            val valueMatch = Regex("""R\$\s*(\d+[.,]\d{2})""").find(text)
            if (valueMatch != null && rideValue == 0.0) {
                rideValue = valueMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            val distMatch = Regex("""(\d+[.,]\d+)\s*km""", RegexOption.IGNORE_CASE).find(text)
            if (distMatch != null && distance == 0.0) {
                distance = distMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 0.0
            }

            val durMatch = Regex("""(\d+)\s*min""", RegexOption.IGNORE_CASE).find(text)
            if (durMatch != null && duration == 0.0) {
                duration = durMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            }
        }

        return RideData(
            platform = Platform.CABIFY,
            rideValue = rideValue,
            dropoffDistance = distance,
            rideDuration = duration
        )
    }

    // --- Utility ---
    private fun collectAllText(node: AccessibilityNodeInfo): List<String> {
        val texts = mutableListOf<String>()
        traverseNode(node, texts)
        return texts
    }

    private fun traverseNode(node: AccessibilityNodeInfo, texts: MutableList<String>) {
        node.text?.toString()?.let { if (it.isNotBlank()) texts.add(it) }
        node.contentDescription?.toString()?.let { if (it.isNotBlank()) texts.add(it) }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseNode(child, texts)
            child.recycle()
        }
    }
}
