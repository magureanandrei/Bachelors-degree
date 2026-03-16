package com.example.diabetesapp.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.diabetesapp.data.models.BolusLog
import kotlinx.coroutines.flow.Flow

@Dao
interface BolusLogDao {

    @Insert
    suspend fun insert(bolusLog: BolusLog)

    @Query("SELECT * FROM bolus_log ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<BolusLog>>

    @Query("DELETE FROM bolus_log")
    suspend fun deleteAll()

    @Update
    suspend fun update(bolusLog: BolusLog)

    @Delete
    suspend fun delete(bolusLog: BolusLog)

    @Query("SELECT * FROM bolus_log WHERE bloodGlucose > 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestManualBgLog(): BolusLog?

    @Query("SELECT * FROM bolus_log WHERE ABS(timestamp - :timestamp) <= :windowMs ORDER BY ABS(timestamp - :timestamp) ASC")
    suspend fun getLogsNearTimestamp(timestamp: Long, windowMs: Long): List<BolusLog>

}

