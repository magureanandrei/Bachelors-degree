package com.example.diabetesapp.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.diabetesapp.data.dao.BolusLogDao
import com.example.diabetesapp.data.models.BolusLog

@Database(entities = [BolusLog::class], version = 1, exportSchema = false)
abstract class BolusDatabase : RoomDatabase() {

    abstract fun bolusLogDao(): BolusLogDao

    companion object {
        @Volatile
        private var INSTANCE: BolusDatabase? = null

        fun getDatabase(context: Context): BolusDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BolusDatabase::class.java,
                    "bolus_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

