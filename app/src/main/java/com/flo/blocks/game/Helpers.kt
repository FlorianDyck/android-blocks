package com.flo.blocks.game

fun <T> List<T>.permutations(): List<List<T>> {
    if (isEmpty()) return listOf(emptyList())
    val result = mutableListOf<List<T>>()
    for (i in indices) {
        val element = get(i)
        val remaining = subList(0, i) + subList(i + 1, size)
        for (p in remaining.permutations()) {
            result.add(listOf(element) + p)
        }
    }
    return result
}
