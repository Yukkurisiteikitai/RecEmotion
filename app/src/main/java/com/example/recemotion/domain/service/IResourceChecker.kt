package com.example.recemotion.domain.service

import java.io.File

/**
 * Domain interface for checking whether required system resources are installed.
 */
interface IResourceChecker {
    fun isDictionaryInstalled(): Boolean
    fun isCabochaModelInstalled(): Boolean
    fun resolveLlmModelFile(): File?
}
