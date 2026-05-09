package com.example.diabetesapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.diabetesapp.data.models.DailyMetrics

@Dao
interface DailyMetricsDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(metrics: DailyMetrics)

    @Query("SELECT * FROM daily_metrics ORDER BY dateEpochDay DESC")
    suspend fun getAll(): List<DailyMetrics>

    @Query("SELECT * FROM daily_metrics WHERE dateEpochDay = :day")
    suspend fun getByDay(day: Long): DailyMetrics?

    @Query("SELECT * FROM daily_metrics WHERE dateEpochDay >= :from AND dateEpochDay <= :to ORDER BY dateEpochDay ASC")
    suspend fun getRange(from: Long, to: Long): List<DailyMetrics>

    @Query("SELECT COUNT(*) FROM daily_metrics")
    suspend fun count(): Int
}
