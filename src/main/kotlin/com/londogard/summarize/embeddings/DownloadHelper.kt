package com.londogard.summarize.embeddings

import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Files
import java.util.zip.ZipFile

object DownloadHelper {
    private val embeddingDirPath: String = "${System.getProperty("user.home")}${File.separator}summarize-embeddings"
    val embeddingPath: String = "$embeddingDirPath${File.separator}glove.6B.50d.txt"
    const val dimension: Int = 50

    fun embeddingsExist(): Boolean = File(embeddingDirPath).let {
        it.exists() && it.isDirectory && it.listFiles()?.asList()?.isNotEmpty() == true
    }

    private fun String.saveTo(path: String) {
        URL(this).openStream().use { input ->
            FileOutputStream(File(path)).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * 1. Download to temp directory
     * 2. Extract embeddings into 'summarize-embeddings' which is placed in root of users home folder.
     */
    fun downloadGloveEmbeddings() {
        if (embeddingsExist()) {
            println("Embeddings exist in path $embeddingDirPath, early exiting...")
            return
        }

        val tempFile = Files.createTempFile("glove", ".zip")
        val tempPath = tempFile.toAbsolutePath().toString()
        val customDir = File(embeddingDirPath)

        if (!customDir.exists()) customDir.mkdir()

        println("Downloading X GB of Glove Word Embeddings (this will take a while, ~1 GB)...")
        "http://downloads.cs.stanford.edu/nlp/data/glove.6B.zip".saveTo(tempPath)
        println("Download done!")
        println("Extracting 50d word embeddings (from $tempPath to $customDir). Extract your own if you want larger.")
        ZipFile(tempPath).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.contains("50d") }
                .forEach { entry ->
                    zip.getInputStream(entry).use { input ->
                        File(customDir.absolutePath + File.separator + entry.name).outputStream()
                            .use { output -> input.copyTo(output) }
                    }
                }
        }
    }
}