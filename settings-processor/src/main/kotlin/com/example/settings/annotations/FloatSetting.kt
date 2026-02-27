package com.example.settings.annotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class FloatSetting(val key: String, val default: Float)
