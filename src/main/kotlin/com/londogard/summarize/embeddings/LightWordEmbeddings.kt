package com.londogard.summarize.embeddings

class LightWordEmbeddings(
    override val dimensions: Int,
    override val filename: String = DownloadHelper.embeddingPath,
    override val delimiter: Char = ' ',
    override val normalized: Boolean = true,
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
        val loadedEmbeddings = loadEmbeddingsFromFile(inFilter)
        embeddings.putAll(loadedEmbeddings)
    }
}