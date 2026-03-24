package com.example.diabetesapp.data.models

/**
 * Long-acting (basal) insulin types for MDI patients.
 * Duration is NOT pre-filled — user must enter their own value.
 * typicalDurationHours is shown as a hint label in the UI only.
 */
enum class BasalInsulinType(
    val displayName: String,
    val typicalDurationHours: Float,
    val hint: String
) {
    NONE("None / Not set", 0f, ""),

    LANTUS("Lantus (Glargine U100)", 24f, "Typically ~24h"),
    BASAGLAR("Basaglar (Glargine biosimilar)", 24f, "Typically ~24h"),
    TOUJEO("Toujeo (Glargine U300)", 36f, "Typically ~30-36h"),
    LEVEMIR("Levemir (Detemir)", 20f, "Typically ~18-22h"),
    TRESIBA("Tresiba U100 (Degludec)", 42f, "Typically ~42h"),
    TRESIBA_200("Tresiba U200 (Degludec)", 42f, "Typically ~42h"),
    NPH("NPH (Isophane)", 12f, "Typically ~10-14h"),
    INSULATARD("Insulatard (NPH)", 12f, "Typically ~10-14h"),
    OTHER("Other", 24f, "Enter your own duration");

    companion object {
        fun fromName(name: String): BasalInsulinType =
            entries.find { it.name == name } ?: NONE
    }
}