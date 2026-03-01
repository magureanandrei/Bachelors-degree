package com.example.diabetesapp.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.diabetesapp.data.models.BolusSettings
import com.example.diabetesapp.data.models.InsulinType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BolusSettingsRepository(context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<BolusSettings> = _settings.asStateFlow()

    private fun loadSettings(): BolusSettings {
        return BolusSettings(
            // --- NEW: Therapy Profile ---
            therapyType = sharedPreferences.getString(KEY_THERAPY_TYPE, "MDI") ?: "MDI",
            glucoseSource = sharedPreferences.getString(KEY_GLUCOSE_SOURCE, "MANUAL") ?: "MANUAL",

            insulinType = InsulinType.valueOf(
                sharedPreferences.getString(KEY_INSULIN_TYPE, InsulinType.NOVORAPID.name) ?: InsulinType.NOVORAPID.name
            ),
            durationOfAction = sharedPreferences.getFloat(KEY_DURATION, 4.0f),
            icrMorning = sharedPreferences.getFloat(KEY_ICR_MORNING, 10f),
            icrNoon = sharedPreferences.getFloat(KEY_ICR_NOON, 10f),
            icrEvening = sharedPreferences.getFloat(KEY_ICR_EVENING, 10f),
            icrNight = sharedPreferences.getFloat(KEY_ICR_NIGHT, 10f),
            isfMorning = sharedPreferences.getFloat(KEY_ISF_MORNING, 50f),
            isfNoon = sharedPreferences.getFloat(KEY_ISF_NOON, 50f),
            isfEvening = sharedPreferences.getFloat(KEY_ISF_EVENING, 50f),
            isfNight = sharedPreferences.getFloat(KEY_ISF_NIGHT, 50f),
            targetBG = sharedPreferences.getFloat(KEY_TARGET_BG, 100f)
        )
    }

    fun updateSettings(settings: BolusSettings) {
        sharedPreferences.edit().apply {
            // --- NEW: Therapy Profile ---
            putString(KEY_THERAPY_TYPE, settings.therapyType)
            putString(KEY_GLUCOSE_SOURCE, settings.glucoseSource)

            putString(KEY_INSULIN_TYPE, settings.insulinType.name)
            putFloat(KEY_DURATION, settings.durationOfAction)
            putFloat(KEY_ICR_MORNING, settings.icrMorning)
            putFloat(KEY_ICR_NOON, settings.icrNoon)
            putFloat(KEY_ICR_EVENING, settings.icrEvening)
            putFloat(KEY_ICR_NIGHT, settings.icrNight)
            putFloat(KEY_ISF_MORNING, settings.isfMorning)
            putFloat(KEY_ISF_NOON, settings.isfNoon)
            putFloat(KEY_ISF_EVENING, settings.isfEvening)
            putFloat(KEY_ISF_NIGHT, settings.isfNight)
            putFloat(KEY_TARGET_BG, settings.targetBG)
            apply()
        }
        _settings.value = settings
    }

    // --- NEW: Update functions for the Therapy Screen ---
    fun updateTherapyType(type: String) { updateSettings(_settings.value.copy(therapyType = type)) }
    fun updateGlucoseSource(source: String) { updateSettings(_settings.value.copy(glucoseSource = source)) }

    // --- Existing Update Functions ---
    fun updateInsulinType(insulinType: InsulinType) { updateSettings(_settings.value.copy(insulinType = insulinType)) }
    fun updateDurationOfAction(duration: Float) { updateSettings(_settings.value.copy(durationOfAction = duration)) }
    fun updateIcrMorning(value: Float) { updateSettings(_settings.value.copy(icrMorning = value)) }
    fun updateIcrNoon(value: Float) { updateSettings(_settings.value.copy(icrNoon = value)) }
    fun updateIcrEvening(value: Float) { updateSettings(_settings.value.copy(icrEvening = value)) }
    fun updateIcrNight(value: Float) { updateSettings(_settings.value.copy(icrNight = value)) }
    fun updateIsfMorning(value: Float) { updateSettings(_settings.value.copy(isfMorning = value)) }
    fun updateIsfNoon(value: Float) { updateSettings(_settings.value.copy(isfNoon = value)) }
    fun updateIsfEvening(value: Float) { updateSettings(_settings.value.copy(isfEvening = value)) }
    fun updateIsfNight(value: Float) { updateSettings(_settings.value.copy(isfNight = value)) }
    fun updateTargetBG(target: Float) { updateSettings(_settings.value.copy(targetBG = target)) }

    companion object {
        private const val PREFS_NAME = "bolus_settings_prefs"
        // --- NEW: Keys ---
        private const val KEY_THERAPY_TYPE = "therapy_type"
        private const val KEY_GLUCOSE_SOURCE = "glucose_source"

        private const val KEY_INSULIN_TYPE = "insulin_type"
        private const val KEY_DURATION = "duration_of_action"
        private const val KEY_ICR_MORNING = "icr_morning"
        private const val KEY_ICR_NOON = "icr_noon"
        private const val KEY_ICR_EVENING = "icr_evening"
        private const val KEY_ICR_NIGHT = "icr_night"
        private const val KEY_ISF_MORNING = "isf_morning"
        private const val KEY_ISF_NOON = "isf_noon"
        private const val KEY_ISF_EVENING = "isf_evening"
        private const val KEY_ISF_NIGHT = "isf_night"
        private const val KEY_TARGET_BG = "target_bg"

        @Volatile
        private var INSTANCE: BolusSettingsRepository? = null
        fun getInstance(context: Context): BolusSettingsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BolusSettingsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}