package com.example.diabetesapp.data.models

enum class BgFetchStatus {
    IDLE,       // no fetch attempted yet
    FOUND,      // BG was auto-filled
    NOT_FOUND   // fetch ran but no reading near this time
}