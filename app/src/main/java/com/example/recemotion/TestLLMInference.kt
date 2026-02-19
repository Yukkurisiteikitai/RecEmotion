package com.example.recemotion

import android.util.Log
import com.example.recemotion.data.llm.LlmStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * テスト用ダミーLLM推論
 * 実際のモデルファイルがない場合に使用
 */
object TestLLMInference {
    private const val TAG = "TestLLMInference"

    fun analyzeThoughtStructure(structureText: String): Flow<LlmStreamEvent> = flow {
        try {
            Log.d(TAG, "Starting dummy LLM inference for testing")
            
            // 問題11 修正: ThoughtPromptBuilder が要求するスキーマ・ThoughtAnalysisJsonParser が
            // 期待するフィールド（premises / emotions / inferences / possibleBiases /
            // missingPerspectives）に合わせたダミーレスポンス
            val dummyResponse = """
                {
                  "premises": ["テキスト入力が行われた", "ユーザーが感情を表現している"],
                  "emotions": ["喜び", "興奮"],
                  "inferences": ["ユーザーはポジティブな感情状態にある"],
                  "possibleBiases": [
                    {"name": "楽観バイアス", "evidence": "ポジティブな表現が多い"}
                  ],
                  "missingPerspectives": [
                    {"description": "否定的な側面の考慮が不足している可能性がある"}
                  ]
                }
            """.trimIndent()

            // ストリーミングのように少しずつ送信
            val chunkSize = 50
            for (i in dummyResponse.indices step chunkSize) {
                val chunk = dummyResponse.substring(
                    i,
                    minOf(i + chunkSize, dummyResponse.length)
                )
                emit(LlmStreamEvent.Delta(chunk))
                
                // ストリーミング感を出すために遅延
                kotlinx.coroutines.delay(100)
            }
            
            emit(LlmStreamEvent.Done(dummyResponse))
            Log.d(TAG, "Dummy inference completed")
        } catch (e: Exception) {
            Log.e(TAG, "Error in test inference", e)
            emit(LlmStreamEvent.Error("Test error: ${e.message}"))
        }
    }
}
