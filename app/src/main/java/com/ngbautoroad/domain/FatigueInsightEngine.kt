package com.ngbautoroad.domain

import android.content.Context
import com.ngbautoroad.data.db.FinanceDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ============================================================================
// ARQUIVO: FatigueInsightEngine.kt
// VERSÃO: v6.9.0
// RESPONSABILIDADE: Análise inteligente de fadiga com dados REAIS
//   - NÃO usa alertas sonoros nem visuais intrusivos
//   - Mostra ao motorista em NÚMEROS REAIS que ele poderia ter descansado
//     mais e ganho o mesmo (ou mais)
//   - Compara turnos longos vs curtos: horários, corridas, valores
//   - Gera insights no final do turno ou quando o motorista consulta
//   - O motorista PODE optar por receber alertas (opt-in), mas por padrão
//     a IA apenas mostra dados comparativos
// DEPENDENTES:
//   - DashboardTab.kt → exibe card de insight (não intrusivo)
//   - ShiftManager.kt → alimenta dados de turno
//   - ShiftHistoryManager.kt → consulta histórico para comparações
//   - FinanceDRE.kt → dados financeiros reais
// ============================================================================

/**
 * Insight gerado pela IA sobre fadiga e produtividade.
 * Mostra dados reais comparativos sem ser intrusivo.
 */
data class FatigueInsight(
    val type: InsightType,
    val title: String,
    val message: String,
    val dataComparison: FatigueComparison? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val priority: InsightPriority = InsightPriority.NORMAL
)

enum class InsightType {
    EFFICIENCY_COMPARISON,    // Comparação de eficiência entre turnos
    OPTIMAL_HOURS,           // Horários ótimos baseados no histórico
    DIMINISHING_RETURNS,     // Ponto onde ganho/hora começa a cair
    REST_BENEFIT,            // Benefício concreto de descansar mais
    WEEKLY_PATTERN           // Padrão semanal de produtividade
}

enum class InsightPriority {
    LOW,       // Informativo, mostra quando motorista consulta
    NORMAL,    // Mostra no final do turno
    HIGH       // Mostra no dashboard (mas sem som/vibração)
}

/**
 * Comparação real entre turnos para mostrar ao motorista.
 * Exemplo: "Ontem 14h → R$180. Anteontem 10h → R$175. Diferença: R$5 por 4h a mais."
 */
data class FatigueComparison(
    val longShift: ShiftSummary,
    val shortShift: ShiftSummary,
    val extraHours: Double,
    val extraEarnings: Double,
    val effectiveHourlyRate: Double,  // R$/h das horas extras
    val conclusion: String
)

data class ShiftSummary(
    val date: String,           // "Ontem", "Seg 16/06", etc.
    val durationHours: Double,
    val totalEarned: Double,
    val ridesCount: Int,
    val valuePerHour: Double,
    val bestHourRange: String   // "18h-21h" (horário mais produtivo)
)

/**
 * Motor de insights de fadiga baseado em dados reais.
 *
 * Filosofia: NÃO ser intrusivo. O motorista decide se quer ver.
 * A IA mostra FATOS com números reais, não opiniões.
 *
 * Exemplo de insight:
 * "Na terça você fez R$180 em 14h (R$12.86/h).
 *  Na quarta fez R$175 em 10h (R$17.50/h).
 *  As últimas 4h da terça renderam apenas R$5 (R$1.25/h).
 *  Se tivesse parado às 10h, teria descansado mais e ganho quase o mesmo."
 */
class FatigueInsightEngine(private val context: Context) {

    companion object {
        private const val TAG = "FatigueInsightEngine"
        private const val MIN_SHIFTS_FOR_ANALYSIS = 5  // Mínimo de turnos para gerar insights
        private const val DIMINISHING_RETURNS_THRESHOLD = 0.5  // Se R$/h cai para 50% da média, é ponto de retorno
    }

