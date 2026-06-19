package com.ngbautoroad.data.db

// ============================================================================
// ARQUIVO: RideHistoryEntity.kt
// LOCALIZAÇÃO: data/db/RideHistoryEntity.kt
// RESPONSABILIDADE: Entity e DAO para histórico de corridas avaliadas
// ENTITY:
//   - RideHistoryEntity: Corrida com score, breakdown, status, plataforma
// DAO:
//   - RideHistoryDao: CRUD + queries por período, plataforma, status
//   - getAllFlow: Flow reativo para HistoryTab
//   - countSinceFlow: Flow reativo para DashboardTab
// MIGRAÇÕES:
//   - MIGRATION_1_2: Adiciona scoreBreakdown (JSON)
// PROTEÇÕES:
//   - Sem allowMainThreadQueries
//   - Queries com Flow para reatividade
// ============================================================================

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Entidade de histórico de corridas.
 *
 * v4.0 — Melhorias:
 * - Migração real (sem fallbackToDestructiveMigration) (item 6.1)
 * - Removido allowMainThreadQueries (item 6.2)
 * - Campo scoreBreakdown para detalhes do score (item 5.2)
 * - Campo confidenceLevel para indicar qualidade dos dados (item 1.3)
 * - Campo criteriaUsed para saber quantos critérios foram usados (item 1.3)
 */
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
    val status: String = "PENDING", // v6.1.0: PENDING, ACCEPTED, COMPLETED, REFUSED, CANCELLED, EXPIRED, UNCERTAIN
    val timestamp: Long = System.currentTimeMillis(),
    // v4.0 novos campos
    val scoreBreakdown: String = "",   // JSON com detalhes de cada critério (item 5.2)
    val criteriaUsed: Int = 0,         // Quantos critérios foram usados no score (item 1.3)
    val totalCriteria: Int = 0,        // Total de critérios configurados (item 1.3)
    val hasViolations: Boolean = false // Se houve violações de threshold (item 5.2)
) {
    val valuePerKm: Double
        get() = if (dropoffDistance > 0) rideValue / dropoffDistance else 0.0

    /** Nível de confiança do score (0-100%) baseado em quantos critérios tinham dados */
    val confidencePercent: Int
        get() = if (totalCriteria > 0) (criteriaUsed * 100) / totalCriteria else 100
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

    @Query("SELECT * FROM ride_history WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getSinceFlow(since: Long): Flow<List<RideHistoryEntity>>

    @Query("SELECT * FROM ride_history WHERE timestamp >= :since AND status = :status ORDER BY timestamp DESC")
    suspend fun getSinceWithStatus(since: Long, status: String): List<RideHistoryEntity>

    @Query("SELECT * FROM ride_history WHERE timestamp >= :since AND status = :status ORDER BY timestamp DESC")
    fun getSinceWithStatusFlow(since: Long, status: String): Flow<List<RideHistoryEntity>>

    @Query("SELECT * FROM ride_history WHERE status = :status ORDER BY timestamp DESC")
    suspend fun getByStatus(status: String): List<RideHistoryEntity>

    @Query("SELECT * FROM ride_history WHERE status = :status ORDER BY timestamp DESC")
    fun getByStatusFlow(status: String): Flow<List<RideHistoryEntity>>

    @Query("SELECT * FROM ride_history WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getBetweenDates(start: Long, end: Long): List<RideHistoryEntity>

    @Query("SELECT * FROM ride_history WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    fun getBetweenDatesFlow(start: Long, end: Long): Flow<List<RideHistoryEntity>>

    // v4.3.0 — Para Projeção Inteligente
    @Query("SELECT * FROM ride_history WHERE timestamp BETWEEN :start AND :end ORDER BY timestamp DESC")
    suspend fun getRidesByPeriodSync(start: Long, end: Long): List<RideHistoryEntity>

    @Query("SELECT * FROM ride_history WHERE platform = :platform ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getByPlatform(platform: String, limit: Int = 50): List<RideHistoryEntity>

    @Query("SELECT * FROM ride_history WHERE platform = :platform ORDER BY timestamp DESC")
    fun getByPlatformFlow(platform: String): Flow<List<RideHistoryEntity>>

    // Busca por bairro (item 5.3)
    @Query("SELECT * FROM ride_history WHERE pickupNeighborhood LIKE '%' || :query || '%' OR dropoffNeighborhood LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchByNeighborhoodFlow(query: String): Flow<List<RideHistoryEntity>>

    // Dashboard queries
    @Query("SELECT COUNT(*) FROM ride_history WHERE timestamp >= :since")
    suspend fun countSince(since: Long): Int

    // Flow reativo para dashboard (item 5.1)
    @Query("SELECT COUNT(*) FROM ride_history WHERE timestamp >= :since")
    fun countSinceFlow(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM ride_history WHERE timestamp >= :since AND status = :status")
    suspend fun countSinceWithStatus(since: Long, status: String): Int

    @Query("SELECT AVG(score) FROM ride_history WHERE timestamp >= :since")
    suspend fun averageScoreSince(since: Long): Double?

    @Query("SELECT SUM(rideValue) FROM ride_history WHERE timestamp >= :since AND (status = 'COMPLETED' OR status = 'ACCEPTED')")
    suspend fun totalEarningsSince(since: Long): Double?

    @Query("SELECT MAX(rideValue) FROM ride_history WHERE timestamp >= :since AND (status = 'COMPLETED' OR status = 'ACCEPTED')")
    suspend fun bestRideSince(since: Long): Double?

    @Query("SELECT AVG(rideValue / CASE WHEN dropoffDistance > 0 THEN dropoffDistance ELSE 1 END) FROM ride_history WHERE timestamp >= :since AND (status = 'COMPLETED' OR status = 'ACCEPTED')")
    suspend fun averageValuePerKmSince(since: Long): Double?

    @Query("SELECT platform FROM ride_history WHERE timestamp >= :since GROUP BY platform ORDER BY COUNT(*) DESC LIMIT 1")
    suspend fun topPlatformSince(since: Long): String?

    @Query("SELECT COUNT(*) FROM ride_history")
    suspend fun count(): Int

    // Estatísticas avançadas (item 5.5)
    @Query("SELECT AVG(score) FROM ride_history WHERE strftime('%H', datetime(timestamp/1000, 'unixepoch', 'localtime')) = :hour AND (status = 'COMPLETED' OR status = 'ACCEPTED')")
    suspend fun avgScoreByHour(hour: String): Double?

    @Query("SELECT SUM(rideValue) FROM ride_history WHERE strftime('%w', datetime(timestamp/1000, 'unixepoch', 'localtime')) = :dayOfWeek AND (status = 'COMPLETED' OR status = 'ACCEPTED')")
    suspend fun totalEarningsByDayOfWeek(dayOfWeek: String): Double?

    @Query("SELECT dropoffNeighborhood, COUNT(*) as cnt, AVG(rideValue) as avgVal FROM ride_history WHERE (status = 'COMPLETED' OR status = 'ACCEPTED') AND dropoffNeighborhood != '' GROUP BY dropoffNeighborhood ORDER BY avgVal DESC LIMIT 10")
    suspend fun topDropoffNeighborhoods(): List<NeighborhoodStats>

    @Insert
    suspend fun insert(ride: RideHistoryEntity): Long

    @Update
    suspend fun update(ride: RideHistoryEntity)

    @Delete
    suspend fun delete(ride: RideHistoryEntity)

    @Query("DELETE FROM ride_history")
    suspend fun deleteAll()
}

/** Resultado de query de bairros mais lucrativos */
data class NeighborhoodStats(
    val dropoffNeighborhood: String,
    val cnt: Int,
    val avgVal: Double
)

@Database(entities = [RideHistoryEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rideHistoryDao(): RideHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migração v2 → v3: adicionar novos campos (item 6.1)
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE ride_history ADD COLUMN scoreBreakdown TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE ride_history ADD COLUMN criteriaUsed INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE ride_history ADD COLUMN totalCriteria INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE ride_history ADD COLUMN hasViolations INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "ngb_autoroad_db"
                )
                .addMigrations(MIGRATION_2_3)
                // Fallback apenas para casos extremos (não destrói dados em migração normal)
                .fallbackToDestructiveMigrationFrom(1)
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
