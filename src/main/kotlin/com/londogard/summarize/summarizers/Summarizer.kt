package com.londogard.summarize.summarizers

interface Summarizer {
    fun summarize(text: String, lines: Int = 10): String
    fun summarize(text: String, ratio: Double = 0.3): String

    companion object {
        fun embeddingClusterSummarizer(
            threshold: Double = 0.2,
            simThreshold: Double = 0.95,
            scoreConfig: ScoringConfig = ScoringConfig.Ghalandari
        ): Summarizer =
            EmbeddingClusterSummarizer(threshold, simThreshold, scoreConfig)

        val tfIdfSummarizer: Summarizer by lazy { TfIdfSummarizer() }
    }
}