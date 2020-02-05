package com.londogard.summarize

import com.londogard.summarize.summarizers.EmbeddingClusterSummarizer
import com.londogard.summarize.summarizers.ScoringConfig
import com.londogard.summarize.summarizers.SummarizerModel
import com.londogard.summarize.summarizers.TfIdfSummarizer

sealed class SummarizeVariant
object TfIdf : SummarizeVariant()
data class EmbeddingCluster(
    val threshold: Double = 0.3,
    val similarityThreshold: Double = 0.95,
    val scoringConfig: ScoringConfig = ScoringConfig.Ghalandari
) : SummarizeVariant()

class Summarizer(summarizer: SummarizeVariant = TfIdf) {
    private val summarizerMethod: SummarizerModel = when (summarizer) {
        is TfIdf -> TfIdfSummarizer()
        is EmbeddingCluster -> EmbeddingClusterSummarizer(
            summarizer.threshold, summarizer.similarityThreshold, summarizer.scoringConfig
        )
    }

    fun summarize(text: String, lines: Int): String = summarizerMethod.summarize(text, lines)

    fun summarize(text: String, ratio: Double): String = summarizerMethod.summarize(text, ratio)
}