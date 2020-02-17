package com.londogard.summarize.summarizers

import com.londogard.smile.SmileOperators
import com.londogard.smile.extensions.*
import com.londogard.summarize.extensions.*
import com.londogard.summarize.embeddings.Embeddings
import com.londogard.summarize.embeddings.LightWordEmbeddings
import com.londogard.summarize.embeddings.WordEmbeddings
import com.londogard.summarize.extensions.mutableSumByCols
import com.londogard.summarize.extensions.normalize
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

enum class ScoringConfig {
    Rosselio,
    Ghalandari
}

internal class EmbeddingClusterSummarizer(
    private val threshold: Double,
    private val simThreshold: Double,
    private val config: ScoringConfig
) : SmileOperators, SummarizerModel {
    private var embeddings: LightWordEmbeddings = LightWordEmbeddings(dimensions = 300)
    private val zeroArray = Array(embeddings.dimensions) { 0f }
    private fun String.simplify(): String = normalize().toLowerCase().words().joinToString(" ")

    private fun getWordsAboveTfIdfThreshold(sentences: List<String>): Set<String> {
        val corpus = sentences.map { it.bag(stemmer = null) }
        val words = corpus.flatMap { bag -> bag.keys }.distinct()
        val bags = corpus.map { vectorize(words, it) }
        val vectors = tfidf(bags)
        val vector = vectors.mutableSumByCols()
        val vecMax = vector.max() ?: 1.0

        return vector
            .map { it / vecMax }
            .mapIndexedNotNull { i, d -> if (d > threshold) words[i] else null }
            .toSet()
    }


    private fun getWordVector(words: List<String>, allowedWords: Set<String>): Array<Float> = words
        .filter(allowedWords::contains)
        .fold(zeroArray) { acc, word -> (embeddings.vector(word) ?: zeroArray) + acc }
        .normalize()

    private fun getSentenceBaseScoring(
        cleanSentences: List<String>,
        rawSentences: List<String>,
        centroid: Array<Float>,
        allowedWords: Set<String>
    ): List<ScoreHolder> =
        cleanSentences
            .map { it.words() }
            .map { words -> getWordVector(words, allowedWords) }
            .mapIndexed { i, embedding ->
                val score = embeddings.cosine(embedding, centroid) // Here we can add further scoring params

                ScoreHolder(i, rawSentences[i], score, embedding)
            }
            .sortedByDescending { it.score }


    /**
     * This scoring compares using the following:
     * 1. Don't have a vector to similar to any vector in the current summary
     * 2. Otherwise just take max that we got by base scoring (comparing single sentence to centroid of document)
     */
    private fun scoreRosellio(
        numSentences: Int,
        scoreHolders: List<ScoreHolder>
    ): List<ScoreHolder> {
        val selectedIndices = mutableSetOf(0)
        val selectedVectors = mutableSetOf(scoreHolders.first().vector)

        return listOf(scoreHolders.first()) + (2..min(numSentences, scoreHolders.size))
            .mapNotNull { _ ->
                scoreHolders.asSequence()
                    .filterNot { selectedIndices.contains(it.i) }
                    .filterNot { score ->
                        selectedVectors
                            .any { selectedVector -> embeddings.cosine(score.vector, selectedVector) > simThreshold }
                    }
                    .firstOrNull()
                    ?.let { maxScore ->
                        selectedVectors.add(maxScore.vector)
                        selectedIndices.add(maxScore.i)

                        maxScore
                    }
            }
    }

    /**
     * This scoring compares using the following:
     * 1. Don't have a vector to similar to any vector in the current summary
     * 2. Create centroid of currSummary + currSen and compare to centroid of whole doc
     */
    private fun scoreGhalandari(
        numSentences: Int,
        centroid: Array<Float>,
        scoreHolders: List<ScoreHolder>
    ): List<ScoreHolder> {
        val selectedIndices = mutableSetOf(scoreHolders.first().i)
        val selectedVectors = mutableSetOf(scoreHolders.first().vector)
        var centroidSummary = scoreHolders.first().vector

        return listOf(scoreHolders.first()) + (2..min(numSentences, scoreHolders.size))
            .mapNotNull { _ ->
                scoreHolders
                    .filterNot { selectedIndices.contains(it.i) }
                    .filterNot { score ->
                        selectedVectors.any { selectedVector ->
                            embeddings.cosine(score.vector, selectedVector) > simThreshold
                        }
                    }
                    .maxBy { score -> embeddings.cosine(centroid, (score.vector + centroidSummary).normalize()) }
                    ?.let { maxScore ->
                        centroidSummary = (centroidSummary + maxScore.vector)
                        selectedVectors.add(maxScore.vector)
                        selectedIndices.add(maxScore.i)

                        maxScore
                    }
            }
    }

    override fun summarize(text: String, lines: Int): String {
        val sentences = text.sentences()
        val superCleanSentences = sentences.map { it.simplify() }
        val wordsOfInterest = getWordsAboveTfIdfThreshold(superCleanSentences)
        embeddings.addWords(wordsOfInterest)

        val centroidVector = getWordVector(superCleanSentences.flatMap { it.words() }, wordsOfInterest)
        val scores = getSentenceBaseScoring(superCleanSentences, sentences, centroidVector, wordsOfInterest)
        val finalSentences = when (config) {
            ScoringConfig.Ghalandari -> scoreGhalandari(lines, centroidVector, scores)
            ScoringConfig.Rosselio -> scoreRosellio(lines, scores)
        }

        return finalSentences.sortedBy { it.i }.joinToString("\n") { it.rawSentence }
    }

    override fun summarize(text: String, ratio: Double): String {
        val lines = text.sentences().size

        return summarize(text, (lines * ratio).roundToInt().coerceAtLeast(1))
    }
}
