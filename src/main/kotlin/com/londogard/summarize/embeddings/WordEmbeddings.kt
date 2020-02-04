package com.londogard.summarize.embeddings

import com.londogard.summarize.extensions.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.streams.toList

class WordEmbeddings(
    private val filename: String = "/glove_embeddings/glove.6B.50d.txt",
    val dimensions: Int,
    private val delimiter: Char = ' ',
    private val normalized: Boolean = true
) {
    /** Vocabulary, word to embedded space */
    val embeddings: Map<String, Array<Float>> by lazy { loadEmbeddings() }

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

    /** Compute the Euclidean distance between two vectors.
     * @param v1 The first vector.
     * @param v2 The other vector.
     * @return The Euclidean distance between the two vectors.
     */
    fun euclidean(v1: Array<Float>, v2: Array<Float>): Double =
        sqrt((v1 - v2).map { it.pow(2) }.sum()).toDouble()

    /** Compute the Euclidean distance between the vector representations of the words.
     * @param w1 The first word.
     * @param w2 The other word.
     * @return The Euclidean distance between the vector representations of the words.
     */
    fun euclidean(w1: String, w2: String): Double? {
        return traverseVectors(listOf(w1, w2))?.let { vectors ->
            if (vectors.size == 2) euclidean(vectors.first(), vectors.last())
            else null
        }
    }

    /** Compute the cosine similarity score between two vectors.
     * @param v1 The first vector.
     * @param v2 The other vector.
     * @return The cosine similarity score of the two vectors.
     */
    fun cosine(v1: Array<Float>, v2: Array<Float>): Double {
        assert(v1.size == v2.size) { "Vectors must be same size (v1: ${v1.size} != v2: ${v2.size}" }
        val dot = v1.dot(v2)

        return if (normalized) dot else dot / (sqrt(v1.dot(v1)) * sqrt(v2.dot(v2)))
    }

    /** Compute the cosine similarity score between the vector representations of the words.
     * @param w1 The first word.
     * @param w2 The other word.
     * @return The cosine similarity score between the vector representations of the words.
     */
    fun cosine(w1: String, w2: String): Double? {
        return traverseVectors(listOf(w1, w2))?.let { vectors ->
            if (vectors.size == 2) cosine(vectors.first(), vectors.last())
            else null
        }
    }

    fun similarity(v1: Array<Float>, v2: Array<Float>): Double =
        if (v1.any { it > 0 } && v2.any { it > 0 }) {
            (2 - cosine(v1, v2)) / 2.0
        } else 0.0

    /** Find N closest terms in the vocab to the given vector, using only words from the in-set (if defined)
     * and excluding all words from the out-set (if non-empty).  Although you can, it doesn't make much
     * sense to define both in and out sets.
     * @param vector The vector.
     * @param inSet Set of words to consider. Specify None to use all words in the vocab (default behavior).
     * @param outSet Set of words to exclude (default to empty).
     * @param N The maximum number of terms to return (default to 40).
     * @return The N closest terms in the vocab to the given vector and their associated cosine similarity scores.
     */
    fun nearestNeighbours(
        vector: Array<Float>, inSet: Set<String>? = null,
        outSet: Set<String> = setOf(), N: Int = 40
    ): List<Pair<String, Float>> {
        val items = inSet?.mapNotNull { word -> vector(word)?.let { word to it } }?.toMap()
        val it = (items ?: embeddings)
            .mapNotNull { (k, v) -> if (!outSet.contains(k)) k to cosine(vector, v).toFloat() else null }

        val top = PriorityQueue<Pair<String, Float>>(compareBy { it.second })
        it.foldIndexed(top) { i, acc, (k, dist) ->
            return@foldIndexed when {
                i < N -> acc.addR(k to dist)
                acc.first().second < dist -> {
                    acc.remove()
                    acc.addR(k to dist)
                }
                else -> acc
            }
        }

        assert(top.size <= N)
        return top.toList().sortedBy { it.second }
    }

    /** Find the N closest terms in the vocab to the input word(s).
     * @param input The input word(s).
     * @param N The maximum number of terms to return (default to 40).
     * @return The N closest terms in the vocab to the input word(s) and their associated cosine similarity scores.
     */
    fun distance(input: List<String>, N: Int = 40): List<Pair<String, Float>>? =
        if (input.isEmpty()) listOf()
        else traverseVectors(input)
            ?.let { vecs -> nearestNeighbours(sumVector(vecs).normalize(), outSet=input.toSet(), N = N) }

    /** Find the N closest terms in the vocab to the analogy:
     * - [w1] is to [w2] as [w3] is to ???
     *
     * The algorithm operates as follow:
     * - Find a vector approximation of the missing word = vec([w2]) - vec([w1]) + vec([w3]).
     * - Return words closest to the approximated vector.
     *
     * @param w1 First word in the analogy [w1] is to [w2] as [w3] is to ???.
     * @param w2 Second word in the analogy [w1] is to [w2] as [w3] is to ???
     * @param w3 Third word in the analogy [w1] is to [w2] as [w3] is to ???.
     * @param N The maximum number of terms to return (default to 40).
     *
     * @return The N closest terms in the vocab to the analogy and their associated cosine similarity scores.
     */
    fun analogy(w1: String, w2: String, w3: String, N: Int = 40): List<Pair<String, Float>>? =
        traverseVectors(listOf(w1, w2, w3))
            ?.takeIf { it.size == 3 }
            ?.let { vec ->
                val vector = (vec[1] - vec[0]) + vec[2]
                nearestNeighbours(vector.normalize(), outSet = setOf(w1, w2, w3), N = N)
            }

    /** Rank a set of words by their respective distance to some central term.
     * @param word The central word.
     * @param set Set of words to rank.
     * @return Ordered list of words and their associated scores.
     */
    fun rank(word: String, set: Set<String>): List<Pair<String, Float>> =
        vector(word)
            ?.let { vec -> nearestNeighbours(vec, inSet = set, N = set.size) }
            ?: listOf()

    /** Pretty print the list of words and their associated scores.
     * @param words List of (word, score) pairs to be printed.
     */
    fun pprint(words: List<Pair<String, Float>>) {
        println("\n%50s${" ".repeat(7)}Cosine distance\n${"-".repeat(72)}".format("Word"))
        println(words.joinToString("\n") { (word, dist) -> "%50s${" ".repeat(7)}%15f".format(word, dist) })
    }


    fun traverseVectors(words: List<String>): List<Array<Float>>? {
        return words
            .fold(listOf<Array<Float>>() as List<Array<Float>>?) { agg, itr ->
                agg?.let { lst ->
                    vector(itr)?.let { v ->
                        listOf(
                            v
                        ) + lst
                    }
                }
            }?.reversed()
    }

    fun loadEmbeddings(): Map<String, Array<Float>> {
        println("WordEmbeddings::Loading Embeddings")

        return javaClass
            .getResourceAsStream(filename)
            .bufferedReader()
            .lines()
            .map { line ->
                val x = line.split(delimiter)

                if (x.size > dimensions) x.first() to Array(x.size - 1) { i -> x[i + 1].toFloat() }
                    .let { if(normalized) it.normalize() else it }
                else null
            }
            .toList()
            .filterNotNull()
            .toMap()
            .also { println("WordEmbeddings::Finished Loading") }
    }
}

fun <T> PriorityQueue<T>.addR(element: T): PriorityQueue<T> {
    this.add(element)
    return this
}

/** Aggregate (sum) the given list of vectors
 * @param vecs The input vector(s).
 * @return The sum vector (aggregated from the input vectors).
 */
fun sumVector(vectors: List<Array<Float>>): Array<Float> = vectors.reduce { agg, itr -> agg + itr }


object RunWord2Vec {
    @JvmStatic
    fun main(args: Array<String>) {
        val model = WordEmbeddings(dimensions =  50)

        // distance: Find N closest words
        model.distance(listOf("frace"), N = 10)?.let(model::pprint)
        model.distance(listOf("france", "usa"))?.let(model::pprint)
        model.distance(listOf("france", "usa", "usa"))?.let(model::pprint)
        model.analogy("king", "queen", "man", N = 10)?.let(model::pprint)
        model.rank("apple", setOf("orange", "soda", "lettuce", "pear")).let(model::pprint)
    }
}