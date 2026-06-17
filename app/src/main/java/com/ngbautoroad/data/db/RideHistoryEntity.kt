package com.ngbautoroad.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "ride_history")
data class RideHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val platform: String = "",
    val rideValue: Double = 0.0,
    val rideDuration: Double = 0.0,
    val pickupDistance: Double = 0.0,
    val dropoffDistance: Double = 0.0,
    val passengerRating: Double = 0.0,
    val intermediateStops: Int = 0,
    val pickupNeighborhood: String = "",
    val dropoffNeighborhood: String = "",
    val score: Double = 0.0,
    val status: String = "REFUSED", // ACCEPTED, REFUSED, CANCELLED, EXPIRED
    val timestamp: Long = System.currentTimeMillis()
) {
    val valuePerKm: Double
        get() = if (dropoffDistance > 0) rideValue / dropoffDistance else 0.0
}

@Dao
interface RideHistoryDao {
    @Query("SELECT * FROM ride_history ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<RideHistoryEntity>>

    @Query("SELECT * FROM ride_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<RideHistoryEntity>

    @Query("SELECT * FROM ride_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<RideHistoryEntity>

    @Query("SELECT * FROM ride_history WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getSince(since: Long): List<RideHistoryEntity>

    @Query("SELECT * FROM ride_history WHERE timestamp >= :since AND status = :status ORDER BY timestamp DESC")
    suspend fun getSinceWithStatus(since: Long, status: String): List<RideHistoryEntity>

    @Query("SELECT * FROM ride_history WHERE status = :status ORDER BY timestamp DESC")
    suspend fun getByStatus(status: String): List<RideHistoryEntity>

    @Query("SELECT * FROM ride_history WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getBetweenDates(start: Long, end: Long): List<RideHistoryEntity>

    @Query("SELECT * FROM ride_history WHERE platform = :platform ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByPlatform(platform: String, limit: Int = 50): List<RideHistoryEntity>

    // Dashboard queries
    @Query("SELECT COUNT(*) FROM ride_history WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM ride_history WHERE timestamp >= :since AND status = :status")
    suspend fun countSinceWithStatus(since: Long, status: String): Int

    @Query("SELECT AVG(score) FROM ride_history WHERE timestamp >= :since")
    suspend fun averageScoreSince(since: Long): Double?

    @Query("SELECT SUM(rideValue) FROM ride_history WHERE timestamp >= :since AND status = 'ACCEPTED'")
    suspend fun totalEarningsSince(since: Long): Double?

    @Query("SELECT MAX(rideValue) FROM ride_history WHERE timestamp >= :since AND status = 'ACCEPTED'")
    suspend fun bestRideSince(since: Long): Double?

    @Query("SELECT AVG(rideValue / CASE WHEN dropoffDistance > 0 THEN dropoffDistance ELSE 1 END) FROM ride_history WHERE timestamp >= :since AND status = 'ACCEPTED'")
    suspend fun averageValuePerKmSince(since: Long): Double?

    @Query("SELECT platform FROM ride_history WHERE timestamp >= :since GROUP BY platform ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun topPlatformSince(since: Long): String?

    @Query("SELECT COUNT(*) FROM ride_history")
    suspend fun count(): Int

    @Insert
    suspend fun insert(ride: RideHistoryEntity): Long

    @Update
    suspend fun update(ride: RideHistoryEntity)

    @Delete
    suspend fun delete(ride: RideHistoryEntity)

    @Query("DELETE FROM ride_history")
    suspend fun deleteAll()
}

@Database(entities = [RideHistoryEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rideHistoryDao(): RideHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ngb_autoroad_db"
                )
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
