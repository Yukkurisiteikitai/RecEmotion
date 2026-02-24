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

    /** Optional native parser; can be set after async initialization. */
    var nativeParser: NativeCabochaParser? = null

    override suspend fun analyze(text: String): LogicalFlowAnalysis = withContext(Dispatchers.Default) {
        val rawSentences = sentenceTokenizer.split(text)
        Log.d(TAG, "Split into ${rawSentences.size} sentences (parser=${if (nativeParser != null) "CaboCha" else "Kuromoji"})")

        val analyzed = rawSentences.mapIndexed { idx, s ->
            morphemeAnalyzer.analyze(idx, s, nativeParser)
        }
        val relations = relationDetector.detect(analyzed, rawSentences)
        val overallFlow = buildOverallFlow(analyzed)

        LogicalFlowAnalysis(analyzed, relations, overallFlow)
    }

    private fun buildOverallFlow(sentences: List<com.example.recemotion.domain.model.AnalyzedSentence>): List<String> {
        return sentences.map { s ->
            val timePrefix = if (s.timeMarkers.isNotEmpty()) "[${s.timeMarkers.first()}] " else ""
            val subj = s.structure.subject.ifEmpty { "(主語不明)" }
            val objPart = if (s.structure.obj.isNotEmpty()) "「${s.structure.obj}」を" else ""
            val verb = s.structure.verb.ifEmpty { "(述語不明)" }
            "$timePrefix$subj が ${objPart}${verb}"
        }
    }
}
