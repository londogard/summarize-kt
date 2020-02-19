package com.londogard.summarize.embeddings

import com.londogard.summarize.extensions.normalize
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.streams.asSequence

class LightWordEmbeddings(
    override val dimensions: Int,
    private val filename: String = DownloadHelper.embeddingPath,
    private val delimiter: Char = ' ',
    private val normalized: Boolean = true,
    private val maxWordCount: Int = 1000
) : Embeddings() {
    /** Vocabulary, word to embedded space */
    override val embeddings: MutableMap<String, Array<Float>> = mutableMapOf()
    private val keys: MutableSet<String> = mutableSetOf()

    init {
        if (filename == DownloadHelper.embeddingPath && !DownloadHelper.embeddingsExist())
            DownloadHelper.downloadGloveEmbeddings()
    }

    fun addWords(words: Set<String>) {
        val leftToAdd = words - keys

        if (leftToAdd.isNotEmpty() && leftToAdd.size + keys.size > maxWordCount) {
            val toRemove = keys - words
            keys -= toRemove
            embeddings -= toRemove
        }

        if (leftToAdd.isNotEmpty()) loadEmbeddings(leftToAdd)
    }

    private fun loadEmbeddings(inFilter: Set<String>) {
        keys += inFilter
        embeddings.putAll(Files
            .newBufferedReader(Paths.get(filename))
            .use { reader ->
                reader
                    .lines()
                    .filter { line -> inFilter.isEmpty() || inFilter.contains(line.takeWhile { it != delimiter }) }
                    .asSequence()
                    .mapNotNull { line ->
                        val x = line.split(delimiter) // TODO optimize
                        if (x.size > dimensions)
                            (x.first() to
                                    Array(x.size - 1) { i -> x[i + 1].toFloat() }.let { if (normalized) it.normalize() else it })
                        else null
                    }
                    .toMap()
            })

    }
}