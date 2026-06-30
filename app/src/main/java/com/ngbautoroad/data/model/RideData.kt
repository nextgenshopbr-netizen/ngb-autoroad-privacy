package com.ngbautoroad.data.model

// ============================================================================
// ARQUIVO: RideData.kt
// LOCALIZAÇÃO: data/model/RideData.kt
// RESPONSABILIDADE: Modelos de dados centrais do sistema — corridas, critérios,
//                   thresholds, scores, zonas bloqueadas, cards e dashboard
// DEPENDENTES (quem usa):
//   - domain/RideScorer.kt → usa RideData, CriteriaWeights, DriverThresholds,
//     BlockedNeighborhood, RideScore, CriteriaScore, ThresholdViolation, ScoreLevel
//   - service/OverlayService.kt → cria RideData a partir de OCR/Accessibility
//   - service/RideAccessibilityService.kt → popula RideData
//   - service/OcrCaptureService.kt → popula RideData
//   - ui/criteria/CriteriaTab.kt → edita CriteriaWeights e DriverThresholds
//   - ui/card/CardTab.kt → usa CardModel, RideData
//   - ui/dashboard/DashboardTab.kt → usa DashboardData
//   - ui/history/HistoryTab.kt → exibe RideData e RideScore
//   - data/prefs/PrefsManager.kt → persiste CriteriaWeights e DriverThresholds
//   - data/db/RideHistoryEntity.kt → serializa RideData para persistência
// ============================================================================

// ============================================================================
// BLOCO 1: RideData — Dados extraídos de uma corrida
// LINHAS: 28-42
// LÓGICA: Contém todos os campos que o OCR/Accessibility pode capturar.
//         Campos calculados (valuePerKm, valuePerHour) são propriedades derivadas.
// PROTEÇÃO: Campos com default=0.0 permitem dados parciais sem crash.
// ============================================================================
/**
 * Dados extraídos de uma corrida via OCR/Accessibility
 */
@kotlinx.serialization.Serializable
data class RideData(
    val platform: Platform = Platform.UNKNOWN,
    val rideType: RideType = RideType.UNKNOWN, // Tipo da corrida (UberX, Comfort, Black, etc.)
    val rideValue: Double = 0.0,          // Valor da corrida (R$) — campo obrigatório
    val rideDuration: Double = 0.0,       // Duração da corrida (minutos) — pode ser 0 se OCR não capturou
    val pickupDistance: Double = 0.0,     // Distância até embarque (km)
    val dropoffDistance: Double = 0.0,    // Distância até desembarque (km) — pode ser 0 se OCR não capturou
    val passengerRating: Double = 0.0,   // Avaliação do passageiro (0-5) — 0 = plataforma não forneceu
    val intermediateStops: Int = 0,       // Número de paradas intermediárias
    val pickupNeighborhood: String = "",  // Bairro de embarque (extraído por regex)
    val dropoffNeighborhood: String = "", // Bairro de destino (extraído por regex)
    val isSimulation: Boolean = false,    // Flag: true = dados simulados (não salvar no histórico)
    val timestamp: Long = System.currentTimeMillis(),
    val metadata: Map<String, String>? = null  // v6.7.0: Metadados extras (shiftHours, gpsKm, etc.)
) {
    // Propriedade calculada: R$/km (protegida contra divisão por zero)
    val valuePerKm: Double
        get() = if (dropoffDistance > 0) rideValue / dropoffDistance else 0.0

    // Propriedade calculada: R$/hora (protegida contra divisão por zero)
    val valuePerHour: Double
        get() = if (rideDuration > 0) (rideValue / rideDuration) * 60.0 else 0.0
}

// ============================================================================
// BLOCO 2: Platform — Enum de plataformas suportadas
// LINHAS: 48-54
// LÓGICA: Cada plataforma tem displayName (UI) e packageName (detecção automática)
// DEPENDENTE: RideAccessibilityService detecta plataforma pelo packageName
// ============================================================================
@kotlinx.serialization.Serializable
enum class Platform(val displayName: String, val packageName: String) {
    UBER("Uber", "com.ubercab.driver"),
    // v6.9.7: Package real do 99 Motorista na Play Store é com.app99.driver
    // (com.ninety9.driver era incorreto — nunca existiu)
    NINETY_NINE("99", "com.app99.driver"),
    INDRIVE("inDrive", "com.machfrankfurt.android"),
    CABIFY("Cabify", "com.cabify.driver"),
    ORB("Orb", "br.com.orb.taxi.drivermachine"), // v7.6.0: app de táxi Orb
    UNKNOWN("Desconhecido", "")
}

