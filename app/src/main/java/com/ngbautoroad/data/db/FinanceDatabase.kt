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
    val ridesCount: Int = 1,
    val date: Long = System.currentTimeMillis(),
    val description: String = ""
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

// === DATABASE ===

@Database(
    entities = [ExpenseEntity::class, EarningEntity::class, ReminderEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun earningDao(): EarningDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        @Volatile
        private var INSTANCE: FinanceDatabase? = null

        fun getInstance(context: Context): FinanceDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FinanceDatabase::class.java,
                    "ngb_finance_db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
