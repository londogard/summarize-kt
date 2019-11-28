package com.londogard.summarize.extensions

operator fun Array<Float>.minus(other: Array<Float>): Array<Float> =
    Array(kotlin.math.min(this.size, other.size)) { i -> this[i] - other[i] }

operator fun Array<Float>.plus(other: Array<Float>): Array<Float> =
    Array(kotlin.math.min(this.size, other.size)) { i -> this[i] + other[i] }

fun Array<Float>.dot(other: Array<Float>): Double =
    this.zip(other).map { it.first * it.second }.reduce { a, b -> a + b }.toDouble()

fun Array<Float>.normalize(): Array<Float> {
    val l = 1f / this.size

    return Array(this.size) { i -> this[i] * l }
}