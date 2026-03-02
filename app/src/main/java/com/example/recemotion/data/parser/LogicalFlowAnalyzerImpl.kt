package com.example.recemotion.data.parser

import android.util.Log
import com.example.recemotion.domain.model.LogicalFlowAnalysis
import com.example.recemotion.domain.service.LogicalFlowService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implementation of [LogicalFlowService].
 *
 * Orchestrates sentence tokenization ([SentenceTokenizer]),
 * morphological analysis ([MorphemeAnalyzer]), and logical relation
 * detection ([RelationDetector]) via dependency injection.
 *
 * When [nativeParser] is null (the default Hilt-provided state),
 * analysis falls back to Kuromoji inside [MorphemeAnalyzer].
 */
class LogicalFlowAnalyzerImpl @Inject constructor(
    private val sentenceTokenizer: SentenceTokenizer,
    private val morphemeAnalyzer: MorphemeAnalyzer,
    private val relationDetector: RelationDetector
) : LogicalFlowService {

    companion object {
        private const val TAG = "LogicalFlowAnalyzerImpl"
    }

    override suspend fun analyze(text: String): LogicalFlowAnalysis = analyze(text, null)

    suspend fun analyze(text: String, nativeParser: NativeCabochaParser?): LogicalFlowAnalysis =
        withContext(Dispatchers.Default) {
            val rawSentences = sentenceTokenizer.split(text)
            Log.d(TAG, "Split into ${rawSentences.size} sentences (parser=${if (nativeParser != null) "CaboCha" else "Kuromoji"})")

            val analyzed = rawSentences.mapIndexed { idx, s ->
                morphemeAnalyzer.analyze(idx, s, nativeParser)
            }
            val relations = relationDetector.detect(analyzed, rawSentences)

            LogicalFlowAnalysis(analyzed, relations)
        }
}
