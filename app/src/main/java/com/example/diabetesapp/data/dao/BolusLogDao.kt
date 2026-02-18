package com.example.diabetesapp.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
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
}