    /**
     * Gera insights baseados no histórico de turnos.
     * Retorna lista de insights ordenados por prioridade.
     */
    suspend fun generateInsights(): List<FatigueInsight> = withContext(Dispatchers.IO) {
        val shifts = loadShiftHistory()
        if (shifts.size < MIN_SHIFTS_FOR_ANALYSIS) {
            return@withContext listOf(
                FatigueInsight(
                    type = InsightType.EFFICIENCY_COMPARISON,
                    title = "Coletando dados...",
                    message = "Preciso de pelo menos $MIN_SHIFTS_FOR_ANALYSIS turnos para gerar análises. " +
                              "Você tem ${shifts.size}/${MIN_SHIFTS_FOR_ANALYSIS}.",
                    priority = InsightPriority.LOW
                )
            )
        }

        val insights = mutableListOf<FatigueInsight>()

        // 1. Comparação de eficiência: turnos longos vs curtos
        generateEfficiencyComparison(shifts)?.let { insights.add(it) }

        // 2. Horários ótimos
        generateOptimalHoursInsight(shifts)?.let { insights.add(it) }

        // 3. Ponto de retorno decrescente
        generateDiminishingReturnsInsight(shifts)?.let { insights.add(it) }

        // 4. Benefício de descanso
        generateRestBenefitInsight(shifts)?.let { insights.add(it) }

        // 5. Padrão semanal
        generateWeeklyPatternInsight(shifts)?.let { insights.add(it) }

        return@withContext insights.sortedByDescending { it.priority.ordinal }
    }

    /**
     * Gera insight de comparação direta entre um turno longo e um curto.
     * Mostra em números reais que as horas extras não compensaram.
     */
    private fun generateEfficiencyComparison(shifts: List<ShiftData>): FatigueInsight? {
        // Separar turnos longos (>10h) e curtos (<8h)
        val longShifts = shifts.filter { it.durationHours >= 10.0 }
        val shortShifts = shifts.filter { it.durationHours in 5.0..8.0 }

        if (longShifts.isEmpty() || shortShifts.isEmpty()) return null

        val avgLong = longShifts.map { it.totalEarned }.average()
        val avgShort = shortShifts.map { it.totalEarned }.average()
        val avgLongHours = longShifts.map { it.durationHours }.average()
        val avgShortHours = shortShifts.map { it.durationHours }.average()

        val extraHours = avgLongHours - avgShortHours
        val extraEarnings = avgLong - avgShort
        val effectiveRate = if (extraHours > 0) extraEarnings / extraHours else 0.0

        val avgHourlyShort = if (avgShortHours > 0) avgShort / avgShortHours else 0.0

        // Só gera insight se as horas extras renderam menos que 60% da média
        if (effectiveRate >= avgHourlyShort * 0.6) return null

        val comparison = FatigueComparison(
            longShift = ShiftSummary(
                date = "Turnos longos (média)",
                durationHours = avgLongHours,
                totalEarned = avgLong,
                ridesCount = longShifts.map { it.ridesCount }.average().toInt(),
                valuePerHour = if (avgLongHours > 0) avgLong / avgLongHours else 0.0,
                bestHourRange = ""
            ),
            shortShift = ShiftSummary(
                date = "Turnos curtos (média)",
                durationHours = avgShortHours,
                totalEarned = avgShort,
                ridesCount = shortShifts.map { it.ridesCount }.average().toInt(),
                valuePerHour = avgHourlyShort,
                bestHourRange = ""
            ),
            extraHours = extraHours,
            extraEarnings = extraEarnings,
            effectiveHourlyRate = effectiveRate,
            conclusion = buildString {
                append("As ${String.format("%.1f", extraHours)}h extras renderam apenas ")
                append("R$ ${String.format("%.2f", extraEarnings)} ")
                append("(R$ ${String.format("%.2f", effectiveRate)}/h). ")
                append("Nos turnos curtos você ganha R$ ${String.format("%.2f", avgHourlyShort)}/h. ")
                append("Descansando mais, você ganha quase o mesmo com menos desgaste.")
            }
        )

        return FatigueInsight(
            type = InsightType.EFFICIENCY_COMPARISON,
            title = "Horas extras compensam?",
            message = buildString {
                append("Seus turnos de ${String.format("%.0f", avgLongHours)}h rendem R$ ${String.format("%.0f", avgLong)}. ")
                append("Seus turnos de ${String.format("%.0f", avgShortHours)}h rendem R$ ${String.format("%.0f", avgShort)}. ")
                append("Diferença: R$ ${String.format("%.0f", extraEarnings)} por ${String.format("%.1f", extraHours)}h a mais.")
            },
            dataComparison = comparison,
            priority = InsightPriority.NORMAL
        )
    }

