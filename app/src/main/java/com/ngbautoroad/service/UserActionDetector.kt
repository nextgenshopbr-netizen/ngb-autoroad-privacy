package com.ngbautoroad.service

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.ngbautoroad.data.model.Platform
import com.ngbautoroad.domain.TelemetryLogger

/**
 * UserActionDetector v1.0
 *
 * Detecta ações do motorista nos apps de corrida via AccessibilityService:
 * - Aceitar corrida (clique no botão ou swipe detectado via mudança de tela)
 * - Recusar corrida (clique no X ou timeout)
 * - Iniciar viagem (clique em "Iniciar viagem")
 * - Finalizar viagem (clique em "Finalizar viagem")
 * - Cancelar (clique em "Cancelar")
 *
 * Usa 3 camadas de detecção:
 * 1. Clique direto em botão (TYPE_VIEW_CLICKED)
 * 2. Mudança de contexto da tela (oferta desaparece → aceite/recusa inferido)
 * 3. Timer de timeout (último recurso)
 */
class UserActionDetector(
    private val context: Context,
    private val onAccepted: () -> Unit,
    private val onRefused: () -> Unit,
    private val onCompleted: () -> Unit,
    private val onCancelled: () -> Unit,
    private val onTripStarted: () -> Unit = {},
    private val onArrived: () -> Unit = {}
) {
    private val telemetry get() = TelemetryLogger.getInstance(context)
    companion object {
        private const val TAG = "NGB_ACTION"

        // === UBER ===
        private val UBER_ACCEPT_BUTTONS = listOf(
            "aceitar", "selecionar", "accept", "confirm"
        )
        private val UBER_REFUSE_BUTTONS = listOf(
            "recusar", "fechar", "dismiss", "✕", "×", "close"
        )
        private val UBER_START_BUTTONS = listOf(
            "iniciar viagem", "start trip", "iniciar corrida",
            "deslize para iniciar", "slide to start"
        )
        private val UBER_COMPLETE_BUTTONS = listOf(
            "finalizar viagem", "encerrar viagem", "end trip",
            "finalizar corrida", "complete trip",
            "deslize para finalizar", "slide to end"
        )
        private val UBER_CANCEL_BUTTONS = listOf(
            "cancelar viagem", "cancel trip", "cancelar corrida"
        )
        private val UBER_ARRIVED_BUTTONS = listOf(
            "cheguei", "estou no local", "arrived", "i'm here"
        )

        // === 99 ===
        private val NINETY_NINE_ACCEPT_BUTTONS = listOf(
            "aceitar corrida", "aceitar", "toque abaixo para aceitar"
        )
        private val NINETY_NINE_REFUSE_BUTTONS = listOf(
            "recusar", "✕", "×", "fechar"
        )
        private val NINETY_NINE_START_BUTTONS = listOf(
            "iniciar corrida", "iniciar viagem"
        )
        private val NINETY_NINE_COMPLETE_BUTTONS = listOf(
            "finalizar corrida", "finalizar viagem", "encerrar"
        )
        private val NINETY_NINE_CANCEL_BUTTONS = listOf(
            "cancelar corrida", "cancelar"
        )
        private val NINETY_NINE_ARRIVED_BUTTONS = listOf(
            "cheguei", "estou no local"
        )

        // === inDrive ===
        private val INDRIVE_ACCEPT_BUTTONS = listOf(
            "aceitar", "accept"
        )
        private val INDRIVE_REFUSE_BUTTONS = listOf(
            "recusar", "decline", "✕", "×"
        )
        private val INDRIVE_START_BUTTONS = listOf(
            "iniciar", "start"
        )
        private val INDRIVE_COMPLETE_BUTTONS = listOf(
            "finalizar", "concluir", "complete"
        )
        private val INDRIVE_CANCEL_BUTTONS = listOf(
            "cancelar", "cancel"
        )
        private val INDRIVE_ARRIVED_BUTTONS = listOf(
            "cheguei", "arrived"
        )

        // Textos que indicam TELA DE OFERTA ATIVA (usados para detecção de timeout)
        private val OFFER_SCREEN_INDICATORS = listOf(
            "aceitar", "selecionar", "toque abaixo para aceitar",
            "accept", "select", "r\$/km", "r$/km"
        )

        // Textos que indicam NAVEGAÇÃO (corrida aceita)
        private val NAVIGATION_INDICATORS = listOf(
            "a caminho", "navegando", "dirigir até", "heading to",
            "buscando passageiro", "indo buscar", "en camino"
        )

        // Textos que indicam TELA DE ESPERA (corrida expirou/recusou)
        private val IDLE_SCREEN_INDICATORS = listOf(
            "procurando viagens", "você está online", "looking for trips",
            "ficar online", "go online", "sem demanda"
        )
    }

    // Estado interno
    private var isMonitoring = false
    private var lastOfferScreenTime = 0L
    private var offerWasVisible = false
    private var currentPlatform: Platform? = null
    // v7.7.0: contador de leituras "ociosas" consecutivas — exige confirmação dupla
    // antes de decretar RECUSADA/EXPIRADA (ver onScreenContentChanged)
    private var consecutiveIdleReads = 0

    /**
     * Inicia monitoramento para uma nova corrida detectada
     */
    fun startMonitoring(platform: Platform) {
        isMonitoring = true
        offerWasVisible = true
        currentPlatform = platform
        lastOfferScreenTime = System.currentTimeMillis()
        consecutiveIdleReads = 0
        Log.d(TAG, "▶ Monitoramento iniciado para ${platform.displayName}")
        telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
            "UserActionDetector: monitoramento iniciado para ${platform.displayName}")
    }

    /**
     * Para monitoramento (corrida já foi resolvida)
     */
    fun stopMonitoring() {
        isMonitoring = false
        offerWasVisible = false
        currentPlatform = null
        consecutiveIdleReads = 0
        Log.d(TAG, "⏹ Monitoramento parado")
    }

    fun isActive(): Boolean = isMonitoring

    /**
     * CAMADA 1: Processa evento TYPE_VIEW_CLICKED para detectar ação do motorista
     * Retorna true se uma ação foi detectada
     */
    fun onViewClicked(event: AccessibilityEvent, packageName: String): Boolean {
        if (!isMonitoring) return false

        val clickedText = buildString {
            event.text.forEach { append(it?.toString()?.lowercase() ?: "") }
            event.contentDescription?.let { append(" ").append(it.toString().lowercase()) }
        }.trim()

        if (clickedText.isEmpty()) return false

        val platform = currentPlatform ?: return false

        Log.d(TAG, "│ Click detectado: \"$clickedText\" em $packageName")
        telemetry.log(TelemetryLogger.Category.PARSER, TelemetryLogger.Level.INFO,
            "UserActionDetector click: \"$clickedText\" pkg=$packageName")

        // Verificar ACEITAR
        val acceptButtons = getAcceptButtons(platform)
        if (matchesAny(clickedText, acceptButtons)) {
            Log.i(TAG, "├─ ✅ CLIQUE EM ACEITAR DETECTADO!")
            telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                "AÇÃO: Motorista ACEITOU corrida (clique em \"$clickedText\")")
            onAccepted()
            return true
        }

        // Verificar RECUSAR
        val refuseButtons = getRefuseButtons(platform)
        if (matchesAny(clickedText, refuseButtons)) {
            Log.i(TAG, "├─ ❌ CLIQUE EM RECUSAR DETECTADO!")
            telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                "AÇÃO: Motorista RECUSOU corrida (clique em \"$clickedText\")")
            onRefused()
            return true
        }

        // Verificar INICIAR VIAGEM
        val startButtons = getStartButtons(platform)
        if (matchesAny(clickedText, startButtons)) {
            Log.i(TAG, "├─ 🚗 CLIQUE EM INICIAR VIAGEM!")
            telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                "AÇÃO: Motorista INICIOU viagem (clique em \"$clickedText\")")
            onTripStarted()
            return true
        }

        // Verificar FINALIZAR
        val completeButtons = getCompleteButtons(platform)
        if (matchesAny(clickedText, completeButtons)) {
            Log.i(TAG, "├─ 🏁 CLIQUE EM FINALIZAR DETECTADO!")
            telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                "AÇÃO: Motorista FINALIZOU corrida (clique em \"$clickedText\")")
            onCompleted()
            return true
        }

        // Verificar CANCELAR
        val cancelButtons = getCancelButtons(platform)
        if (matchesAny(clickedText, cancelButtons)) {
            Log.i(TAG, "├─ 🚫 CLIQUE EM CANCELAR DETECTADO!")
            telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                "AÇÃO: Motorista CANCELOU corrida (clique em \"$clickedText\")")
            onCancelled()
            return true
        }

        // Verificar CHEGUEI
        val arrivedButtons = getArrivedButtons(platform)
        if (matchesAny(clickedText, arrivedButtons)) {
            Log.i(TAG, "├─ 📍 CLIQUE EM CHEGUEI!")
            telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                "AÇÃO: Motorista chegou no local (clique em \"$clickedText\")")
            onArrived()
            return true
        }

        return false
    }

    /**
     * CAMADA 2: Detecta mudança de contexto da tela (oferta sumiu → aceite/timeout)
     * Chamado a cada TYPE_WINDOW_CONTENT_CHANGED
     */
    fun onScreenContentChanged(allTexts: List<String>): Boolean {
        if (!isMonitoring) return false

        // v6.9.14: Ignorar se for tela de ganhos ou aba de notificações,
        // evitando falsos eventos de expiração (timeout)
        if (isEarningsScreen(allTexts) || isNotificationShadeContent(allTexts)) {
            return false
        }

        val lowerTexts = allTexts.map { it.lowercase() }
        val joinedTexts = lowerTexts.joinToString(" ")

        // Verificar se tela de oferta ainda está visível
        val offerStillVisible = OFFER_SCREEN_INDICATORS.any { indicator ->
            joinedTexts.contains(indicator)
        }

        // Verificar se mudou para tela de navegação (corrida aceita via swipe/auto)
        val isNavigationScreen = NAVIGATION_INDICATORS.any { indicator ->
            joinedTexts.contains(indicator)
        } || isNavigationScreen(allTexts)

        // v7.7.0: Verificar se voltou para tela de espera (corrida expirou).
        // ANTES bastava 1 palavra genérica bater (ex.: "você está online" — que pode ser um
        // cabeçalho persistente, presente inclusive COM a oferta na tela). Isso derrubava
        // corridas reais como RECUSADA em 4-15s (task_b3cf48f5, confirmado via telemetria).
        // Agora exige 2+ indicadores batendo — o mesmo critério que isEarningsScreen() já usa
        // com sucesso logo abaixo.
        val isIdleScreen = IDLE_SCREEN_INDICATORS.count { indicator ->
            joinedTexts.contains(indicator)
        } >= 2

        if (offerWasVisible && !offerStillVisible) {
            // A oferta desapareceu! Determinar o que aconteceu
            if (isNavigationScreen) {
                // Mudou para navegação → motorista aceitou (provavelmente via swipe)
                Log.i(TAG, "├─ ✅ ACEITE DETECTADO via mudança de tela (swipe/auto)")
                telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                    "AÇÃO: Corrida ACEITA (tela mudou para navegação - swipe inferido)")
                offerWasVisible = false
                consecutiveIdleReads = 0
                onAccepted()
                return true
            } else if (isIdleScreen) {
                // v7.7.0: Confirmação dupla — só decreta RECUSADA/EXPIRADA na 2ª leitura
                // ociosa CONSECUTIVA. Uma leitura isolada pode ser uma tela de transição
                // (Uber renderizando em etapas); duas leituras seguidas apontando pro mesmo
                // estado ocioso é sinal muito mais confiável de que a oferta realmente sumiu.
                consecutiveIdleReads++
                if (consecutiveIdleReads < 2) {
                    Log.d(TAG, "│  Possível expiração (leitura ${consecutiveIdleReads}/2) — aguardando confirmação")
                    return false
                }
                Log.i(TAG, "├─ ⏰ EXPIRAÇÃO/RECUSA DETECTADA via mudança de tela (confirmado 2x)")
                telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                    "AÇÃO: Corrida EXPIRADA/RECUSADA (tela voltou para espera, confirmado 2x)")
                offerWasVisible = false
                consecutiveIdleReads = 0
                onRefused()
                return true
            } else {
                // Tela mudou mas não sabemos para onde — aguardar mais um ciclo
                consecutiveIdleReads = 0
                Log.d(TAG, "│  Oferta sumiu mas destino incerto — aguardando...")
            }
        }

        // Atualizar estado
        if (offerStillVisible) {
            offerWasVisible = true
            lastOfferScreenTime = System.currentTimeMillis()
            consecutiveIdleReads = 0
        }

        return false
    }

    /**
     * CAMADA 2b: Detecta conclusão/cancelamento durante corrida ativa
     * (após aceite, monitora textos de finalização)
     */
    fun onTextsAfterAccepted(allTexts: List<String>): Boolean {
        if (!isMonitoring) return false

        val platform = currentPlatform ?: return false
        val lowerTexts = allTexts.map { it.lowercase() }
        val joinedTexts = lowerTexts.joinToString(" ")

        // Verificar conclusão
        val completedTexts = getCompletedTexts(platform)
        if (completedTexts.any { joinedTexts.contains(it) }) {
            Log.i(TAG, "├─ 🏁 CONCLUSÃO DETECTADA via texto na tela")
            telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                "AÇÃO: Corrida CONCLUÍDA (texto de conclusão detectado na tela)")
            onCompleted()
            return true
        }

        // Verificar cancelamento
        val cancelledTexts = getCancelledTexts(platform)
        if (cancelledTexts.any { joinedTexts.contains(it) }) {
            Log.i(TAG, "├─ 🚫 CANCELAMENTO DETECTADO via texto na tela")
            telemetry.log(TelemetryLogger.Category.LIFECYCLE, TelemetryLogger.Level.INFO,
                "AÇÃO: Corrida CANCELADA (texto de cancelamento detectado na tela)")
            onCancelled()
            return true
        }

        return false
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private fun matchesAny(text: String, patterns: List<String>): Boolean {
        return patterns.any { pattern -> text.contains(pattern) }
    }

    private fun getAcceptButtons(platform: Platform): List<String> = when (platform) {
        Platform.UBER -> UBER_ACCEPT_BUTTONS
        Platform.NINETY_NINE -> NINETY_NINE_ACCEPT_BUTTONS
        Platform.INDRIVE -> INDRIVE_ACCEPT_BUTTONS
        else -> UBER_ACCEPT_BUTTONS + NINETY_NINE_ACCEPT_BUTTONS + INDRIVE_ACCEPT_BUTTONS
    }

    private fun getRefuseButtons(platform: Platform): List<String> = when (platform) {
        Platform.UBER -> UBER_REFUSE_BUTTONS
        Platform.NINETY_NINE -> NINETY_NINE_REFUSE_BUTTONS
        Platform.INDRIVE -> INDRIVE_REFUSE_BUTTONS
        else -> UBER_REFUSE_BUTTONS + NINETY_NINE_REFUSE_BUTTONS + INDRIVE_REFUSE_BUTTONS
    }

    private fun getStartButtons(platform: Platform): List<String> = when (platform) {
        Platform.UBER -> UBER_START_BUTTONS
        Platform.NINETY_NINE -> NINETY_NINE_START_BUTTONS
        Platform.INDRIVE -> INDRIVE_START_BUTTONS
        else -> UBER_START_BUTTONS + NINETY_NINE_START_BUTTONS + INDRIVE_START_BUTTONS
    }

    private fun getCompleteButtons(platform: Platform): List<String> = when (platform) {
        Platform.UBER -> UBER_COMPLETE_BUTTONS
        Platform.NINETY_NINE -> NINETY_NINE_COMPLETE_BUTTONS
        Platform.INDRIVE -> INDRIVE_COMPLETE_BUTTONS
        else -> UBER_COMPLETE_BUTTONS + NINETY_NINE_COMPLETE_BUTTONS + INDRIVE_COMPLETE_BUTTONS
    }

    private fun getCancelButtons(platform: Platform): List<String> = when (platform) {
        Platform.UBER -> UBER_CANCEL_BUTTONS
        Platform.NINETY_NINE -> NINETY_NINE_CANCEL_BUTTONS
        Platform.INDRIVE -> INDRIVE_CANCEL_BUTTONS
        else -> UBER_CANCEL_BUTTONS + NINETY_NINE_CANCEL_BUTTONS + INDRIVE_CANCEL_BUTTONS
    }

    private fun getArrivedButtons(platform: Platform): List<String> = when (platform) {
        Platform.UBER -> UBER_ARRIVED_BUTTONS
        Platform.NINETY_NINE -> NINETY_NINE_ARRIVED_BUTTONS
        Platform.INDRIVE -> INDRIVE_ARRIVED_BUTTONS
        else -> UBER_ARRIVED_BUTTONS + NINETY_NINE_ARRIVED_BUTTONS + INDRIVE_ARRIVED_BUTTONS
    }

    private fun getCompletedTexts(platform: Platform): List<String> = when (platform) {
        Platform.UBER -> listOf("viagem concluída", "trip completed", "corrida finalizada",
            "avalie o passageiro", "rate rider", "como foi a viagem", "avaliar usuário", "avaliar o usuário")
        Platform.NINETY_NINE -> listOf("corrida finalizada", "avalie", "viagem concluída")
        Platform.INDRIVE -> listOf("concluída", "finalizada", "completed")
        else -> listOf("concluída", "finalizada", "completed", "avalie")
    }

    private fun getCancelledTexts(platform: Platform): List<String> = when (platform) {
        Platform.UBER -> listOf("viagem cancelada", "trip cancelled", "corrida cancelada",
            "cancelada pelo passageiro", "cancelled by rider")
        Platform.NINETY_NINE -> listOf("cancelada", "corrida cancelada")
        Platform.INDRIVE -> listOf("cancelada", "cancelled")
        else -> listOf("cancelada", "cancelled")
    }

    // Utilizado por: onScreenContentChanged
    // Depende de: List<String> contendo a lista de todos os nós de texto visíveis na janela
    private fun isEarningsScreen(texts: List<String>): Boolean {
        val joined = texts.joinToString(" ").lowercase()
        val earningsIndicators = listOf(
            "última viagem", "ver histórico de ganhos", "ver resumo semanal",
            "viagens concluídas", "uber pro", "ver progresso",
            "começar", "você está offline"
        )
        val matchCount = earningsIndicators.count { joined.contains(it) }
        return matchCount >= 2
    }

    // Utilizado por: onScreenContentChanged
    // Depende de: List<String> contendo a lista de todos os nós de texto visíveis na janela
    private fun isNotificationShadeContent(texts: List<String>): Boolean {
        val joined = texts.joinToString(" ").lowercase()
        val shadeIndicators = listOf(
            "recentes", "voltar", "início", "painéis edge", "silenciar",
            "notificações", "gerenciar", "limpar tudo"
        )
        return shadeIndicators.any { joined.contains(it) }
    }

    // Utilizado por: onScreenContentChanged
    // Depende de: List<String> contendo a lista de todos os nós de texto visíveis na janela
    private fun isNavigationScreen(texts: List<String>): Boolean {
        val joined = texts.joinToString(" ").lowercase()
        val hasDestino = joined.contains("destino de")
        val hasCountdown = Regex("""(em|a)\s+\d+(?:[.,]\d+)?\s*(m|km)""").containsMatchIn(joined)
        return hasDestino || hasCountdown
    }
}
