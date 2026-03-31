package com.example.diabetesapp.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.diabetesapp.data.repository.BolusSettingsRepository
import com.example.diabetesapp.utils.CgmHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CgmStatus {
    object Disabled : CgmStatus()
    object Unreachable : CgmStatus()
    data class Connected(val minutesAgo: Int) : CgmStatus()
    data class Stale(val minutesAgo: Int) : CgmStatus()
    data class NoSignal(val minutesAgo: Int?) : CgmStatus()
}

class MenuViewModel(
    private val settingsRepository: BolusSettingsRepository
) : ViewModel() {

    private val _cgmStatus = MutableStateFlow<CgmStatus>(CgmStatus.Disabled)
    val cgmStatus: StateFlow<CgmStatus> = _cgmStatus.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettingsImmediate()
            if (!settings.isCgmEnabled) {
                _cgmStatus.value = CgmStatus.Disabled
                return@launch
            }
            val reading = CgmHelper.getLatestBgFromXDrip()
            if (reading == null) {
                _cgmStatus.value = CgmStatus.Unreachable
                return@launch
            }
            val ageMs = System.currentTimeMillis() - reading.timestamp
            val minutesAgo = (ageMs / 60_000).toInt()
            _cgmStatus.value = when {
                minutesAgo < 15 -> CgmStatus.Connected(minutesAgo)
                minutesAgo < 30 -> CgmStatus.Stale(minutesAgo)
                else -> CgmStatus.NoSignal(minutesAgo)
            }
        }
    }
}

class MenuViewModelFactory(
    private val settingsRepository: BolusSettingsRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MenuViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MenuViewModel(settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}