    /**
     * Identifica os horários mais produtivos do motorista.
     */
    private fun generateOptimalHoursInsight(shifts: List<ShiftData>): FatigueInsight? {
        // Agrupar ganhos por faixa horária
        val hourlyEarnings = mutableMapOf<Int, MutableList<Double>>()
        shifts.forEach { shift ->
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = shift.startTimeMs }
            val startHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
            val endHour = startHour + shift.durationHours.toInt()
            val perHour = shift.totalEarned / shift.durationHours.coerceAtLeast(1.0)
            for (h in startHour until endHour.coerceAtMost(24)) {
                hourlyEarnings.getOrPut(h) { mutableListOf() }.add(perHour)
            }
        }

        if (hourlyEarnings.size < 6) return null

        // Encontrar as 3 melhores horas
        val bestHours = hourlyEarnings
            .mapValues { it.value.average() }
            .entries
            .sortedByDescending { it.value }
            .take(3)

        val worstHours = hourlyEarnings
            .mapValues { it.value.average() }
            .entries
            .sortedBy { it.value }
            .take(3)

        val bestRange = bestHours.map { "${it.key}h" }.joinToString(", ")
        val bestAvg = bestHours.map { it.value }.average()
        val worstRange = worstHours.map { "${it.key}h" }.joinToString(", ")
        val worstAvg = worstHours.map { it.value }.average()

