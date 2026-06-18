package com.ngbautoroad.data.db

// ============================================================================
// ARQUIVO: FinanceDatabase.kt
// LOCALIZAÇÃO: data/db/FinanceDatabase.kt
// RESPONSABILIDADE: Banco Room para dados financeiros (gastos, ganhos, metas, veículo)
// ENTITIES:
//   - ExpenseEntity: Gastos com recorrência
//   - EarningEntity: Ganhos (manuais ou auto-import)
//   - ReminderEntity: Lembretes financeiros
//   - VehicleConfigEntity: Configuração do veículo
//   - FinancialGoalEntity: Metas financeiras
// DAOS:
//   - ExpenseDao, EarningDao, ReminderDao, VehicleConfigDao, FinancialGoalDao
// MIGRAÇÕES:
//   - MIGRATION_1_2: Adiciona campos de recorrência
//   - MIGRATION_2_3: Adiciona rideHistoryId no EarningEntity
// PROTEÇÕES:
//   - Sem allowMainThreadQueries (todas queries são suspend/Flow)
//   - fallbackToDestructiveMigration removido (usa migrações reais)
// ============================================================================

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

// === ENTITIES ===

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String = "",
    val amount: Double = 0.0,
    val description: String = "",
    val date: Long = System.currentTimeMillis(),
    val isRecurring: Boolean = false,
    val recurringDay: Int = 1,
    val recurringDays: String = "",        // Dias da semana: "1,2,3,4,5" (seg-sex)
    val recurringDuration: Int = 0,        // Por quantos dias se repete (0 = indefinido)
    val recurringEndDate: Long = 0,        // Data final da recorrência (0 = sem fim)
    val liters: Double? = null,
    val pricePerLiter: Double? = null,
    val odometer: Int? = null,
    val fuelType: String? = null,
    // v3.0 — item 3.1: controle de instâncias geradas de recorrência
    val parentExpenseId: Long = 0,         // ID do gasto recorrente pai (0 = original)
    val isGenerated: Boolean = false       // Se foi gerado automaticamente pela recorrência
)

@Entity(tableName = "earnings")
data class EarningEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String = "",
    val amount: Double = 0.0,
    val tips: Double = 0.0,
    val bonus: Double = 0.0,
    val distance: Double = 0.0,
    val duration: Int = 0,
    val ridesCount: Int = 0,
    val date: Long = System.currentTimeMillis(),
    val description: String = "",
    val period: String = "DIA",            // DIA, SEMANA, MES
    val isAutoImported: Boolean = false,   // Se foi importado automaticamente (item 3.2)
    // v3.0 — item 3.3: DRE / relatório
    val rideHistoryId: Long = 0            // ID do histórico de corrida associado (se auto-importado)
)

@Entity(tableName = "maintenance_reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val category: String = "",
    val nextDate: Long = 0,
    val nextOdometer: Int = 0,
    val intervalDays: Int = 0,
    val intervalKm: Int = 0,
    val isActive: Boolean = true
)

@Entity(tableName = "vehicle_config")
data class VehicleConfigEntity(
    @PrimaryKey val id: Int = 1, // Sempre 1, só um veículo
    val vehicleType: String = "COMBUSTION", // COMBUSTION, HYBRID, ELECTRIC
    val fuelType: String = "GASOLINE",      // GASOLINE, ETHANOL, DIESEL, FLEX, ELECTRIC
    val brand: String = "",
    val model: String = "",
    val year: Int = 0,
    val plate: String = "",
    val averageConsumption: Double = 0.0,   // km/L ou km/kWh
    val fuelPrice: Double = 0.0,            // R$/L ou R$/kWh
    val costPerKm: Double = 0.0,            // Calculado
    val monthlyFixedCosts: Double = 0.0,    // Parcela + seguro + IPVA mensal
    val isOwned: Boolean = true,            // Próprio ou alugado
    val rentalCost: Double = 0.0            // Custo mensal do aluguel
)

@Entity(tableName = "financial_goals")
data class FinancialGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String = "",
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val period: String = "MES",            // DIA, SEMANA, MES
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

