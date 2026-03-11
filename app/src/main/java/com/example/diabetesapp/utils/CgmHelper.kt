package com.example.diabetesapp.utils

import android.util.Log
import com.example.diabetesapp.data.models.BolusLog
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class CgmReading(
    val timestamp: Long,
    val bgValue: Int,
    val carbs: Float = 0f,
    val insulin: Float = 0f,
    val trendString: String = "",
    val iob: Double? = null
)

object CgmHelper {

    // ---------------------------------------------------------------------------------
    // FUNCTION 1: Gets the single latest reading for the Smart Bolus calculator & Widget
    // ---------------------------------------------------------------------------------
    fun getLatestBgFromXDrip(): CgmReading? {
        Log.d("CGM_Fetch", "--> Attempting to fetch rich pebble data...")

        return try {
            val url = URL("http://127.0.0.1:17580/pebble")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            // Short timeouts so your app UI doesn't freeze if xDrip is closed
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonObject = JSONObject(response)
                val bgsArray = jsonObject.optJSONArray("bgs")

                if (bgsArray != null && bgsArray.length() > 0) {
                    val latest = bgsArray.getJSONObject(0)

                    // 1. Get Timestamp (Pebble calls it 'datetime')
                    val timestamp = latest.optLong("datetime", System.currentTimeMillis())

                    // 2. Get BG Value
                    val bgString = latest.optString("sgv", "")
                    val bgValue = bgString.toIntOrNull()

                    // 3. Get Trend
                    val trend = latest.optString("direction", "")
                    val trendArrow = when (trend) {
                        "DoubleUp" -> "↑↑"
                        "SingleUp" -> "↑"
                        "FortyFiveUp" -> "↗"
                        "Flat" -> "→"
                        "FortyFiveDown" -> "↘"
                        "SingleDown" -> "↓"
                        "DoubleDown" -> "↓↓"
                        else -> ""
                    }

                    // 4. Get Active Insulin (IOB)
                    val iobString = latest.optString("iob", "")
                    val iobValue = iobString.toDoubleOrNull()

                    if (bgValue != null) {
                        // Pass all the extracted data back to the ViewModel
                        return CgmReading(
                            timestamp = timestamp,
                            bgValue = bgValue,
                            trendString = trendArrow,
                            iob = iobValue
                        )
                    } else {
                        Log.e("CGM_Fetch", "--> Error: Could not parse SGV into a number.")
                        return null
                    }
                } else {
                    Log.e("CGM_Fetch", "--> JSON 'bgs' Array was empty.")
                    null
                }
            } else {
                Log.e("CGM_Fetch", "--> Server returned HTTP ${connection.responseCode}")
                null
            }
        } catch (e: Exception) {
            Log.e("CGM_Fetch", "--> CRITICAL CRASH: ${e.message}", e)
            null
        }
    }

    // ---------------------------------------------------------------------------------
    // FUNCTION 2: Gets the 24-hour history array for the Home Screen Graph
    // ---------------------------------------------------------------------------------
    fun getBgHistoryFromXDrip(): List<CgmReading> {
        val readings = mutableListOf<CgmReading>()
        Log.d("CGM_History", "--> Attempting to fetch 24-hour history...")

        return try {
            // xDrip's local shortcut for historical data is /sgv.json
            val url = URL("http://127.0.0.1:17580/sgv.json?count=288")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                // /sgv.json returns a direct array, so we parse it immediately
                val jsonArray = JSONArray(response)

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)

                    // The standard Nightscout JSON uses 'sgv' and 'date'
                    val bgValue = item.optInt("sgv", 0)
                    val timestamp = item.optLong("date", 0L)

                    if (bgValue > 0 && timestamp > 0L) {
                        readings.add(
                            CgmReading(
                                timestamp = timestamp,
                                bgValue = bgValue,
                                trendString = "", // Not needed for history dots
                                iob = null        // Not needed for history dots
                            )
                        )
                    }
                }
                Log.d("CGM_History", "--> SUCCESS! Fetched ${readings.size} historical readings.")

                // Return them sorted from oldest to newest so the graph draws left-to-right
                readings.sortedBy { it.timestamp }
            } else {
                Log.e("CGM_History", "--> Server returned HTTP ${connection.responseCode}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("CGM_History", "--> CRITICAL CRASH: ${e.message}", e)
            emptyList()
        }
    }
    fun getTreatmentsFromXDrip(): List<BolusLog> {
        Log.d("CGM_Treatments", "--> Attempting to fetch treatments...")

        return try {
            val url = URL("http://127.0.0.1:17580/treatments.json?count=100&find[eventType][\$ne]=temp+target")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            Log.d("CGM_Treatments", "Response code: ${connection.responseCode}")

            if (connection.responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonArray = JSONArray(response)
                Log.d("CGM_Treatments", "Raw response has ${jsonArray.length()} items")

                // Step 1: Parse everything into a flat list first
                data class RawTreatment(
                    val timestamp: Long,
                    val carbs: Double,
                    val insulin: Double,
                    val notes: String,
                    val eventType: String
                )

                val rawList = mutableListOf<RawTreatment>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)

                    var timestamp = item.optLong("mills", 0L)
                    if (timestamp == 0L) timestamp = item.optLong("timestamp", 0L)
                    if (timestamp == 0L) timestamp = item.optLong("created_at", 0L)
                    if (timestamp in 1_000_000_000L..9_999_999_999L) timestamp *= 1000L

                    if (timestamp == 0L) continue

                    val carbs = item.optDouble("carbs", 0.0)
                    val insulin = item.optDouble("insulin", 0.0)
                    val notes = item.optString("notes", "")
                    val eventType = item.optString("eventType", "")

                    // Only keep entries that have something meaningful
                    if (carbs > 0 || insulin > 0) {
                        rawList.add(RawTreatment(timestamp, carbs, insulin, notes, eventType))
                    }
                }

                // Step 2: Group by timestamp (CareLink splits food+bolus into same timestamp)
                // Use a 60-second window to catch entries that are "almost" the same time
                val grouped = mutableMapOf<Long, MutableList<RawTreatment>>()
                val windowMs = 60 * 1000L

                rawList.forEach { raw ->
                    // Find an existing group within 60 seconds of this entry
                    val existingKey = grouped.keys.firstOrNull {
                        Math.abs(it - raw.timestamp) <= windowMs
                    }
                    if (existingKey != null) {
                        grouped[existingKey]!!.add(raw)
                    } else {
                        grouped[raw.timestamp] = mutableListOf(raw)
                    }
                }

                // Step 3: Merge each group into a single BolusLog and apply filters
                val treatmentLogs = mutableListOf<BolusLog>()

                grouped.forEach { (groupTimestamp, entries) ->
                    val totalCarbs = entries.sumOf { it.carbs }
                    val totalInsulin = entries.sumOf { it.insulin }
                    val notes = entries.firstOrNull { it.notes.isNotEmpty() }?.notes ?: "xDrip Sync"
                    val isCalibration = entries.any {
                        it.eventType.contains("calibration", ignoreCase = true)
                    }

                    // Determine event type
                    val eventType = when {
                        isCalibration -> "CALIBRATION"
                        totalCarbs > 0 && totalInsulin > 0 -> "MEAL"
                        totalCarbs > 0 -> "MEAL"
                        else -> "CORRECTION"
                    }

                    // Apply filter rules:
                    // - Always keep meals (carbs > 0)
                    // - Always keep calibrations
                    // - Only keep corrections >= 1.5U (drop microboluses)
                    val shouldKeep = when (eventType) {
                        "MEAL" -> true
                        "CALIBRATION" -> true
                        "CORRECTION" -> totalInsulin >= 1.5
                        else -> false
                    }

                    if (shouldKeep) {
                        Log.d("CGM_Treatments", "  Keeping: ts=$groupTimestamp carbs=$totalCarbs insulin=$totalInsulin type=$eventType")
                        treatmentLogs.add(
                            BolusLog(
                                timestamp = groupTimestamp,
                                carbs = totalCarbs,
                                administeredDose = totalInsulin,
                                standardDose = totalInsulin,
                                suggestedDose = totalInsulin,
                                notes = "Auto-entry via CareLink",
                                eventType = eventType,
                                bloodGlucose = 0.0,
                                isSportModeActive = false,
                                sportType = null,
                                sportIntensity = null,
                                sportDuration = null,
                                clinicalSuggestion = null
                            )
                        )
                    } else {
                        Log.d("CGM_Treatments", "  Skipping microbolus: ts=$groupTimestamp insulin=$totalInsulin")
                    }
                }

                Log.d("CGM_Treatments", "--> SUCCESS! Fetched ${treatmentLogs.size} treatments after filtering.")
                treatmentLogs.sortedBy { it.timestamp }
            } else {
                Log.e("CGM_Treatments", "--> Server returned HTTP ${connection.responseCode}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("CGM_Treatments", "--> CRITICAL CRASH: ${e.message}", e)
            emptyList()
        }
    }
}