package com.example.diabetesapp.viewmodel

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
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

sealed class ActivityStatus {
    object Connected : ActivityStatus()
    object NotConnected : ActivityStatus()
}

class MenuViewModel(
    private val settingsRepository: BolusSettingsRepository,
    private val context: Context? = null
) : ViewModel() {

    private val _cgmStatus = MutableStateFlow<CgmStatus>(CgmStatus.Disabled)
    val cgmStatus: StateFlow<CgmStatus> = _cgmStatus.asStateFlow()

    private val _activityStatus = MutableStateFlow<ActivityStatus>(ActivityStatus.NotConnected)
    val activityStatus: StateFlow<ActivityStatus> = _activityStatus.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = settingsRepository.getSettingsImmediate()
            if (!settings.isCgmEnabled) {
                _cgmStatus.value = CgmStatus.Disabled
            } else {
                val reading = CgmHelper.getLatestBgFromXDrip()
                if (reading == null) {
                    _cgmStatus.value = CgmStatus.Unreachable
                } else {
                    val ageMs = System.currentTimeMillis() - reading.timestamp
                    val minutesAgo = (ageMs / 60_000).toInt()
                    _cgmStatus.value = when {
                        minutesAgo < 15 -> CgmStatus.Connected(minutesAgo)
                        minutesAgo < 30 -> CgmStatus.Stale(minutesAgo)
                        else -> CgmStatus.NoSignal(minutesAgo)
                    }
                }
            }

            _activityStatus.value = checkActivityStatus()
        }
    }

    private suspend fun checkActivityStatus(): ActivityStatus {
        val ctx = context ?: return ActivityStatus.NotConnected
        return try {
            val sdkStatus = HealthConnectClient.getSdkStatus(ctx)
            if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) return ActivityStatus.NotConnected
            val client = HealthConnectClient.getOrCreate(ctx)
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class))) {
                ActivityStatus.Connected
            } else {
                ActivityStatus.NotConnected
            }
        } catch (e: Exception) {
            Log.w("MenuViewModel", "HC permission check failed: ${e.message}")
            ActivityStatus.NotConnected
        }
    }
}

class MenuViewModelFactory(
    private val settingsRepository: BolusSettingsRepository,
    private val context: Context? = null
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MenuViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MenuViewModel(settingsRepository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}