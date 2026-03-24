package com.example.diabetesapp.data.models

enum class InsulinType(
    val displayName: String,
    val typicalDurationHours: Float,
    val hint: String
) {
    NONE ("None / Not set", 0f, ""),
    ACTARAPID("Actrapid", 6f, "Typically ~6-8h"),
    APIDRA("Apidra", 4f, "Typically ~4-5h"),
    BERLININSULIN_NORMAL("Berlin Insulin Normal", 6f, "Typically ~6-8h"),
    FIASP("Fiasp", 3.5f, "Typically ~3-4h (ultra-fast)"),
    HUMALOG("Humalog", 4.5f, "Typically ~4-5h"),
    HUMALOG200("Humalog 200", 4.5f, "Typically ~4-5h"),
    HUMININSULIN_NORMAL("Humininsulin Normal", 6f, "Typically ~6-8h"),
    HUMULIN_R("Humulin R", 6f, "Typically ~6-8h"),
    HUMULIN_R_U500("Humulin R U500", 21f, "Typically ~13-24h (concentrated)"),
    INSULIN_lispro_SANOFI("Insulin lispro Sanofi", 4.5f, "Typically ~4-5h"),
    INSUMAN_INFUSATE("Insuman Infusate", 6f, "Typically ~6-8h"),
    INSUMAN_RAPID("Insuman Rapid", 6f, "Typically ~6-8h"),
    LIPROLOG("Liprolog", 4.5f, "Typically ~4-5h"),
    LIPROLOG200("Liprolog 200", 4.5f, "Typically ~4-5h"),
    LYUMJEV("Lyumjev", 3.5f, "Typically ~3-4h (ultra-fast)"),
    NOVORAPID("NovoRapid", 4.5f, "Typically ~3-4h"),
    NOVOLIN_R("NovoLin R", 6f, "Typically ~6-8h"),
    OTHER("Other", 4f, "Enter your own duration");

    companion object {
        fun fromDisplayName(name: String): InsulinType {
            return entries.find { it.displayName == name } ?: NOVORAPID
        }
    }
}
