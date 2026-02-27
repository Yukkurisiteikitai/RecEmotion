package com.example.settings.annotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class BoolSetting(val key: String, val default: Boolean)
