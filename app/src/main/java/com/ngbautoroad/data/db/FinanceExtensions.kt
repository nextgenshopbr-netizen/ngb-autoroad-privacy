package com.ngbautoroad.data.db

// ============================================================================
// ARQUIVO: FinanceExtensions.kt
// LOCALIZAÇÃO: data/db/FinanceExtensions.kt
// RESPONSABILIDADE: Extensões financeiras v4.3.0
//   - VehicleProfileEntity: Múltiplos veículos com campo "ativo"
//   - IndividualExpenseEntity: Despesas individuais com rateio temporal
//   - ProjectionEngine: Algoritmo de projeção inteligente
//   - WhatIfSimulator: Simulação "E se?" com cenários
// ============================================================================

import androidx.room.*
import kotlinx.coroutines.flow.Flow

// ============================================================================
// ENTITY: VehicleProfileEntity — Multi-veículos com veículo ativo
// ============================================================================

@Entity(tableName = "vehicle_profiles")
data class VehicleProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isActive: Boolean = false,            // Apenas 1 veículo ativo por vez
    val brand: String = "",                   // Ex: Fiat, VW, Chevrolet
    val model: String = "",                   // Ex: Argo, Gol, Onix
    val year: Int = 0,
    val plate: String = "",
    val vehicleType: String = "COMBUSTION",   // COMBUSTION, HYBRID, ELECTRIC
    val fuelType: String = "FLEX",            // GASOLINE, ETHANOL, DIESEL, FLEX, ELECTRIC
    val averageConsumption: Double = 0.0,     // km/L ou km/kWh
    val fuelPrice: Double = 0.0,             // R$/L ou R$/kWh atual
    val costPerKm: Double = 0.0,             // Calculado: fuelPrice / averageConsumption
    val isOwned: Boolean = true,             // Próprio ou alugado
    val rentalCost: Double = 0.0,            // Custo mensal do aluguel (se não for próprio)
    val purchaseValue: Double = 0.0,         // Valor de compra (para depreciação)
    val currentOdometer: Int = 0,            // Odômetro atual
    // Dados de desgaste (para projeção)
    val tireLifeKm: Int = 40000,             // Vida útil dos pneus em km
    val tireCost: Double = 0.0,              // Custo de 4 pneus
    val brakepadLifeKm: Int = 30000,         // Vida útil das pastilhas em km
    val brakepadCost: Double = 0.0,          // Custo das pastilhas
    val oilChangeKm: Int = 10000,            // Intervalo de troca de óleo em km
    val oilChangeCost: Double = 0.0,         // Custo da troca de óleo
    val maintenanceIntervalKm: Int = 20000,  // Revisão geral a cada X km
    val maintenanceCost: Double = 0.0,       // Custo médio de revisão
    val createdAt: Long = System.currentTimeMillis()
)

// ============================================================================
// ENTITY: IndividualExpenseEntity — Despesas individuais com rateio temporal
// ============================================================================

@Entity(tableName = "individual_expenses")
data class IndividualExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long = 0,                 // Veículo associado (0 = geral)
    val title: String = "",                  // Ex: "IPVA 2026", "Parcela financiamento"
    val category: String = "",               // IPVA, SEGURO, PARCELA, MANUTENCAO, MULTA, OUTRO
    val totalAmount: Double = 0.0,           // Valor total da despesa
    val installments: Int = 1,               // Em quantas parcelas/meses dividir (rateio)
    val installmentsPaid: Int = 0,           // Quantas já foram pagas
    val monthlyAmount: Double = 0.0,         // totalAmount / installments (calculado)
    val startDate: Long = System.currentTimeMillis(), // Data de início do rateio
    val dueDay: Int = 1,                     // Dia do mês para vencimento
    val isIncludedInCalc: Boolean = true,    // Motorista decide se entra nos cálculos
    val isRecurringAnnual: Boolean = false,  // Se é despesa anual (IPVA, seguro)
    val frequency: String = "MENSAL",        // UNICA, MENSAL, ANUAL
    val notes: String = "",                  // Observações
    val isPaid: Boolean = false,             // Se já foi quitada completamente
    val createdAt: Long = System.currentTimeMillis()
)

// ============================================================================
// DAO: VehicleProfileDao
// ============================================================================

@Dao
interface VehicleProfileDao {
    @Insert
    suspend fun insert(vehicle: VehicleProfileEntity): Long

    @Update
    suspend fun update(vehicle: VehicleProfileEntity)

    @Delete
    suspend fun delete(vehicle: VehicleProfileEntity)

    @Query("SELECT * FROM vehicle_profiles ORDER BY isActive DESC, createdAt DESC")
    fun getAllVehicles(): Flow<List<VehicleProfileEntity>>

    @Query("SELECT * FROM vehicle_profiles WHERE isActive = 1 LIMIT 1")
    fun getActiveVehicle(): Flow<VehicleProfileEntity?>

    @Query("SELECT * FROM vehicle_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveVehicleSync(): VehicleProfileEntity?

    @Query("UPDATE vehicle_profiles SET isActive = 0")
    suspend fun deactivateAll()

    @Query("UPDATE vehicle_profiles SET isActive = 1 WHERE id = :vehicleId")
    suspend fun setActive(vehicleId: Long)

    @Transaction
    suspend fun switchActiveVehicle(vehicleId: Long) {
        deactivateAll()
        setActive(vehicleId)
    }
}

// ============================================================================
// DAO: IndividualExpenseDao
// ============================================================================

@Dao
interface IndividualExpenseDao {
    @Insert
    suspend fun insert(expense: IndividualExpenseEntity): Long

