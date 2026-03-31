package com.example.diabetesapp.data.models

data class BolusSettings(

    val therapyType: String = "MDI", // "MDI", "PUMP_STANDARD", "PUMP_AID"
    val glucoseSource: String = "MANUAL",  // "MANUAL" or "CGM"

    // General Configuration
    val insulinType: InsulinType = InsulinType.NOVORAPID,
    val durationOfAction: Float = 4.0f, // Duration in hours (decimal)

    val icrH0: Float = 10f,
    val icrH1: Float = 10f,
    val icrH2: Float = 10f,
    val icrH3: Float = 10f,
    val icrH4: Float = 10f,
    val icrH5: Float = 10f,
    val icrH6: Float = 10f,
    val icrH7: Float = 10f,
    val icrH8: Float = 10f,
    val icrH9: Float = 10f,
    val icrH10: Float = 10f,
    val icrH11: Float = 10f,
    val icrH12: Float = 10f,
    val icrH13: Float = 10f,
    val icrH14: Float = 10f,
    val icrH15: Float = 10f,
    val icrH16: Float = 10f,
    val icrH17: Float = 10f,
    val icrH18: Float = 10f,
    val icrH19: Float = 10f,
    val icrH20: Float = 10f,
    val icrH21: Float = 10f,
    val icrH22: Float = 10f,
    val icrH23: Float = 10f,

    val isfH0: Float = 50f,
    val isfH1: Float = 50f,
    val isfH2: Float = 50f,
    val isfH3: Float = 50f,
    val isfH4: Float = 50f,
    val isfH5: Float = 50f,
    val isfH6: Float = 50f,
    val isfH7: Float = 50f,
    val isfH8: Float = 50f,
    val isfH9: Float = 50f,
    val isfH10: Float = 50f,
    val isfH11: Float = 50f,
    val isfH12: Float = 50f,
    val isfH13: Float = 50f,
    val isfH14: Float = 50f,
    val isfH15: Float = 50f,
    val isfH16: Float = 50f,
    val isfH17: Float = 50f,
    val isfH18: Float = 50f,
    val isfH19: Float = 50f,
    val isfH20: Float = 50f,
    val isfH21: Float = 50f,
    val isfH22: Float = 50f,
    val isfH23: Float = 50f,

    // Blood Glucose Target (Global for V1)
    val targetBG: Float = 100f,

    val maxBolus: Float = 15.0f,    // Hard cap on calculator recommendations
    val hypoLimit: Float = 70.0f,   // Low line for graph & hypo warnings
    val hyperLimit: Float = 180.0f,  // High line for graph

    val basalInsulinType: BasalInsulinType = BasalInsulinType.NONE,
    val basalDurationHours: Float = 0f   // 0 = not set yet
) {
    // Computed properties for display
    val durationDisplay: String
        get() = "${durationOfAction}h"

    val targetBGDisplay: String
        get() = "${targetBG.toInt()} mg/dL"

    val icrProfile: List<Float>
        get() = listOf(icrH0, icrH1, icrH2, icrH3, icrH4, icrH5,
            icrH6, icrH7, icrH8, icrH9, icrH10, icrH11,
            icrH12, icrH13, icrH14, icrH15, icrH16, icrH17,
            icrH18, icrH19, icrH20, icrH21, icrH22, icrH23)

    val isfProfile: List<Float>
        get() = listOf(isfH0, isfH1, isfH2, isfH3, isfH4, isfH5,
            isfH6, isfH7, isfH8, isfH9, isfH10, isfH11,
            isfH12, isfH13, isfH14, isfH15, isfH16, isfH17,
            isfH18, isfH19, isfH20, isfH21, isfH22, isfH23)


    // Feature flags — use these everywhere instead of comparing therapyType strings directly
    val isPumpUser: Boolean get() = therapyType == "PUMP_STANDARD" || therapyType == "PUMP_AID"
    val isAidPump: Boolean get() = therapyType == "PUMP_AID"
    val isManualInsulin: Boolean get() = !isPumpUser
    val isCgmEnabled: Boolean get() = glucoseSource == "CGM"
    val therapyTypeEnum: TherapyType get() = TherapyType.fromString(therapyType)
    val isMdi: Boolean get() = therapyType == "MDI"

    // True only when MDI user has actually configured their basal insulin
    val hasBasalConfigured: Boolean
        get() = isMdi && basalInsulinType != BasalInsulinType.NONE && basalDurationHours > 0f

    fun getIcrForHour(hour: Int): Float = icrProfile[hour.coerceIn(0, 23)]
    fun getIsfForHour(hour: Int): Float = isfProfile[hour.coerceIn(0, 23)]

    fun getCurrentIcr(): Float = getIcrForHour(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    fun getCurrentIsf(): Float = getIsfForHour(java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY))
    val hasUniformIcr: Boolean
        get() = icrProfile.distinct().size == 1
    val hasUniformIsf: Boolean
        get() = isfProfile.distinct().size == 1
    val icrSummary: String
        get() {
            val values = icrProfile
            return if (values.distinct().size == 1) {
                "1:${values.first().toInt()}"
            } else {
                "1:${values.min().toInt()}-${values.max().toInt()}"
            }
        }
    val isfSummary: String
        get() {
            val values = isfProfile
            return if (values.distinct().size == 1) {
                "1:${values.first().toInt()}"
            } else {
                "1:${values.min().toInt()}-${values.max().toInt()}"
            }
        }
}

