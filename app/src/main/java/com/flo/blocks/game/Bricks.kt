package com.flo.blocks.game

import java.util.ArrayList

fun rect(x0: Int, y0: Int, x1: Int, y1: Int): Brick =
    Brick(x1 + 1, y1 + 1, BooleanArray((x1 + 1) * (y1 + 1)) {
        val width = x1 + 1
        val x = it % width
        val y = it / width
        x in x0..x1 && y in y0..y1
    })

fun field(x: Int, y: Int): Brick = rect(x, y, x, y)

fun buildBricks(): List<Brick> {
    val result = ArrayList<Brick>()

    fun addRotations(brick: Brick) {
        var rotated = brick
        do {
            result.add(rotated)
            rotated = rotated.rotate()
        } while (rotated != brick)
    }

    fun addAllVersions(brick: Brick) {
        addRotations(brick)
        val flipped = brick.flipVertically()
        if (flipped !in result) addRotations(flipped)
    }

    for (i in 0..4) addAllVersions(rect(0, 0, i, 0)) // 1*i
    for (i in 1..2) addAllVersions(rect(0, 0, i, i)) // i*i
    for (i in 1..2) addAllVersions(rect(0, 0, i, 0) + field(0, 1)) // iL2

    addAllVersions(rect(0, 0, 2, 0) + rect(0, 0, 0, 2)) // 3L3
    addAllVersions(rect(0, 0, 2, 0) + field(1, 1)) // T
    addAllVersions(rect(0, 0, 1, 0) + rect(1, 1, 2, 1)) // Z

    return result
}

val BRICKS = buildBricks()