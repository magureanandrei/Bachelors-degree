package com.example.diabetesapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.diabetesapp.data.models.BasalInsulinType
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
            icrH0 = prefs.getFloat("icr_h0", 10f),
            icrH1 = prefs.getFloat("icr_h1", 10f),
            icrH2 = prefs.getFloat("icr_h2", 10f),
            icrH3 = prefs.getFloat("icr_h3", 10f),
            icrH4 = prefs.getFloat("icr_h4", 10f),
            icrH5 = prefs.getFloat("icr_h5", 10f),
            icrH6 = prefs.getFloat("icr_h6", 10f),
            icrH7 = prefs.getFloat("icr_h7", 10f),
            icrH8 = prefs.getFloat("icr_h8", 10f),
            icrH9 = prefs.getFloat("icr_h9", 10f),
            icrH10 = prefs.getFloat("icr_h10", 10f),
            icrH11 = prefs.getFloat("icr_h11", 10f),
            icrH12 = prefs.getFloat("icr_h12", 10f),
            icrH13 = prefs.getFloat("icr_h13", 10f),
            icrH14 = prefs.getFloat("icr_h14", 10f),
            icrH15 = prefs.getFloat("icr_h15", 10f),
            icrH16 = prefs.getFloat("icr_h16", 10f),
            icrH17 = prefs.getFloat("icr_h17", 10f),
            icrH18 = prefs.getFloat("icr_h18", 10f),
            icrH19 = prefs.getFloat("icr_h19", 10f),
            icrH20 = prefs.getFloat("icr_h20", 10f),
            icrH21 = prefs.getFloat("icr_h21", 10f),
            icrH22 = prefs.getFloat("icr_h22", 10f),
            icrH23 = prefs.getFloat("icr_h23", 10f),

            // ISF
            isfH0 = prefs.getFloat("isf_h0", 50f),
            isfH1 = prefs.getFloat("isf_h1", 50f),
            isfH2 = prefs.getFloat("isf_h2", 50f),
            isfH3 = prefs.getFloat("isf_h3", 50f),
            isfH4 = prefs.getFloat("isf_h4", 50f),
            isfH5 = prefs.getFloat("isf_h5", 50f),
            isfH6 = prefs.getFloat("isf_h6", 50f),
            isfH7 = prefs.getFloat("isf_h7", 50f),
            isfH8 = prefs.getFloat("isf_h8", 50f),
            isfH9 = prefs.getFloat("isf_h9", 50f),
            isfH10 = prefs.getFloat("isf_h10", 50f),
            isfH11 = prefs.getFloat("isf_h11", 50f),
            isfH12 = prefs.getFloat("isf_h12", 50f),
            isfH13 = prefs.getFloat("isf_h13", 50f),
            isfH14 = prefs.getFloat("isf_h14", 50f),
            isfH15 = prefs.getFloat("isf_h15", 50f),
            isfH16 = prefs.getFloat("isf_h16", 50f),
            isfH17 = prefs.getFloat("isf_h17", 50f),
            isfH18 = prefs.getFloat("isf_h18", 50f),
            isfH19 = prefs.getFloat("isf_h19", 50f),
            isfH20 = prefs.getFloat("isf_h20", 50f),
            isfH21 = prefs.getFloat("isf_h21", 50f),
            isfH22 = prefs.getFloat("isf_h22", 50f),
            isfH23 = prefs.getFloat("isf_h23", 50f),


            basalInsulinType = BasalInsulinType.fromName(
                prefs.getString("basal_insulin_type", BasalInsulinType.NONE.name)
                    ?: BasalInsulinType.NONE.name
            ),
            basalDurationHours = prefs.getFloat("basal_duration_hours", 0f)
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

            putFloat("icr_h0", settings.icrH0)
            putFloat("icr_h1", settings.icrH1)
            putFloat("icr_h2", settings.icrH2)
            putFloat("icr_h3", settings.icrH3)
            putFloat("icr_h4", settings.icrH4)
            putFloat("icr_h5", settings.icrH5)
            putFloat("icr_h6", settings.icrH6)
            putFloat("icr_h7", settings.icrH7)
            putFloat("icr_h8", settings.icrH8)
            putFloat("icr_h9", settings.icrH9)
            putFloat("icr_h10", settings.icrH10)
            putFloat("icr_h11", settings.icrH11)
            putFloat("icr_h12", settings.icrH12)
            putFloat("icr_h13", settings.icrH13)
            putFloat("icr_h14", settings.icrH14)
            putFloat("icr_h15", settings.icrH15)
            putFloat("icr_h16", settings.icrH16)
            putFloat("icr_h17", settings.icrH17)
            putFloat("icr_h18", settings.icrH18)
            putFloat("icr_h19", settings.icrH19)
            putFloat("icr_h20", settings.icrH20)
            putFloat("icr_h21", settings.icrH21)
            putFloat("icr_h22", settings.icrH22)
            putFloat("icr_h23", settings.icrH23)

            putFloat("isf_h0", settings.isfH0)
            putFloat("isf_h1", settings.isfH1)
            putFloat("isf_h2", settings.isfH2)
            putFloat("isf_h3", settings.isfH3)
            putFloat("isf_h4", settings.isfH4)
            putFloat("isf_h5", settings.isfH5)
            putFloat("isf_h6", settings.isfH6)
            putFloat("isf_h7", settings.isfH7)
            putFloat("isf_h8", settings.isfH8)
            putFloat("isf_h9", settings.isfH9)
            putFloat("isf_h10", settings.isfH10)
            putFloat("isf_h11", settings.isfH11)
            putFloat("isf_h12", settings.isfH12)
            putFloat("isf_h13", settings.isfH13)
            putFloat("isf_h14", settings.isfH14)
            putFloat("isf_h15", settings.isfH15)
            putFloat("isf_h16", settings.isfH16)
            putFloat("isf_h17", settings.isfH17)
            putFloat("isf_h18", settings.isfH18)
            putFloat("isf_h19", settings.isfH19)
            putFloat("isf_h20", settings.isfH20)
            putFloat("isf_h21", settings.isfH21)
            putFloat("isf_h22", settings.isfH22)
            putFloat("isf_h23", settings.isfH23)

            putString("basal_insulin_type", settings.basalInsulinType.name)
            putFloat("basal_duration_hours", settings.basalDurationHours)

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

    fun updateIcrProfile(rates: List<Float>) {
        require(rates.size == 24)
        val current = getSettingsImmediate()
        saveSettings(current.copy(
            icrH0 = rates[0], icrH1 = rates[1], icrH2 = rates[2], icrH3 = rates[3],
            icrH4 = rates[4], icrH5 = rates[5], icrH6 = rates[6], icrH7 = rates[7],
            icrH8 = rates[8], icrH9 = rates[9], icrH10 = rates[10], icrH11 = rates[11],
            icrH12 = rates[12], icrH13 = rates[13], icrH14 = rates[14], icrH15 = rates[15],
            icrH16 = rates[16], icrH17 = rates[17], icrH18 = rates[18], icrH19 = rates[19],
            icrH20 = rates[20], icrH21 = rates[21], icrH22 = rates[22], icrH23 = rates[23]
        ))
    }

    fun updateIsfProfile(rates: List<Float>) {
        require(rates.size == 24)
        val current = getSettingsImmediate()
        saveSettings(current.copy(
            isfH0 = rates[0], isfH1 = rates[1], isfH2 = rates[2], isfH3 = rates[3],
            isfH4 = rates[4], isfH5 = rates[5], isfH6 = rates[6], isfH7 = rates[7],
            isfH8 = rates[8], isfH9 = rates[9], isfH10 = rates[10], isfH11 = rates[11],
            isfH12 = rates[12], isfH13 = rates[13], isfH14 = rates[14], isfH15 = rates[15],
            isfH16 = rates[16], isfH17 = rates[17], isfH18 = rates[18], isfH19 = rates[19],
            isfH20 = rates[20], isfH21 = rates[21], isfH22 = rates[22], isfH23 = rates[23]
        ))
    }

    fun updateTargetBG(target: Float) = updateField { it.copy(targetBG = target) }
    fun updateMaxBolus(value: Float) = updateField { it.copy(maxBolus = value) }
    fun updateHypoLimit(value: Float) = updateField { it.copy(hypoLimit = value) }
    fun updateHyperLimit(value: Float) = updateField { it.copy(hyperLimit = value) }

    fun updateBasalInsulinType(type: BasalInsulinType) =
        updateField { it.copy(basalInsulinType = type) }

    fun updateBasalDurationHours(hours: Float) =
        updateField { it.copy(basalDurationHours = hours) }

    fun recordSettingsChange(description: String) {
        val existing = prefs.getString("settings_changes", "") ?: ""
        val newEntry = "${System.currentTimeMillis()}|$description"
        val updated = if (existing.isEmpty()) newEntry else "$existing||$newEntry"
        prefs.edit().putString("settings_changes", updated).apply()
    }

    fun getSettingsChanges(): List<Pair<Long, String>> {
        val raw = prefs.getString("settings_changes", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("||").mapNotNull { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) Pair(parts[0].toLongOrNull() ?: return@mapNotNull null, parts[1])
            else null
        }
    }

    fun hasCompletedOnboarding(): Boolean = prefs.getBoolean("has_completed_onboarding", false)
    fun setOnboardingComplete() = prefs.edit().putBoolean("has_completed_onboarding", true).apply()

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