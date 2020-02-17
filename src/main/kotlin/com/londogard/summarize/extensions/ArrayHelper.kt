package com.londogard.summarize.extensions

import kotlin.math.sqrt

internal operator fun Array<Float>.minus(other: Array<Float>): Array<Float> {
    if (size != other.size)
        throw IllegalArgumentException("Arrays must be of same size, $size != ${other.size}")

    return Array(size) { i -> this[i] - other[i] }
}

internal operator fun Array<Float>.plus(other: Array<Float>): Array<Float> {
    if (size != other.size)
        throw IllegalArgumentException("Arrays must be of same size, $size != ${other.size}")

    return Array(size) { i -> this[i] + other[i] }
}

internal fun Array<Float>.dot(other: Array<Float>): Double {
    if (size != other.size)
        throw IllegalArgumentException("Arrays must be of same size, $size != ${other.size}")
    var dotSum = 0.0
    for (i in 0 until size) dotSum += this[i] * other[i]

    return dotSum
}

internal fun Array<Float>.normalize(): Array<Float> = when {
        isEmpty() -> this
        sum() == 0f -> this
        else -> {
            val norm = if (isNotEmpty()) sqrt(map { it * it }.sum()) else 1f
            for (i in 0 until size) this[i] /= norm

            this
    }
}

internal fun Iterable<Array<Float>>.sumByColumns(): Array<Float> = reduce { agg, vector -> agg + vector }

internal fun List<List<Double>>.mutableSumByCols(): List<Double> {
    val columnSum = MutableList(this[0].size) { 0.0 }
    for (columns in this)
        for (i in columns.indices)
            columnSum[i] += columns[i]
    return columnSum.toList()
}
