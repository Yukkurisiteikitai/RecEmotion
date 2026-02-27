package com.example.settings.annotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class IntSetting(val key: String, val default: Int)