        return FatigueInsight(
            type = InsightType.OPTIMAL_HOURS,
            title = "Seus horários de ouro",
            message = buildString {
                append("Melhores horários: $bestRange (R$ ${String.format("%.2f", bestAvg)}/h). ")
                append("Piores horários: $worstRange (R$ ${String.format("%.2f", worstAvg)}/h). ")
                append("Focando nos melhores horários, você pode ganhar o mesmo em menos tempo.")
            },
            priority = InsightPriority.NORMAL
        )
    }

    /**
     * Identifica o ponto exato onde o ganho/hora começa a cair significativamente.
     * Mostra: "Após Xh de turno, seu R$/h cai de Y para Z."
     */
    private fun generateDiminishingReturnsInsight(shifts: List<ShiftData>): FatigueInsight? {
        val longShifts = shifts.filter { it.durationHours >= 8.0 && it.hourlyBreakdown.isNotEmpty() }
        if (longShifts.size < 3) return null

        // Calcular R$/h médio por hora de turno (1ª hora, 2ª hora, etc.)
        val hourlyRates = mutableMapOf<Int, MutableList<Double>>()
        longShifts.forEach { shift ->
            shift.hourlyBreakdown.forEachIndexed { index, rate ->
                hourlyRates.getOrPut(index) { mutableListOf() }.add(rate)
            }
        }

        val avgByHour = hourlyRates.mapValues { it.value.average() }.toSortedMap()
        if (avgByHour.size < 6) return null

        // Encontrar ponto de queda: onde R$/h cai para menos de 50% do pico
        val peakRate = avgByHour.values.max()
        val dropPoint = avgByHour.entries.firstOrNull { it.value < peakRate * DIMINISHING_RETURNS_THRESHOLD }

        if (dropPoint == null) return null

        return FatigueInsight(
            type = InsightType.DIMINISHING_RETURNS,
            title = "Ponto de retorno decrescente",
            message = buildString {
                append("Após ${dropPoint.key + 1}h de turno, seu ganho/hora cai de ")
                append("R$ ${String.format("%.2f", peakRate)} para R$ ${String.format("%.2f", dropPoint.value)}. ")
                append("Isso significa que a partir desse ponto, cada hora extra rende ")
                append("${String.format("%.0f", (dropPoint.value / peakRate) * 100)}% do que você ganha no início.")
            },
            priority = InsightPriority.HIGH
        )
    }

    /**
     * Mostra o benefício concreto de descansar: turnos após descanso rendem mais.
     */
    private fun generateRestBenefitInsight(shifts: List<ShiftData>): FatigueInsight? {
        if (shifts.size < 7) return null

        // Calcular intervalo entre turnos e correlacionar com produtividade
        val shiftsWithRest = mutableListOf<Pair<Double, Double>>() // (horas de descanso, R$/h do turno seguinte)
        for (i in 1 until shifts.size) {
            val restHours = (shifts[i].startTimeMs - shifts[i - 1].endTimeMs) / 3_600_000.0
            if (restHours in 4.0..48.0) { // Filtrar intervalos razoáveis
                val productivity = shifts[i].totalEarned / shifts[i].durationHours.coerceAtLeast(1.0)
                shiftsWithRest.add(Pair(restHours, productivity))
            }
        }

        if (shiftsWithRest.size < 5) return null

        // Separar: descansou pouco (<8h) vs descansou bem (>10h)
        val littleRest = shiftsWithRest.filter { it.first < 8.0 }
        val goodRest = shiftsWithRest.filter { it.first >= 10.0 }

        if (littleRest.isEmpty() || goodRest.isEmpty()) return null

        val avgLittleRest = littleRest.map { it.second }.average()
        val avgGoodRest = goodRest.map { it.second }.average()

        if (avgGoodRest <= avgLittleRest) return null // Sem correlação

        val improvement = ((avgGoodRest - avgLittleRest) / avgLittleRest) * 100.0

        return FatigueInsight(
            type = InsightType.REST_BENEFIT,
            title = "Descanso = mais dinheiro",
            message = buildString {
                append("Quando você descansa 10h+, seu R$/h sobe ${String.format("%.0f", improvement)}%. ")
                append("Com pouco descanso (<8h): R$ ${String.format("%.2f", avgLittleRest)}/h. ")
                append("Com bom descanso (10h+): R$ ${String.format("%.2f", avgGoodRest)}/h. ")
                append("Descansar mais = ganhar mais por hora trabalhada.")
            },
            priority = InsightPriority.HIGH
        )
    }

    /**
     * Identifica padrões semanais (ex: segunda rende mais que sexta à noite).
     */
    private fun generateWeeklyPatternInsight(shifts: List<ShiftData>): FatigueInsight? {
        if (shifts.size < 14) return null // Precisa de pelo menos 2 semanas

        // Agrupar por dia da semana (0=Dom, 1=Seg, ..., 6=Sáb)
        val byDayOfWeek = shifts.groupBy { it.dayOfWeek }
        if (byDayOfWeek.size < 5) return null

        val avgByDay = byDayOfWeek.mapValues { entry ->
            entry.value.map { it.totalEarned / it.durationHours.coerceAtLeast(1.0) }.average()
        }

        val bestDay = avgByDay.maxByOrNull { it.value } ?: return null
        val worstDay = avgByDay.minByOrNull { it.value } ?: return null

        val dayNames = mapOf(
            1 to "Domingo", 2 to "Segunda", 3 to "Terça",
            4 to "Quarta", 5 to "Quinta", 6 to "Sexta", 7 to "Sábado"
        )

        return FatigueInsight(
            type = InsightType.WEEKLY_PATTERN,
            title = "Padrão semanal",
            message = buildString {
                append("Melhor dia: ${dayNames[bestDay.key] ?: "?"} (R$ ${String.format("%.2f", bestDay.value)}/h). ")
                append("Pior dia: ${dayNames[worstDay.key] ?: "?"} (R$ ${String.format("%.2f", worstDay.value)}/h). ")
                append("Considere descansar no ${dayNames[worstDay.key]} e focar no ${dayNames[bestDay.key]}.")
            },
            priority = InsightPriority.LOW
        )
    }

    // =========================================================================
    // DADOS
    // =========================================================================

    /**
     * Dados internos de um turno para análise.
     */
    private data class ShiftData(
        val startTimeMs: Long,
        val endTimeMs: Long,
        val durationHours: Double,
        val totalEarned: Double,
        val ridesCount: Int,
        val dayOfWeek: Int,
        val hourlyBreakdown: List<Double> = emptyList() // R$/h por hora do turno
    )

    /**
     * Carrega histórico de turnos do banco de dados.
     */
    private suspend fun loadShiftHistory(): List<ShiftData> {
        return try {
            val db = FinanceDatabase.getInstance(context)
            val cursor = db.openHelper.readableDatabase.query(
                "SELECT startTime, endTime, durationMinutes, totalEarned, ridesAccepted " +
                "FROM shift_history WHERE durationMinutes > 60 ORDER BY startTime DESC LIMIT 100"
            )
            val shifts = mutableListOf<ShiftData>()
            while (cursor.moveToNext()) {
                val startTime = cursor.getLong(0)
                val endTime = cursor.getLong(1)
                val durationMin = cursor.getInt(2)
                val earned = cursor.getDouble(3)
                val rides = cursor.getInt(4)

                val calendar = java.util.Calendar.getInstance().apply { timeInMillis = startTime }
                val dayOfWeek = calendar.get(java.util.Calendar.DAY_OF_WEEK)

                shifts.add(ShiftData(
                    startTimeMs = startTime,
                    endTimeMs = endTime,
                    durationHours = durationMin / 60.0,
                    totalEarned = earned,
                    ridesCount = rides,
                    dayOfWeek = dayOfWeek,
                    hourlyBreakdown = getRealHourlyBreakdown(startTime, endTime, durationMin / 60.0)
                ))
            }
            cursor.close()
            shifts
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Calcula a distribuição real de ganhos por hora do turno usando RideHistoryDao.
     */
    private suspend fun getRealHourlyBreakdown(startTime: Long, endTime: Long, durationHours: Double): List<Double> {
        if (durationHours < 1.0) return listOf(0.0)
        val hours = durationHours.toInt()
        val rides = try {
            com.ngbautoroad.data.db.AppDatabase.getInstance(context).rideHistoryDao().getRidesByPeriodSync(startTime, endTime).filter { it.status == "COMPLETED" }
        } catch (e: Exception) {
            emptyList()
        }
        if (rides.isEmpty()) return List(hours) { 0.0 }

        val breakdown = MutableList(hours) { 0.0 }
        rides.forEach { ride ->
            val hourIndex = ((ride.timestamp - startTime) / 3600000L).toInt().coerceIn(0, hours - 1)
            breakdown[hourIndex] += ride.rideValue
        }
        return breakdown
    }

    /**
     * Gera um resumo rápido para o dashboard (não intrusivo).
     * Retorna null se não há insight relevante.
     */
    suspend fun getQuickInsight(): FatigueInsight? = withContext(Dispatchers.IO) {
        val insights = generateInsights()
        return@withContext insights.firstOrNull { it.priority == InsightPriority.HIGH }
            ?: insights.firstOrNull { it.priority == InsightPriority.NORMAL }
    }
}
