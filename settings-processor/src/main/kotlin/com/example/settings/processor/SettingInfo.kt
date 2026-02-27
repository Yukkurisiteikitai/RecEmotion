package com.example.settings.processor

enum class SettingType { STRING, INT, LONG, FLOAT, BOOL }

data class SettingInfo(
    val propName: String,
    val type: SettingType,
    val key: String,
    val defaultValue: Any
)
