package com.londogard.summarize.extensions


internal operator fun Array<Float>.minus(other: Array<Float>): Array<Float> =
    Array(kotlin.math.min(this.size, other.size)) { i -> this[i] - other[i] }

internal operator fun Array<Float>.plus(other: Array<Float>): Array<Float> =
    Array(kotlin.math.min(this.size, other.size)) { i -> this[i] + other[i] }

internal fun Array<Float>.dot(other: Array<Float>): Double =
    this.zip(other).map { it.first * it.second }.reduce { a, b -> a + b }.toDouble()

internal fun Array<Float>.normalize(): Array<Float> {
    val l = if (size > 0) (1f / size) else 1f

    return Array(size) { i -> this[i] * l }
}