    @Update
    suspend fun update(expense: IndividualExpenseEntity)

    @Delete
    suspend fun delete(expense: IndividualExpenseEntity)

    @Query("SELECT * FROM individual_expenses ORDER BY createdAt DESC")
    fun getAllExpenses(): Flow<List<IndividualExpenseEntity>>

    @Query("SELECT * FROM individual_expenses WHERE isIncludedInCalc = 1 ORDER BY createdAt DESC")
    fun getIncludedExpenses(): Flow<List<IndividualExpenseEntity>>

    @Query("SELECT * FROM individual_expenses WHERE isIncludedInCalc = 1")
    suspend fun getIncludedExpensesSync(): List<IndividualExpenseEntity>

    @Query("SELECT * FROM individual_expenses WHERE vehicleId = :vehicleId ORDER BY createdAt DESC")
    fun getByVehicle(vehicleId: Long): Flow<List<IndividualExpenseEntity>>

    @Query("SELECT * FROM individual_expenses WHERE category = :category ORDER BY createdAt DESC")
    fun getByCategory(category: String): Flow<List<IndividualExpenseEntity>>

    @Query("SELECT * FROM individual_expenses WHERE isPaid = 0 AND isIncludedInCalc = 1")
    suspend fun getActiveUnpaidSync(): List<IndividualExpenseEntity>

    // Total mensal rateado (soma dos monthlyAmount de despesas ativas e incluídas)
    @Query("SELECT SUM(monthlyAmount) FROM individual_expenses WHERE isIncludedInCalc = 1 AND isPaid = 0")
    fun getTotalMonthlyRated(): Flow<Double?>

    @Query("SELECT SUM(monthlyAmount) FROM individual_expenses WHERE isIncludedInCalc = 1 AND isPaid = 0")
    suspend fun getTotalMonthlyRatedSync(): Double?
}

// ============================================================================
// DATA CLASSES: Resultado de projeção e simulação
// ============================================================================

/**
 * Resultado da projeção financeira inteligente
 */
data class FinancialProjection(
    val period: String,                      // DIA, SEMANA, MES, ANO
    val projectedEarnings: Double,           // Ganhos projetados
    val projectedFuelCost: Double,           // Custo de combustível projetado
    val projectedMaintenanceCost: Double,    // Custo de manutenção projetado (desgaste)
    val projectedFixedCosts: Double,         // Custos fixos (despesas individuais rateadas)
    val projectedTotalCosts: Double,         // Total de custos projetados
    val projectedGrossProfit: Double,        // Lucro bruto (ganhos - combustível)
    val projectedNetProfit: Double,          // Lucro líquido (ganhos - todos os custos)
    val projectedRealProfit: Double,         // Lucro real (líquido - depreciação)
    val projectedKm: Double,                 // Km projetados
    val projectedRides: Int,                 // Corridas projetadas
    val projectedHours: Double,              // Horas projetadas
    val avgEarningPerRide: Double,           // Média de ganho por corrida
    val avgEarningPerKm: Double,             // Média de ganho por km
    val avgEarningPerHour: Double,           // Média de ganho por hora
    val confidenceLevel: Double              // Nível de confiança da projeção (0-100%)
)

/**
 * Resultado da simulação "E se?" — cenários alternativos
 */
data class WhatIfResult(
    val scenarioName: String,                // "Todas Boas", "Todas Médias", "Todas Ruins", "Mescla"
    val totalRides: Int,                     // Total de corridas no cenário
    val totalEarnings: Double,               // Ganho total
    val totalKm: Double,                     // Km total rodado
    val totalHours: Double,                  // Horas total
    val fuelCost: Double,                    // Custo de combustível
    val maintenanceCost: Double,             // Custo de manutenção/desgaste
    val tireCost: Double,                    // Custo de desgaste de pneus
    val brakepadCost: Double,               // Custo de desgaste de pastilhas
    val oilChangeCost: Double,              // Custo de trocas de óleo
    val totalCosts: Double,                  // Total de custos
    val grossProfit: Double,                 // Lucro bruto
    val netProfit: Double,                   // Lucro líquido
    val realProfit: Double,                  // Lucro real (com depreciação)
    val differenceFromActual: Double,        // Diferença em R$ vs. o que realmente ganhou
    val differenceKm: Double,                // Km a mais/menos vs. real
    val differenceHours: Double,             // Horas a mais/menos vs. real
    val avgPerRide: Double,                  // Média por corrida
    val avgPerKm: Double,                    // Média por km
    val avgPerHour: Double                   // Média por hora
)

/**
 * Categorias de despesas individuais
 */
object ExpenseCategories {
    const val IPVA = "IPVA"
    const val SEGURO = "SEGURO"
    const val PARCELA = "PARCELA"
    const val MANUTENCAO = "MANUTENCAO"
    const val MULTA = "MULTA"
    const val LICENCIAMENTO = "LICENCIAMENTO"
    const val DOCUMENTACAO = "DOCUMENTACAO"
    const val ACESSORIO = "ACESSORIO"
    const val OUTRO = "OUTRO"

    val all = listOf(IPVA, SEGURO, PARCELA, MANUTENCAO, MULTA, LICENCIAMENTO, DOCUMENTACAO, ACESSORIO, OUTRO)
    val labels = mapOf(
        IPVA to "IPVA",
        SEGURO to "Seguro",
        PARCELA to "Parcela/Financiamento",
        MANUTENCAO to "Manutenção",
        MULTA to "Multa",
        LICENCIAMENTO to "Licenciamento",
        DOCUMENTACAO to "Documentação",
        ACESSORIO to "Acessório",
        OUTRO to "Outro"
    )
}