// === DAOs ===

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Insert
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("SELECT * FROM expenses ORDER BY date DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getExpensesByPeriod(startDate: Long, endDate: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE category = :category ORDER BY date DESC")
    fun getExpensesByCategory(category: String): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amount) FROM expenses WHERE date >= :startDate AND date <= :endDate")
    fun getTotalExpenses(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE category = :category AND date >= :startDate AND date <= :endDate")
    fun getTotalByCategory(category: String, startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT * FROM expenses WHERE isRecurring = 1 AND isGenerated = 0")
    fun getRecurringExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE isRecurring = 1 AND isGenerated = 0")
    suspend fun getRecurringExpensesSync(): List<ExpenseEntity>

    @Query("SELECT SUM(amount) FROM expenses WHERE isRecurring = 1")
    fun getTotalRecurring(): Flow<Double?>

    // Para verificar se já existe instância gerada para um dia específico (evitar duplicatas)
    @Query("SELECT COUNT(*) FROM expenses WHERE parentExpenseId = :parentId AND date >= :dayStart AND date <= :dayEnd")
    suspend fun countGeneratedForDay(parentId: Long, dayStart: Long, dayEnd: Long): Int

    // Relatório DRE (item 3.3)
    @Query("SELECT category, SUM(amount) as total FROM expenses WHERE date >= :startDate AND date <= :endDate GROUP BY category ORDER BY total DESC")
    suspend fun getExpenseSummaryByCategory(startDate: Long, endDate: Long): List<CategorySummary>
}

@Dao
interface EarningDao {
    @Insert
    suspend fun insert(earning: EarningEntity): Long

    @Update
    suspend fun update(earning: EarningEntity)

    @Delete
    suspend fun delete(earning: EarningEntity)

    @Query("SELECT * FROM earnings ORDER BY date DESC")
    fun getAllEarnings(): Flow<List<EarningEntity>>

    @Query("SELECT * FROM earnings WHERE date >= :startDate AND date <= :endDate ORDER BY date DESC")
    fun getEarningsByPeriod(startDate: Long, endDate: Long): Flow<List<EarningEntity>>

    @Query("SELECT * FROM earnings WHERE platform = :platform ORDER BY date DESC")
    fun getEarningsByPlatform(platform: String): Flow<List<EarningEntity>>

    @Query("SELECT SUM(amount + tips + bonus) FROM earnings WHERE date >= :startDate AND date <= :endDate")
    fun getTotalEarnings(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT SUM(amount + tips + bonus) FROM earnings WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalEarningsSync(startDate: Long, endDate: Long): Double?

    @Query("SELECT SUM(distance) FROM earnings WHERE date >= :startDate AND date <= :endDate")
    fun getTotalDistance(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT SUM(distance) FROM earnings WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalDistanceSync(startDate: Long, endDate: Long): Double?

    @Query("SELECT SUM(duration) FROM earnings WHERE date >= :startDate AND date <= :endDate")
    fun getTotalDuration(startDate: Long, endDate: Long): Flow<Int?>

    @Query("SELECT SUM(duration) FROM earnings WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalDurationSync(startDate: Long, endDate: Long): Int?

    @Query("SELECT SUM(ridesCount) FROM earnings WHERE date >= :startDate AND date <= :endDate")
    fun getTotalRides(startDate: Long, endDate: Long): Flow<Int?>

    @Query("SELECT SUM(ridesCount) FROM earnings WHERE date >= :startDate AND date <= :endDate")
    suspend fun getTotalRidesSync(startDate: Long, endDate: Long): Int?

    @Query("SELECT * FROM earnings WHERE id = :id")
    suspend fun getById(id: Long): EarningEntity?

    // Relatório por plataforma (item 3.3)
    @Query("SELECT platform, SUM(amount + tips + bonus) as total, SUM(ridesCount) as rides, SUM(distance) as km FROM earnings WHERE date >= :startDate AND date <= :endDate GROUP BY platform ORDER BY total DESC")
    suspend fun getEarningsByPlatformSummary(startDate: Long, endDate: Long): List<PlatformSummary>

    // Verificar se corrida já foi importada (evitar duplicatas - item 2.2)
    @Query("SELECT COUNT(*) FROM earnings WHERE rideHistoryId = :rideId AND isAutoImported = 1")
    suspend fun countAutoImportedByRideId(rideId: Long): Int

    // Ganhos do dia para dashboard (item 3.4)
    @Query("SELECT SUM(amount + tips + bonus) FROM earnings WHERE date >= :dayStart AND date <= :dayEnd")
    suspend fun getTodayEarnings(dayStart: Long, dayEnd: Long): Double?
}

@Dao
interface ReminderDao {
    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("SELECT * FROM maintenance_reminders WHERE isActive = 1 ORDER BY nextDate ASC")
    fun getActiveReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM maintenance_reminders ORDER BY nextDate ASC")
    fun getAllReminders(): Flow<List<ReminderEntity>>
}

@Dao
interface VehicleConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(config: VehicleConfigEntity)

    @Query("SELECT * FROM vehicle_config WHERE id = 1")
    fun getConfig(): Flow<VehicleConfigEntity?>

    @Query("SELECT * FROM vehicle_config WHERE id = 1")
    suspend fun getConfigSync(): VehicleConfigEntity?
}

@Dao
interface FinancialGoalDao {
    @Insert
    suspend fun insert(goal: FinancialGoalEntity): Long

    @Update
    suspend fun update(goal: FinancialGoalEntity)

    @Delete
    suspend fun delete(goal: FinancialGoalEntity)

    @Query("SELECT * FROM financial_goals WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getActiveGoals(): Flow<List<FinancialGoalEntity>>

    @Query("SELECT * FROM financial_goals ORDER BY createdAt DESC")
    fun getAllGoals(): Flow<List<FinancialGoalEntity>>
}

// === Resultado de queries de relatório ===

data class CategorySummary(
    val category: String,
    val total: Double
)

data class PlatformSummary(
    val platform: String,
    val total: Double,
    val rides: Int,
    val km: Double
)

// === DATABASE ===

@Database(
    entities = [
        ExpenseEntity::class,
        EarningEntity::class,
        ReminderEntity::class,
        VehicleConfigEntity::class,
        FinancialGoalEntity::class,
        VehicleProfileEntity::class,
        IndividualExpenseEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun earningDao(): EarningDao
    abstract fun reminderDao(): ReminderDao
    abstract fun vehicleConfigDao(): VehicleConfigDao
    abstract fun financialGoalDao(): FinancialGoalDao
    abstract fun vehicleProfileDao(): VehicleProfileDao
    abstract fun individualExpenseDao(): IndividualExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: FinanceDatabase? = null

        // Migração v2 → v3: adicionar novos campos sem destruir dados (item 6.1)
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE expenses ADD COLUMN parentExpenseId INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE expenses ADD COLUMN isGenerated INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE earnings ADD COLUMN rideHistoryId INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migração v3 → v4: Adicionar tabelas vehicle_profiles e individual_expenses (v4.3.0)
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS vehicle_profiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        isActive INTEGER NOT NULL DEFAULT 0,
                        brand TEXT NOT NULL DEFAULT '',
                        model TEXT NOT NULL DEFAULT '',
                        year INTEGER NOT NULL DEFAULT 0,
                        plate TEXT NOT NULL DEFAULT '',
                        vehicleType TEXT NOT NULL DEFAULT 'COMBUSTION',
                        fuelType TEXT NOT NULL DEFAULT 'FLEX',
                        averageConsumption REAL NOT NULL DEFAULT 0.0,
                        fuelPrice REAL NOT NULL DEFAULT 0.0,
                        costPerKm REAL NOT NULL DEFAULT 0.0,
                        isOwned INTEGER NOT NULL DEFAULT 1,
                        rentalCost REAL NOT NULL DEFAULT 0.0,
                        purchaseValue REAL NOT NULL DEFAULT 0.0,
                        currentOdometer INTEGER NOT NULL DEFAULT 0,
                        tireLifeKm INTEGER NOT NULL DEFAULT 40000,
                        tireCost REAL NOT NULL DEFAULT 0.0,
                        brakepadLifeKm INTEGER NOT NULL DEFAULT 30000,
                        brakepadCost REAL NOT NULL DEFAULT 0.0,
                        oilChangeKm INTEGER NOT NULL DEFAULT 10000,
                        oilChangeCost REAL NOT NULL DEFAULT 0.0,
                        maintenanceIntervalKm INTEGER NOT NULL DEFAULT 20000,
                        maintenanceCost REAL NOT NULL DEFAULT 0.0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS individual_expenses (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        vehicleId INTEGER NOT NULL DEFAULT 0,
                        title TEXT NOT NULL DEFAULT '',
                        category TEXT NOT NULL DEFAULT '',
                        totalAmount REAL NOT NULL DEFAULT 0.0,
                        installments INTEGER NOT NULL DEFAULT 1,
                        installmentsPaid INTEGER NOT NULL DEFAULT 0,
                        monthlyAmount REAL NOT NULL DEFAULT 0.0,
                        startDate INTEGER NOT NULL DEFAULT 0,
                        dueDay INTEGER NOT NULL DEFAULT 1,
                        isIncludedInCalc INTEGER NOT NULL DEFAULT 1,
                        isRecurringAnnual INTEGER NOT NULL DEFAULT 0,
                        frequency TEXT NOT NULL DEFAULT 'MENSAL',
                        notes TEXT NOT NULL DEFAULT '',
                        isPaid INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        fun getInstance(context: Context): FinanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FinanceDatabase::class.java,
                    "ngb_finance_db"
                )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                // Fallback apenas para migração de versão 1 (primeira instalação antiga)
                .fallbackToDestructiveMigrationFrom(1)
                // Removido allowMainThreadQueries — todas as queries são suspend/Flow (item 6.2)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
