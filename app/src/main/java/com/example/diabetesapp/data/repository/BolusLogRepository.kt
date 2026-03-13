package com.example.diabetesapp.data.repository

import com.example.diabetesapp.data.dao.BolusLogDao
import com.example.diabetesapp.data.models.BolusLog
import kotlinx.coroutines.flow.Flow

class BolusLogRepository(private val bolusLogDao: BolusLogDao) {

    // Room executes all queries on a separate thread, so we don't need to worry about blocking the UI
    val allLogs: Flow<List<BolusLog>> = bolusLogDao.getAllLogs()

    suspend fun insert(bolusLog: BolusLog) {
        bolusLogDao.insert(bolusLog)
    }

    suspend fun deleteAll() {
        bolusLogDao.deleteAll()
    }

    suspend fun update(bolusLog: BolusLog) {
        bolusLogDao.update(bolusLog)
    }

    suspend fun delete(bolusLog: BolusLog) {
        bolusLogDao.delete(bolusLog)
    }

    suspend fun getLatestManualBgLog(): BolusLog? {
        return bolusLogDao.getLatestManualBgLog()
    }
}