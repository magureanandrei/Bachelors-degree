package com.example.diabetesapp.utils

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class CgmReading(
    val timestamp: Long,
    val bgValue: Int,
    val trendString: String,
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
}