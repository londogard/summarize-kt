package com.londogard.summarize.summarizers

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