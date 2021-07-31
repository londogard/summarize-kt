<a href='https://ko-fi.com/O5O819SEH' target='_blank'><img height='22' style='border:0px;height:22px;' src='https://az743702.vo.msecnd.net/cdn/kofi2.png?v=2' border='0' alt='Buy Me a Coffee at ko-fi.com' /></a>[![](https://jitpack.io/v/com.londogard/summarize-kt.svg)](https://jitpack.io/#com.londogard/summarize-kt)

# summarize-kt
Summarization library with an easy-to-use API (pre-loaded models). Currently only extractive summarisation is supported.

The layout:

1. [Usage](#usage)
    1. [Simple example](#example-where-wed-return-30-of-the-content)
2. [Configurations](#explanation-of-the-different-configs)
3. [Installation](#installation)
    1. [Jitpack (the simple way)](#jitpack-easiest)
    2. [Github Packages](#github-packages)

Possible to test on [londogard.com](https://londogard.com/projects/smry).
![image](https://user-images.githubusercontent.com/7490199/127736892-3278817b-cf0c-496a-9193-e14c6a973c74.png)


## Usage
There's an interface named `Summarizer` that allows us to select the method of summarization
through its `companion object`. Two variants are available:

1) `Summarizer.tfIdfSummarizer` 
2) `Summarizer.embeddingClusterSummarizer(threshold: Double = 0.2, simThreshold: Double = 0.95, scoreConfig: ScoringConfig = ScoringConfig.Ghalandari)`

Where we have two different scoring configurations for `embeddingClusterSummarizer`. Read more in this [section](#explanation-of-the-different-configs).  

`Summarizer` has two important methods:  
```kotlin
fun summarize(text: String, lines: Int): String
fun summarize(text: String, ratio: Double): String
```
Both methods returns the summary of the text, the first one returns X number of sentences and the second returns approximate % reduction of the document (0.3 returns ~30% of the article).

##### Example where we'd return ~30% of the content
```kotlin
val summarizer: Summarizer = Summarizer.tfIdfSummarizer
val fullText = """
...Plenty of text...
"""
val summary = summarizer.summarize(fullText, ratio = 0.3)
```

## Explanation of the different configs
`Summarizer` currently support two different versions, either `TfIdf` or `EmbeddingCluster` where the latter has two different configs.  
#### Term Frequency-Inverse Document Frequency (TFIDF)
`TfIdf` uses [TfIdf](https://en.wikipedia.org/wiki/Tf%E2%80%93idf) to find the most important sentences and then retrieves those back.
#### Embedding Cluster  
`EmbeddingCluster` combines both TfIdf & [Word-Embeddings](https://en.wikipedia.org/wiki/Word_embedding).  
In its essence a centroid of the full document is created where we only allow words above a certain TfIdf score to be
 contained in the centroid. The centroid is created using Word Embeddings, we pick the words above the threshold 
 aggregate all their embedding vectors and then normalize - this is the centroid.  
 When this is done we either
 
1. Find all the sentences that are closest to this centroid (not including sentences
  that are too similar to an already included sentence, using the `similarityThreshold`)
2. The same as above but instead of comparing the sentence to the centroid we compare the centroid of the current 
summary (with the new sentence added) to the centroid. That is, we now compare our new summary in total with the document 
so that the sentences plays well together.  

The approach is chosen by the `ScoringConfig` where the first approach is based on 
[Rossiello's](https://www.aclweb.org/anthology/W17-1003/) work and the second is based on
 [Ghalandari's](https://arxiv.org/abs/1708.07690).

In addition one can also set the TfIdf-threshold mentioned using the `threshold` and similarity-threshold 
using `similarityThreshold`.

**OBS** if you want to use custom embeddings you'll currently have to fork the project.
The emeddings should download if you don't have them (OBS: this takes ~1gb download, then 157mb on HDD).

## Installation
The code is uploaded to two different repositories, both Jitpack.io and GitHub Packages.
### Jitpack (easiest)
Add the following to your `build.gradle`. `$version` should be equal to the version supplied by tag above.
```
   repositories {
        maven { url "https://jitpack.io" }
   }
   dependencies {
         implementation 'com.londogard:summarize-kt:$version'
   }
```
### GitHub Packages
Add the following to your `build.gradle`. `$version` should be equal to the version supplied by tag above.  
The part with logging into github repository is how I understand that you need to login. If you know a better way please ping me in an issue.
```
repositories {
   maven {
     url = uri("https://maven.pkg.github.com/londogard/summarize-kt")
     credentials {
         username = project.findProperty("gpr.user") ?: System.getenv("GH_USERNAME")
         password = project.findProperty("gpr.key") ?: System.getenv("GH_TOKEN")
     }
}
}
dependencies {
   implementation "com.londogard:summarize-kt:$version"
}
```
