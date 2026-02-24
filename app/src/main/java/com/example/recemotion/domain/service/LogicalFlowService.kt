package com.example.recemotion.domain.service

import com.example.recemotion.domain.model.LogicalFlowAnalysis

/**
 * Domain interface for logical flow analysis of Japanese text.
 */
interface LogicalFlowService {
    suspend fun analyze(text: String): LogicalFlowAnalysis
}
