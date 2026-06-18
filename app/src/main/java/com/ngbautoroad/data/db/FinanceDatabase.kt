package com.ngbautoroad.data.db

import android.content.Context
import androidx.room.*
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
    val fuelType: String? = null
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
    val isAutoImported: Boolean = false    // Se foi importado automaticamente
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

// === DAO ===

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

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

    @Query("SELECT * FROM expenses WHERE isRecurring = 1")
    fun getRecurringExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amount) FROM expenses WHERE isRecurring = 1")
    fun getTotalRecurring(): Flow<Double?>
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

    @Query("SELECT SUM(distance) FROM earnings WHERE date >= :startDate AND date <= :endDate")
    fun getTotalDistance(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT SUM(duration) FROM earnings WHERE date >= :startDate AND date <= :endDate")
    fun getTotalDuration(startDate: Long, endDate: Long): Flow<Int?>

    @Query("SELECT SUM(ridesCount) FROM earnings WHERE date >= :startDate AND date <= :endDate")
    fun getTotalRides(startDate: Long, endDate: Long): Flow<Int?>

    @Query("SELECT * FROM earnings WHERE id = :id")
    suspend fun getById(id: Long): EarningEntity?
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

// === DATABASE ===

@Database(
    entities = [
        ExpenseEntity::class,
        EarningEntity::class,
        ReminderEntity::class,
        VehicleConfigEntity::class,
        FinancialGoalEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun earningDao(): EarningDao
    abstract fun reminderDao(): ReminderDao
    abstract fun vehicleConfigDao(): VehicleConfigDao
    abstract fun financialGoalDao(): FinancialGoalDao

    companion object {
        @Volatile
        private var INSTANCE: FinanceDatabase? = null

        fun getInstance(context: Context): FinanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FinanceDatabase::class.java,
                    "ngb_finance_db"
                ).fallbackToDestructiveMigration()
                 .allowMainThreadQueries()
                 .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
