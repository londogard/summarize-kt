package com.londogard.summarize.summarizers

import com.londogard.smile.SmileOperators
import com.londogard.smile.extensions.*
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.math.roundToInt

class TfIdfSummarizer : SmileOperators, Summarizer {
    private fun getSentences(text: String): List<String> = text.normalize().sentences()

    override fun summarize(text: String, ratio: Double): String {
        val sentences = getSentences(text)
        return summarize(sentences, (sentences.size * ratio).roundToInt().coerceAtLeast(1))
    }

    override fun summarize(text: String, lines: Int): String = summarize(getSentences(text), lines)

    private fun summarize(sentences: List<String>, lines: Int): String {
        val corpus = sentences.map { it.bag() } // bag includes stemming
        val words = corpus.flatMap { bag -> bag.keys }.distinct()
        val bags = corpus.map { vectorize(words, it) }
        val vectors = tfidf(bags)

        val vector = vectors.effectiveSumColumns()
        val max = vector.max() ?: 1.0
        val normalizedVector = List(vector.size) { i -> vector[i] / max }

        return sentences
            .asSequence()
            .map { sen ->
                sen.words().fold(0.0) { acc, word ->
                    val idx = word.indexOf(word)

                    if (idx == -1) acc + 0 else acc + normalizedVector[idx]
                }
            }
            .withIndex()
            .sortedByDescending { it.value }
            .take(lines)
            .map { it.index to sentences[it.index] }
            .sortedBy { it.first }
            .map { it.second }
            .joinToString("\n")
    }

    private fun List<List<Double>>.effectiveSumColumns(): List<Double> {
        val columnSum = MutableList(this[0].size) { 0.0 }
        for (columns in this)
            for (i in columns.indices)
                columnSum[i] += columns[i]
        return columnSum.toList()
    }
}