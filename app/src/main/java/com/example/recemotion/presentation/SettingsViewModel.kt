package com.example.recemotion.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recemotion.settings.SetupSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val autoCalibrate: Boolean = false,
    val wakeTimeUnix: Long = 0L,
    val lastDate: String = ""
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: SetupSettingsStore
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        store.autoCalibrateFlow,
        store.wakeTimeUnixFlow,
        store.lastDateFlow
    ) { autoCalibrate, wakeTimeUnix, lastDate ->
        SettingsUiState(
            autoCalibrate = autoCalibrate,
            wakeTimeUnix = wakeTimeUnix,
            lastDate = lastDate
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = SettingsUiState()
    )

    fun setAutoCalibrate(value: Boolean) {
        viewModelScope.launch {
            store.setAutoCalibrate(value)
        }
    }
}
