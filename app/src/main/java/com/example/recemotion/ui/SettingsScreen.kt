package com.example.recemotion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.example.recemotion.presentation.SettingsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BackgroundColor = Color(0xFF11151B)
private val TextPrimary    = Color.White
private val TextSecondary  = Color(0xFF888888)
private val DividerColor   = Color(0xFF2A2F3B)
private val AccentGreen    = Color(0xFF4CAF50)

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onAutoCalibrateChanged: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundColor)
            .padding(horizontal = 24.dp)
    ) {
        // Header — matches existing XML: sans-serif-light, letterSpacing 0.2, 28sp, white, marginTop 80dp
        Spacer(modifier = Modifier.height(80.dp))
        Text(
            text = "SETTINGS",
            color = TextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 0.2.em
        )

        Spacer(modifier = Modifier.height(40.dp))

        // --- Auto Calibrate (editable) ---
        SettingToggleRow(
            label = "Auto Calibrate",
            description = "Automatically start face calibration on launch",
            checked = uiState.autoCalibrate,
            onCheckedChange = onAutoCalibrateChanged
        )

        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)

        // --- Wake Time (read-only display) ---
        SettingDisplayRow(
            label = "Wake Time",
            value = formatWakeTime(uiState.wakeTimeUnix)
        )

        HorizontalDivider(color = DividerColor, thickness = 0.5.dp)

        // --- Last Date (read-only display) ---
        SettingDisplayRow(
            label = "Last Setup Date",
            value = uiState.lastDate.ifEmpty { "—" }
        )
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label,      color = TextPrimary,   fontSize = 16.sp)
            Text(text = description, color = TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = { newValue ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(newValue)
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor   = AccentGreen,
                checkedTrackColor   = AccentGreen.copy(alpha = 0.4f),
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = TextSecondary.copy(alpha = 0.3f)
            )
        )
    }
}

@Composable
private fun SettingDisplayRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = TextPrimary,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            color = TextSecondary,
            fontSize = 14.sp
        )
    }
}

private fun formatWakeTime(wakeTimeUnix: Long): String {
    if (wakeTimeUnix == 0L) return "—"
    return try {
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        fmt.format(Date(wakeTimeUnix * 1000L))
    } catch (e: Exception) {
        "—"
    }
}
