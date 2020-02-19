package com.londogard.summarize.embeddings

import com.londogard.summarize.extensions.`--`
import com.londogard.summarize.extensions.dot
import kotlin.math.sqrt

abstract class Embeddings {
    abstract val dimensions: Int
    internal abstract val embeddings: Map<String, Array<Float>>

    /** Number of words */
    val numWords by lazy { embeddings.keys }

    /** Check if the word is present in the vocab map.
     * @param word Word to be checked.
     * @return True if the word is in the vocab map.
     */
    fun contains(word: String): Boolean = embeddings.contains(word)

    /** Get the vector representation for the word.
     * @param word Word to retrieve vector for.
     * @return The vector representation of the word.
     */
    fun vector(word: String): Array<Float>? = embeddings[word]

    /** Compute the Euclidean distance between the vector representations of the words.
     * @param w1 The first word.
     * @param w2 The other word.
     * @return The Euclidean distance between the vector representations of the words.
     */
    fun euclidean(w1: String, w2: String): Double? = traverseVectors(listOf(w1, w2))?.let { vectors ->
        if (vectors.size == 2) euclidean(vectors.first(), vectors.last())
        else null
    }

    /** Compute the Euclidean distance between two vectors.
     * @param v1 The first vector.
     * @param v2 The other vector.
     * @return The Euclidean distance between the two vectors.
     */
    fun euclidean(v1: Array<Float>, v2: Array<Float>): Double =
        (v1 `--` v2).let { vector -> sqrt(vector.dot(vector)) }

    /** Compute the cosine similarity score between two vectors.
     * 1.0 means equal, 0 = 90* & -1 is when they're opposite
     * @param v1 The first vector.
     * @param v2 The other vector.
     * @return The cosine similarity score of the two vectors.
     */
    fun cosine(v1: Array<Float>, v2: Array<Float>): Double {
        if (v1.size != v2.size) throw ArithmeticException("Vectors must be same size (v1: ${v1.size} != v2: ${v2.size}")

        return v1.dot(v2) / (sqrt(v1.dot(v1)) * sqrt(v2.dot(v2)))
    }

    /** Compute the cosine similarity score between the vector representations of the words.
     * @param w1 The first word.
     * @param w2 The other word.
     * @return The cosine similarity score between the vector representations of the words.
     */
    fun cosine(w1: String, w2: String): Double? = traverseVectors(listOf(w1, w2))?.let { vectors ->
        if (vectors.size == 2) cosine(vectors.first(), vectors.last())
        else null
    }

    internal fun traverseVectors(words: List<String>): List<Array<Float>>? = words
        .fold(listOf<Array<Float>>() as List<Array<Float>>?) { agg, word ->
            vector(word)?.let { v -> (agg ?: emptyList()) + listOf(v) }
        }
}