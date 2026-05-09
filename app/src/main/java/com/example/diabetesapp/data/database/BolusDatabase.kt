package com.example.diabetesapp.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.diabetesapp.data.dao.BolusLogDao
import com.example.diabetesapp.data.dao.DailyMetricsDao
import com.example.diabetesapp.data.models.BolusLog
import com.example.diabetesapp.data.models.DailyMetrics

@Database(entities = [BolusLog::class, DailyMetrics::class], version = 4, exportSchema = false)
abstract class BolusDatabase : RoomDatabase() {

    abstract fun bolusLogDao(): BolusLogDao
    abstract fun dailyMetricsDao(): DailyMetricsDao

    companion object {
        @Volatile
        private var INSTANCE: BolusDatabase? = null

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE daily_metrics ADD COLUMN avgBg REAL NOT NULL DEFAULT 0"
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_metrics (
                        dateEpochDay INTEGER NOT NULL PRIMARY KEY,
                        tbr REAL NOT NULL,
                        tir REAL NOT NULL,
                        tar REAL NOT NULL,
                        cv REAL NOT NULL,
                        steps INTEGER NOT NULL,
                        insulinUnits REAL NOT NULL,
                        carbs REAL NOT NULL,
                        readingCount INTEGER NOT NULL,
                        isCgmData INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        fun getDatabase(context: Context): BolusDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BolusDatabase::class.java,
                    "bolus_database"
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
