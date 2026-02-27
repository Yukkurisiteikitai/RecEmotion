package com.example.settings.annotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class StringSetting(val key: String, val default: String)
