package com.londogard.summarize.summarizers

import com.londogard.smile.SmileOperators
import com.londogard.smile.extensions.*
import com.londogard.summarize.mutableSumByCols
import com.londogard.summarize.embeddings.WordEmbeddings
import com.londogard.summarize.extensions.normalize
import kotlin.math.min
import kotlin.math.roundToInt

enum class ScoringConfig {
    Rosselio,
    Ghalandari
}

class EmbeddingClusterSummarizer(
    val threshold: Double = 0.3,
    val simThreshold: Double = 0.95,
    val config: ScoringConfig = ScoringConfig.Ghalandari
) : SmileOperators, Summarizer {
    private val embeddings: WordEmbeddings = WordEmbeddings(dimensions = 50)
    private val zeroArray = Array(embeddings.dimensions) { 0f }
    private fun String.simplify(): String =
        normalize().toLowerCase().words().joinToString(" ")

    private fun getTfIdf(sentences: List<String>): Pair<List<Double>, Map<Int, String>> {
        val corpus = sentences.map { it.bag(stemmer = null) }
        val words = corpus.flatMap { bag -> bag.keys }.distinct()
        val bags = corpus.map {
            vectorize(
                words,
                it
            )
        } // Is this really how to do it? Shouldn't we do one vectorization in total per document rather than sent?
        val vectors = tfidf(bags)
        val vector = vectors.mutableSumByCols()
        return (vector.max()?.let { max -> List(vector.size) { i -> vector[i] / max } }
            ?: vector) to words.mapIndexed { i, s -> i to s }.toMap()
    }


    private fun getWordVector(words: List<String>, allowedWords: Set<String>): Array<Float> = words
        .filter(allowedWords::contains)
        .fold(zeroArray) { acc, word -> embeddings.vector(word)?.plus(acc) ?: acc }
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
    private fun scoreRosellio(numSentences: Int, centroid: Array<Float>, scoreHolders: List<ScoreHolder>): List<ScoreHolder> {
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
    private fun scoreGhalandari(numSentences: Int, centroid: Array<Float>, scoreHolders: List<ScoreHolder>): List<ScoreHolder> {
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
                        centroidSummary = (centroidSummary + maxScore.vector) // No need to normalize as it's done in the maxBy.
                        selectedVectors.add(maxScore.vector)
                        selectedIndices.add(maxScore.i)

                        maxScore
                    }
            }
    }

    override fun summarize(text: String, lines: Int): String {
        val sentences = text.sentences()
        val superCleanSentences = sentences.map { it.simplify() }
        val (tfIdf, wordMap) = getTfIdf(superCleanSentences)
        val wordsOfInterest = tfIdf
            .mapIndexedNotNull { index, d -> if (d > threshold) wordMap[index] else null }
            .toSet()
        val centroidVector = getWordVector(superCleanSentences.flatMap { it.words() }, wordsOfInterest)
        val scores = getSentenceBaseScoring(superCleanSentences, sentences, centroidVector, wordsOfInterest)
        val finalSentences = scoreGhalandari(lines, centroidVector, scores)

        return finalSentences.sortedBy { it.i }.joinToString("\n") { it.rawSentence }
    }

    override fun summarize(text: String, ratio: Double): String {
        val lines = text.sentences().size

        return summarize(text, (lines * ratio).roundToInt().coerceAtLeast(1))
    }

    data class ScoreHolder(val i: Int, val rawSentence: String, val score: Double, val vector: Array<Float>) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ScoreHolder

            return i == other.i && score == other.score
        }

        override fun hashCode(): Int {
            var result = i
            result = 31 * result + score.hashCode()
            return result
        }
    }
}

object a {
    @JvmStatic
    fun main(args: Array<String>) {
        val summarizer = EmbeddingClusterSummarizer()
        val text = """
            When airport foreman Scott Babcock went out onto the runway at Wiley Post-Will Rogers Memorial Airport in Utqiagvik, Alaska, on Monday to clear some snow, he was surprised to find a visitor waiting for him on the asphalt: a 450-pound bearded seal chilling in the milky sunshine.

            “It was very strange to see the seal. I’ve seen a lot of things on runways, but never a seal,” Babcock told ABC News. His footage of the hefty mammal went viral after he posted it on Facebook.

            According to local TV station KTVA, animal control was called in and eventually moved the seal with the help of a “sled.”

            Normal air traffic soon resumed, the station said.

            Poking fun at the seal’s surprise appearance, the Alaska Department of Transportation warned pilots on Tuesday of  “low sealings” in the North Slope region — a pun on “low ceilings,” a term used to describe low clouds and poor visibility.

            Though this was the first seal sighting on the runway at the airport, the department said other animals, including birds, caribou and polar bears, have been spotted there in the past.

            “Wildlife strikes to aircraft pose a significant safety hazard and cost the aviation industry hundreds of millions of dollars each year,” department spokeswoman Meadow Bailey told the Associated Press. “Birds make up over 90 percent of strikes in the U.S., while mammal strikes are rare.”
        """.trimIndent()
        println(summarizer.summarize(text, 2))
    }
}