// ============================================================================
// BLOCO 2.1: RideType — Tipo/categoria da corrida dentro da plataforma
// LÓGICA: Uber tem UberX, Comfort, Black, Flash, etc.
//         99 tem 99Pop, 99Comfort. inDrive e Cabify não diferenciam.
//         O tipo influencia o valor esperado e pode ser critério de aceitação.
// DETECÇÃO: Parser do AccessibilityService lê o badge no topo do card da Uber
// DEPENDENTE: OverlayCard exibe tipo, RideScorer pode usar como critério futuro
// ============================================================================
@kotlinx.serialization.Serializable
enum class RideType(val displayName: String, val platform: Platform) {
    // Uber
    UBER_X("UberX", Platform.UBER),
    UBER_COMFORT("Comfort", Platform.UBER),
    UBER_BLACK("Black", Platform.UBER),
    UBER_FLASH("Flash", Platform.UBER),
    UBER_PROMO("Promo", Platform.UBER),
    UBER_GREEN("Green", Platform.UBER),
    UBER_PRIORITY("Prioridade", Platform.UBER),
    // 99
    NINETY_NINE_POP("99Pop", Platform.NINETY_NINE),
    NINETY_NINE_COMFORT("99Comfort", Platform.NINETY_NINE),
    // inDrive
    INDRIVE_STANDARD("Padrão", Platform.INDRIVE),
    // Cabify
    CABIFY_STANDARD("Padrão", Platform.CABIFY),
    // Desconhecido
    UNKNOWN("Desconhecido", Platform.UNKNOWN);

    companion object {
        /**
         * Detecta o RideType a partir do texto do badge (ex: "UberX", "Comfort", "Black")
         */
        fun fromBadgeText(text: String, platform: Platform): RideType {
            val normalized = text.trim().lowercase()
            return when (platform) {
                Platform.UBER -> when {
                    normalized.contains("uberx") || normalized == "x" -> UBER_X
                    normalized.contains("comfort") || normalized.contains("confort") -> UBER_COMFORT
                    normalized.contains("black") -> UBER_BLACK
                    normalized.contains("flash") -> UBER_FLASH
                    normalized.contains("promo") -> UBER_PROMO
                    normalized.contains("green") || normalized.contains("planet") -> UBER_GREEN
                    normalized.contains("prioridade") || normalized.contains("priority") -> UBER_PRIORITY
                    else -> UBER_X // Default Uber
                }
                Platform.NINETY_NINE -> when {
                    normalized.contains("comfort") || normalized.contains("confort") -> NINETY_NINE_COMFORT
                    else -> NINETY_NINE_POP
                }
                Platform.INDRIVE -> INDRIVE_STANDARD
                Platform.CABIFY -> CABIFY_STANDARD
                else -> UNKNOWN
            }
        }
    }
}

// ============================================================================
// BLOCO 3: RideStatus — Status da corrida no histórico
// LINHAS: 58-63
// LÓGICA: Indica a fase/resultado da corrida no ciclo de vida
// v6.1.0: Adicionados PENDING, COMPLETED, UNCERTAIN para lifecycle completo
// DEPENDENTE: RideHistoryEntity.status, HistoryTab filtros, RideLifecycleManager
// ============================================================================
enum class RideStatus(val displayName: String) {
    PENDING("Pendente"),       // v6.1.0: Corrida detectada, aguardando decisão
    ACCEPTED("Aceita"),        // Motorista aceitou, em andamento
    COMPLETED("Concluída"),    // v6.1.0: Viagem concluída com sucesso (ganho registrado)
    REFUSED("Recusada"),       // Motorista recusou explicitamente
    CANCELLED("Cancelada"),    // Cancelada por motorista ou passageiro
    EXPIRED("Expirada"),       // Card expirou sem ação
    UNCERTAIN("Incerta")       // v6.1.0: Não detectou resultado, motorista deve confirmar
}

