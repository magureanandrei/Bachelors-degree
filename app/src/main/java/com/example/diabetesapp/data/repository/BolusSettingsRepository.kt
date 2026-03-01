package com.example.diabetesapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.diabetesapp.data.models.BolusSettings
import com.example.diabetesapp.data.models.InsulinType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class BolusSettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("bolus_settings", Context.MODE_PRIVATE)

    // This is the "Magic" part. It listens for any change to SharedPreferences
    // and immediately pushes the new BolusSettings object into the Flow.
    val settings: Flow<BolusSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(getSettingsImmediate())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)

        // Push the initial values
        trySend(getSettingsImmediate())

        // Cleanup when the Flow is no longer needed
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun getSettingsImmediate(): BolusSettings {
        return BolusSettings(
            targetBG = prefs.getFloat("target_bg", 100f),
            hypoLimit = prefs.getFloat("hypo_limit", 70f),
            hyperLimit = prefs.getFloat("hyper_limit", 180f),

            // Therapy & Glucose Source
            therapyType = prefs.getString("therapy_type", "MDI") ?: "MDI",
            glucoseSource = prefs.getString("glucose_source", "MANUAL") ?: "MANUAL",

            // General Configuration
            insulinType = InsulinType.valueOf(
                prefs.getString("insulin_type", InsulinType.NOVORAPID.name) ?: InsulinType.NOVORAPID.name
            ),
            durationOfAction = prefs.getFloat("duration_of_action", 4.0f),
            maxBolus = prefs.getFloat("max_bolus", 15.0f),

            // ICR
            icrMorning = prefs.getFloat("icr_morning", 10f),
            icrNoon = prefs.getFloat("icr_noon", 10f),
            icrEvening = prefs.getFloat("icr_evening", 10f),
            icrNight = prefs.getFloat("icr_night", 10f),

            // ISF
            isfMorning = prefs.getFloat("isf_morning", 50f),
            isfNoon = prefs.getFloat("isf_noon", 50f),
            isfEvening = prefs.getFloat("isf_evening", 50f),
            isfNight = prefs.getFloat("isf_night", 50f)
        )
    }

    fun saveSettings(settings: BolusSettings) {
        prefs.edit().apply {
            putFloat("target_bg", settings.targetBG)
            putFloat("hypo_limit", settings.hypoLimit)
            putFloat("hyper_limit", settings.hyperLimit)

            putString("therapy_type", settings.therapyType)
            putString("glucose_source", settings.glucoseSource)
            putString("insulin_type", settings.insulinType.name)
            putFloat("duration_of_action", settings.durationOfAction)
            putFloat("max_bolus", settings.maxBolus)

            putFloat("icr_morning", settings.icrMorning)
            putFloat("icr_noon", settings.icrNoon)
            putFloat("icr_evening", settings.icrEvening)
            putFloat("icr_night", settings.icrNight)

            putFloat("isf_morning", settings.isfMorning)
            putFloat("isf_noon", settings.isfNoon)
            putFloat("isf_evening", settings.isfEvening)
            putFloat("isf_night", settings.isfNight)

            apply()
        }
    }

    // --- Helper Update Functions (Routing to saveSettings) ---
    private fun updateField(update: (BolusSettings) -> BolusSettings) {
        val current = getSettingsImmediate()
        saveSettings(update(current))
    }

    fun updateTherapyType(type: String) = updateField { it.copy(therapyType = type) }
    fun updateGlucoseSource(source: String) = updateField { it.copy(glucoseSource = source) }
    fun updateInsulinType(insulinType: InsulinType) = updateField { it.copy(insulinType = insulinType) }
    fun updateDurationOfAction(duration: Float) = updateField { it.copy(durationOfAction = duration) }

    fun updateIcrMorning(value: Float) = updateField { it.copy(icrMorning = value) }
    fun updateIcrNoon(value: Float) = updateField { it.copy(icrNoon = value) }
    fun updateIcrEvening(value: Float) = updateField { it.copy(icrEvening = value) }
    fun updateIcrNight(value: Float) = updateField { it.copy(icrNight = value) }

    fun updateIsfMorning(value: Float) = updateField { it.copy(isfMorning = value) }
    fun updateIsfNoon(value: Float) = updateField { it.copy(isfNoon = value) }
    fun updateIsfEvening(value: Float) = updateField { it.copy(isfEvening = value) }
    fun updateIsfNight(value: Float) = updateField { it.copy(isfNight = value) }

    fun updateTargetBG(target: Float) = updateField { it.copy(targetBG = target) }
    fun updateMaxBolus(value: Float) = updateField { it.copy(maxBolus = value) }
    fun updateHypoLimit(value: Float) = updateField { it.copy(hypoLimit = value) }
    fun updateHyperLimit(value: Float) = updateField { it.copy(hyperLimit = value) }

    companion object {
        @Volatile
        private var INSTANCE: BolusSettingsRepository? = null

        fun getInstance(context: Context): BolusSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BolusSettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}