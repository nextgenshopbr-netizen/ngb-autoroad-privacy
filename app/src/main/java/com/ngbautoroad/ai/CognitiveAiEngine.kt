package com.ngbautoroad.ai

import android.content.Context
import kotlinx.coroutines.flow.firstOrNull

/**
 * Motor Cognitivo do NGB AutoRoad.
 *
 * Usa geracao de linguagem natural (NLG) ultrarrapida baseada na heuristica do AiBrain.
 * Totalmente offline e compativel com todos os aparelhos.
 */
class CognitiveAiEngine(
    private val context: Context,
    private val brainRepository: AiBrainRepository
) {

    /**
     * Gera um insight conversacional de alto nivel para o motorista baseado
     * no cruzamento de todos os modulos.
     */
    suspend fun generateDailyInsight(): String {
        val currentState = brainRepository.cognitiveStateFlow.firstOrNull()
            ?: return "Os sensores do veiculo ainda estao inicializando..."

        return generateViaFastHeuristics(currentState)
    }

    /**
     * Heuristica super rapida do AiBrain — funciona em 100% dos aparelhos.
     */
    private fun generateViaFastHeuristics(state: AiBrainRepository.BrainState): String {
        val earnedStr = String.format("%.2f", state.totalEarnedToday)
        val progressStr = String.format("%.0f", state.goalProgressPercentage)

        return "Resumo Rapido: Voce ganhou R$ $earnedStr ($progressStr% da meta). Dica: ${state.suggestion}"
    }
}
