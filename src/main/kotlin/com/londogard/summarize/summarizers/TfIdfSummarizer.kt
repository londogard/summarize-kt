package com.londogard.summarize.summarizers

import com.londogard.summarize.extensions.mutableSumByCols
import smile.nlp.*
import kotlin.math.roundToInt

internal class TfIdfSummarizer : Summarizer {
    private fun getSentences(text: String): List<String> = text.normalize().sentences().toList()

    override fun summarize(text: String, ratio: Double): String {
        val sentences = getSentences(text)
        return summarize(sentences, (sentences.size * ratio).roundToInt().coerceAtLeast(1))
    }

    override fun summarize(text: String, lines: Int): String = summarize(getSentences(text), lines)

    private fun summarize(sentences: List<String>, lines: Int): String {
        val corpus = sentences.map { it.bag() } // bag includes stemming
        val words = corpus.flatMap { bag -> bag.keys }.distinct()
        val bags = corpus.map { vectorize(words.toTypedArray(), it) }
        val vectors = tfidf(bags)

        val vector = vectors.mutableSumByCols()
        val normalizedVector = vector.max()
            ?.let { max -> List(vector.size) { i -> vector[i] / max } } ?: vector

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
}