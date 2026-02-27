package com.example.recemotion.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.recemotion.settings.SetupSettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class CalibrationButtonState {
    UNSET,          // 未設定: グレー + バツマーク
    NOW_SETTING,    // キャリブレーション中: ぐるぐる
    PASS_SETTINGS,  // 設定済み: 緑の再実行アイコン
    ERROR_SETTING   // 設定エラー: 赤の再実行アイコン + エラーメッセージ
}

data class SetupUiState(
    val cameraGranted: Boolean = false,
    val calibrationState: CalibrationButtonState = CalibrationButtonState.UNSET,
    val calibrationErrorMsg: String? = null,
    val showAutoSection: Boolean = false,    // キャリブレーション試行後に表示
    val autoCalibrate: Boolean = false,
    val showWakeTimeSection: Boolean = false, // キャリブレーション成功後に表示
    val wakeTimeText: String = "07:00",
    val wakeTimeUnix: Long = 0L,
    val wakeCountdown: Int = 5,
    val setupComplete: Boolean = false
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val settings: SetupSettingsStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }
        _uiState.update { it.copy(wakeTimeUnix = cal.timeInMillis / 1000) }
    }

    fun onCameraPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(cameraGranted = granted) }
    }

    fun onCalibrationStarted() {
        _uiState.update { it.copy(calibrationState = CalibrationButtonState.NOW_SETTING) }
    }

    fun onCalibrationSuccess() {
        _uiState.update {
            it.copy(
                calibrationState = CalibrationButtonState.PASS_SETTINGS,
                calibrationErrorMsg = null,
                showAutoSection = true,
                showWakeTimeSection = true
            )
        }
    }

    fun onCalibrationError(errorMsg: String) {
        _uiState.update {
            it.copy(
                calibrationState = CalibrationButtonState.ERROR_SETTING,
                calibrationErrorMsg = errorMsg,
                showAutoSection = true,
                showWakeTimeSection = false
            )
        }
    }

    fun onAutoCalibrateChanged(enabled: Boolean) {
        _uiState.update { it.copy(autoCalibrate = enabled) }
    }

    fun onClearCalibration() {
        _uiState.update {
            it.copy(
                calibrationState = CalibrationButtonState.UNSET,
                calibrationErrorMsg = null,
                showAutoSection = false,
                autoCalibrate = false,
                showWakeTimeSection = false
            )
        }
    }

    fun onWakeTimeChanged(hour: Int, minute: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
        }
        _uiState.update {
            it.copy(
                wakeTimeText = String.format("%02d:%02d", hour, minute),
                wakeTimeUnix = cal.timeInMillis / 1000
            )
        }
    }

    fun onWakeTimeDetected(hour: Int, minute: Int) {
        onWakeTimeChanged(hour, minute)
    }

    fun onCountdownTick(remaining: Int) {
        _uiState.update { it.copy(wakeCountdown = remaining) }
    }

    fun onSetupComplete() {
        _uiState.update { it.copy(setupComplete = true) }
    }

    fun saveSetup(date: String, wakeTimeUnix: Long, autoCalibrate: Boolean) {
        viewModelScope.launch {
            settings.setLastDate(date)
            settings.setWakeTimeUnix(wakeTimeUnix)
            settings.setAutoCalibrate(autoCalibrate)
        }
    }

    suspend fun getSavedAutoCalibrate(): Boolean = settings.autoCalibrateFlow.first()
}