// ============================================================================
// BLOCO 4: CriteriaWeights — Pesos dos critérios (soma DEVE ser 100)
// LINHAS: 68-82
// LÓGICA: Cada campo é 0-100. totalUsed calcula a soma para validação na UI.
//         Se um campo é 0, o critério é ignorado no cálculo do score.
// VALIDAÇÃO: CriteriaTab.kt impede que totalUsed > 100 via slider com maxValue dinâmico
// PERSISTÊNCIA: PrefsManager.criteriaWeightsFlow
// ============================================================================
@kotlinx.serialization.Serializable
data class CriteriaWeights(
    val valuePerKm: Int = 30,         // Peso: Valor por quilômetro
    val valuePerHour: Int = 30,       // Peso: Valor por hora
    val intermediateStops: Int = 25,  // Peso: Paradas intermediárias
    val passengerRating: Int = 15,    // Peso: Avaliação do passageiro
    val rideValue: Int = 0,           // Peso: Valor absoluto da corrida
    val rideDuration: Int = 0,        // Peso: Duração da corrida
    val pickupDistance: Int = 0,      // Peso: Distância até embarque
    val dropoffDistance: Int = 0      // Peso: Distância até destino
) {
    // Soma de todos os pesos — DEVE ser exatamente 100
    val totalUsed: Int
        get() = valuePerKm + valuePerHour + intermediateStops + passengerRating +
                rideValue + rideDuration + pickupDistance + dropoffDistance
}

// ============================================================================
// BLOCO 5: DriverThresholds — Valores mínimos/máximos desejados pelo motorista
// LINHAS: 87-109
// LÓGICA: Corridas que violam estes limites recebem PENALIDADE no score.
//         Cada campo tem um método isXxxActive() que retorna true se configurado.
//         Valor 0 = não configurado = sem penalidade.
// PERSISTÊNCIA: PrefsManager.driverThresholdsFlow
// DEPENDENTE: RideScorer aplica penalidade de 50-70% do peso do critério violado
// ============================================================================
@kotlinx.serialization.Serializable
data class DriverThresholds(
    val minValuePerKm: Double = 2.00,        // R$/km mínimo desejado (padrão 2.00)
    val minValuePerHour: Double = 42.00,      // R$/hora mínimo desejado (padrão 42.00)
    val minRideValue: Double = 0.0,         // Valor mínimo da corrida R$ (0=desativado)
    val maxPickupDistance: Double = 0.0,    // Distância máxima até embarque km (0=desativado)
    val minPassengerRating: Double = 4.70,   // Avaliação mínima do passageiro (padrão 4.70, max=5.0)
    val maxDuration: Double = 0.0,          // Duração máxima aceitável min (0=desativado)
    val maxStops: Int = 1,                  // Máximo de paradas aceitáveis (padrão 1)
    val minDropoffDistance: Double = 0.0    // Distância mínima do destino km (0=desativado)
) {
    fun isValuePerKmActive() = minValuePerKm > 0
    fun isValuePerHourActive() = minValuePerHour > 0
    fun isRideValueActive() = minRideValue > 0
    fun isPickupDistanceActive() = maxPickupDistance > 0
    fun isPassengerRatingActive() = minPassengerRating > 0
    fun isDurationActive() = maxDuration > 0
    fun isStopsActive() = maxStops < 99
    fun isDropoffDistanceActive() = minDropoffDistance > 0
}

// ============================================================================
// BLOCO 6: RideScore — Resultado do cálculo de score
// LINHAS: 114-128
// LÓGICA: Contém score total (0-100), breakdown por critério e violações
// DEPENDENTE: OverlayCard exibe totalScore e scoreColor
//             HistoryTab exibe criteriaScores no detalhe
// ============================================================================
data class RideScore(
    val totalScore: Double = 0.0,
    val criteriaScores: Map<String, CriteriaScore> = emptyMap(),
    val thresholdViolations: List<ThresholdViolation> = emptyList(),
    // v7.5.0: Penalidade de bairro bloqueado aplicada (0 = não bloqueado).
    // Exposto para o OverlayCard pintar o bairro de vermelho quando estiver puxando o score.
    val pickupPenalty: Int = 0,
    val dropoffPenalty: Int = 0
) {
    val scoreColor: ScoreLevel
        get() = when {
            totalScore >= 70 -> ScoreLevel.GREEN
            totalScore >= 50 -> ScoreLevel.YELLOW
            totalScore >= 30 -> ScoreLevel.ORANGE
            else -> ScoreLevel.RED
        }

    val hasViolations: Boolean get() = thresholdViolations.isNotEmpty()
}

