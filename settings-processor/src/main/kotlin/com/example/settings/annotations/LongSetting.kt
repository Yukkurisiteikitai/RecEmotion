package com.example.settings.annotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class LongSetting(val key: String, val default: Long)
