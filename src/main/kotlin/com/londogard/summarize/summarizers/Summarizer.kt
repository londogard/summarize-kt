package com.londogard.summarize.summarizers

interface Summarizer {
    fun summarize(text: String, lines: Int = 10): String
    fun summarize(text: String, ratio: Double = 0.3): String

    companion object {
        /**
         * threshold: Threshold for TfIdf words
         * simThreshold: Threshold on how similar two sentences can at max be
         * scoreConfig: The config used to score
         * embeddingOverload: Pair<FILEPATH, DIMENSION> where we overload and use our own embeddings.
         */
        fun embeddingClusterSummarizer(
            threshold: Double = 0.2,
            simThreshold: Double = 0.95,
            scoreConfig: ScoringConfig = ScoringConfig.Ghalandari,
            embeddingOverload: Pair<String, Int>? = null
        ): Summarizer =
            EmbeddingClusterSummarizer(threshold, simThreshold, scoreConfig, embeddingOverload)

        val tfIdfSummarizer: Summarizer by lazy { TfIdfSummarizer() }
    }
}