// ============================================================================
// BLOCO 7: Modelos auxiliares de Score
// LINHAS: 132-151
// ============================================================================
data class ThresholdViolation(
    val criteriaName: String,
    val currentValue: Double,
    val minimumRequired: Double,
    val penaltyApplied: Double
)

data class CriteriaScore(
    val name: String,
    val rawValue: Double,
    val normalizedScore: Double, // 0-100
    val weight: Int,
    val weightedScore: Double,   // normalizedScore * weight / 100
    val level: ScoreLevel
)

enum class ScoreLevel {
    GREEN, YELLOW, ORANGE, RED
}

// ============================================================================
// BLOCO 8: BlockedNeighborhood e BlockedZone — Zonas/bairros bloqueados
// LINHAS: 155-180
// LÓGICA: Motorista configura bairros ou zonas no mapa que não quer ir.
//         Corridas com pickup/dropoff nesses locais recebem penalidade.
// CONFIGURAÇÃO: ZoneMapActivity (mapa interativo)
// PERSISTÊNCIA: PrefsManager (JSON serializado)
// DEPENDENTE: RideScorer aplica penaltyWeight como subtração direta do score
// ============================================================================
data class BlockedNeighborhood(
    val name: String,
    val type: NeighborhoodType,
    val penaltyWeight: Int = 20 // Peso da penalidade (0-100 pontos subtraídos)
)

enum class NeighborhoodType {
    PICKUP,   // Não quero pegar passageiro neste bairro
    DROPOFF   // Não quero ir para este bairro
}

data class BlockedZone(
    val id: String = "",
    val name: String = "",
    val type: NeighborhoodType = NeighborhoodType.DROPOFF,
    val points: List<GeoPoint> = emptyList(),
    val isActive: Boolean = true,
    val penaltyWeight: Int = 30
)

data class GeoPoint(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)

// ============================================================================
// BLOCO 9: CardModel — Modelo visual de card (customizado pelo motorista)
// LINHAS: 188-202
// LÓGICA: Define aparência do card overlay (cores, bordas, fontes)
// CONFIGURAÇÃO: CardEditorActivity
// PERSISTÊNCIA: PrefsManager (card1ModelId, card2ModelId)
// DEPENDENTE: OverlayCard.kt renderiza com estes valores
// ============================================================================
data class CardModel(
    val id: Int,
    val name: String,
    val backgroundColor: Long = 0xFF101830,
    val textColor: Long = 0xFFFFFFFF,
    val accentColor: Long = 0xFF4F6BFF,
    val borderColor: Long = 0xFF4F6BFF,
    val borderRadius: Int = 12,
    val fontSize: Int = 14,
    val showPlatformIcon: Boolean = true,
    val showScore: Boolean = true,
    val isCustom: Boolean = false,
    val isFavorite: Boolean = false,
    val isAiDriven: Boolean = false // <-- Novo sinalizador de UI controlada pela IA
)

// ============================================================================
// BLOCO 10: DashboardData — Dados agregados para o painel principal
// LINHAS: 208-224
// LÓGICA: Resumo de corridas, ganhos e métricas do dia/semana/mês
// POPULADO POR: DashboardTab.kt via queries ao RideHistoryDao
// ============================================================================
data class DashboardData(
    val totalRidesToday: Int = 0,
    val totalRidesWeek: Int = 0,
    val totalRidesMonth: Int = 0,
    val acceptedToday: Int = 0,
    val refusedToday: Int = 0,
    val cancelledToday: Int = 0,
    val averageScoreToday: Double = 0.0,
    val averageScoreWeek: Double = 0.0,
    val totalEarningsToday: Double = 0.0,
    val totalEarningsWeek: Double = 0.0,
    val totalEarningsMonth: Double = 0.0,
    val bestRideToday: Double = 0.0,
    val averageValuePerKm: Double = 0.0,
    val topPlatform: String = "",
    val serviceActive: Boolean = false,
    val protectionActive: Boolean = false
)
