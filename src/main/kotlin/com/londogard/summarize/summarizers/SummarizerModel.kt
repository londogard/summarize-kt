package com.londogard.summarize.summarizers

interface SummarizerModel {
    fun summarize(text: String, lines: Int = 10): String
    fun summarize(text: String, ratio: Double = 0.3): String

    companion object {
        fun embeddingClusterSummarizer(
            threshold: Double = 0.3,
            simThreshold: Double = 0.95,
            scoreConfig: ScoringConfig = ScoringConfig.Ghalandari,
            keepEmbeddingsInRAM: Boolean = false
        ): SummarizerModel =
            EmbeddingClusterSummarizer(threshold, simThreshold, scoreConfig, keepEmbeddingsInRAM)

        fun tfIdfSummarizer(): SummarizerModel = TfIdfSummarizer()
    }
}