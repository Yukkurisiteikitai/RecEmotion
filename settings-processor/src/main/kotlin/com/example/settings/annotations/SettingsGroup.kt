package com.example.settings.annotations

/**
 * Marks an interface as a settings schema.
 * KSP generates a [name]Store class and a Hilt module for it.
 *
 * @param name DataStore file name (e.g. "recemotion_setup")
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class SettingsGroup(val name: String)
