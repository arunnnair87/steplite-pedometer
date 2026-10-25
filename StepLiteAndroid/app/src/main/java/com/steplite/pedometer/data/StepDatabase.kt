package com.steplite.pedometer.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Stores 1 row per calendar day.
 * 10 years of data = 3,650 rows (~140 KB total SQLite footprint).
 * Indexed by year and yearMonth for instant multi-year aggregation.
 */
@Entity(
    tableName = "daily_steps",
    indices = [
        Index(value = ["year"]),
        Index(value = ["yearMonth"])
    ]
)
data class DailyStepEntity(
    @PrimaryKey val dateIso: String, // e.g., "2026-10-23"
    val epochDay: Long,              // LocalDate.toEpochDay() for fast range queries
    val year: Int,                   // e.g., 2026
    val yearMonth: String,           // e.g., "2026-10"
    val steps: Int,
    val goal: Int,
    val activeSeconds: Int,
    val distanceMeters: Float,
    val caloriesKcal: Int
)

/** Projection for Monthly and Yearly aggregated bar charts */
data class PeriodSummary(
    val label: String,
    val totalSteps: Long,
    val avgSteps: Int,
    val totalDistanceMeters: Float,
    val totalCalories: Long,
    val daysGoalMet: Int
)

@Dao
interface StepDao {

    @Query("SELECT * FROM daily_steps WHERE dateIso = :dateIso LIMIT 1")
    fun observeDay(dateIso: String): Flow<DailyStepEntity?>

    @Query("SELECT * FROM daily_steps WHERE dateIso = :dateIso LIMIT 1")
    suspend fun getDayOnce(dateIso: String): DailyStepEntity?

    /** Last N days (used for 7-Day Week & 30-Day Month views) */
    @Query("SELECT * FROM daily_steps WHERE epochDay >= :minEpochDay ORDER BY epochDay ASC")
    fun observeRecentDays(minEpochDay: Long): Flow<List<DailyStepEntity>>

    /** 12-Month aggregation for a specific Year (e.g. "2026-01" .. "2026-12") */
    @Query(
        """
        SELECT yearMonth AS label,
               SUM(steps) AS totalSteps,
               CAST(AVG(steps) AS INTEGER) AS avgSteps,
               SUM(distanceMeters) AS totalDistanceMeters,
               SUM(caloriesKcal) AS totalCalories,
               SUM(CASE WHEN steps >= goal THEN 1 ELSE 0 END) AS daysGoalMet
        FROM daily_steps
        WHERE year = :year
        GROUP BY yearMonth
        ORDER BY yearMonth ASC
        """
    )
    fun observeMonthlyTotalsForYear(year: Int): Flow<List<PeriodSummary>>

    /** Multi-Year All-Time aggregation (e.g. 2022, 2023, 2024, 2025, 2026...) */
    @Query(
        """
        SELECT CAST(year AS TEXT) AS label,
               SUM(steps) AS totalSteps,
               CAST(AVG(steps) AS INTEGER) AS avgSteps,
               SUM(distanceMeters) AS totalDistanceMeters,
               SUM(caloriesKcal) AS totalCalories,
               SUM(CASE WHEN steps >= goal THEN 1 ELSE 0 END) AS daysGoalMet
        FROM daily_steps
        GROUP BY year
        ORDER BY year ASC
        """
    )
    fun observeYearlyTotals(): Flow<List<PeriodSummary>>

    /** Export entire multi-year history to CSV */
    @Query("SELECT * FROM daily_steps ORDER BY epochDay ASC")
    suspend fun getAllHistoryForCsv(): List<DailyStepEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDay(entity: DailyStepEntity)
}

@Database(entities = [DailyStepEntity::class], version = 1, exportSchema = false)
abstract class StepDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao

    companion object {
        @Volatile
        private var INSTANCE: StepDatabase? = null

        fun getInstance(context: Context): StepDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    StepDatabase::class.java,
                    "steplite_multiyear_history.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
