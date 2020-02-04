package com.londogard.summarize

internal fun List<List<Double>>.mutableSumByCols(): List<Double> {
    val columnSum = MutableList(this[0].size) { 0.0 }
    for (columns in this)
        for (i in columns.indices)
            columnSum[i] += columns[i]
    return columnSum.toList()
}