package com.londogard.summarize

import com.londogard.summarize.summarizers.Summarizer
import com.londogard.summarize.summarizers.TfIdfSummarizer
import java.nio.file.Files
import java.nio.file.Paths

enum class SummarizeVariant {
    TfIdf
    //EmbeddingCluster
}

class Summarize(val summarizer: SummarizeVariant = SummarizeVariant.TfIdf) {
    lateinit var summarizerMethod: Summarizer
    init {
        val summarizerMethod = when (summarizer) {
            SummarizeVariant.TfIdf -> TfIdfSummarizer()
        }
    }

    fun parseFile(filename: String): String =
        Files
            .readAllLines(Paths.get(javaClass.getResource(filename).path))
            .joinToString("\n")

    fun summarize(text: String, lines: Int): String = summarizerMethod.summarize(text, lines)

    fun summarize(text: String, ratio: Double): String = summarizerMethod.summarize(text, ratio)
}