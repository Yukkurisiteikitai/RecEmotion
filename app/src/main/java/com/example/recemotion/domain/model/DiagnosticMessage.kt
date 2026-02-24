package com.example.recemotion.domain.model

/** Domain-level diagnostic result. Presentation layer maps this to UI items. */
data class DiagnosticMessage(
    val text: String,
    val isError: Boolean = false
)
