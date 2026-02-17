package com.example.recemotion.data.parser

/**
 * Dependency parser abstraction (CaboCha via JNI).
 */
interface DependencyParser {
    suspend fun parse(text: String): CabochaResult
}
