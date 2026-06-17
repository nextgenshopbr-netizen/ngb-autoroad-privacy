package com.ngbautoroad.data.db

import androidx.room.*

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
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface RideHistoryDao {
    @Query("SELECT * FROM ride_history ORDER BY timestamp DESC")
    suspend fun getAll(): List<RideHistoryEntity>

    @Query("SELECT * FROM ride_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<RideHistoryEntity>

    @Insert
    suspend fun insert(ride: RideHistoryEntity): Long

    @Delete
    suspend fun delete(ride: RideHistoryEntity)

    @Query("DELETE FROM ride_history")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM ride_history")
    suspend fun count(): Int

    @Query("SELECT AVG(score) FROM ride_history WHERE timestamp > :since")
    suspend fun averageScoreSince(since: Long): Double?

    @Query("SELECT * FROM ride_history WHERE platform = :platform ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByPlatform(platform: String, limit: Int = 50): List<RideHistoryEntity>
}

@Database(entities = [RideHistoryEntity::class], version = 1, exportSchema = false)
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
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
