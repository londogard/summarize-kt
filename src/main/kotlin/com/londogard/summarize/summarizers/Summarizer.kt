package com.londogard.summarize.summarizers

interface Summarizer {
    fun summarize(text: String, lines: Int = 10): String
    fun summarize(text: String, ratio: Double = 0.1): String
}