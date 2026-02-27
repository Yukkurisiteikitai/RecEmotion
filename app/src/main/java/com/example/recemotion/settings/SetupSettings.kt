package com.example.recemotion.settings

import com.example.settings.annotations.BoolSetting
import com.example.settings.annotations.LongSetting
import com.example.settings.annotations.SettingsGroup
import com.example.settings.annotations.StringSetting

@SettingsGroup("recemotion_setup")
interface SetupSettings {

    @StringSetting("setup_last_date", "")
    val lastDate: String

    @LongSetting("setup_wake_time_unix", 0L)
    val wakeTimeUnix: Long

    @BoolSetting("setup_auto_calibrate", false)
    val autoCalibrate: Boolean
}
