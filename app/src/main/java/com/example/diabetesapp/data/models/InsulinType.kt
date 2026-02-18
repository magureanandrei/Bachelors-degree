package com.example.diabetesapp.data.models

enum class InsulinType(val displayName: String) {
    ACTARAPID("Actrapid"),
    APIDRA("Apidra"),
    BERLININSULIN_NORMAL("Berlin Insulin Normal"),
    FIASP("Fiasp"),
    HUMALOG("Humalog"),
    HUMALOG200("Humalog 200"),
    HUMININSULIN_NORMAL("Humininsulin Normal"),
    HUMULIN_R("Humulin R"),
    HUMULIN_R_U500("Humulin R U500"),
    INSULIN_lispro_SANOFI("Insulin lispro Sanofi"),
    INSUMAN_INFUSATE("Insuman Infusate"),
    INSUMAN_RAPID("Insuman Rapid"),
    LIPROLOG("Liprolog"),
    LIPROLOG200("Liprolog 200"),
    LYUMJEV("Lyumjev"),
    NOVORAPID("NovoRapid"),
    NOVOLIN_R("NovoLin R"),
    OTHER("Other");
    companion object {
        fun fromDisplayName(name: String): InsulinType {
            return entries.find { it.displayName == name } ?: NOVORAPID
        }
    }
}

